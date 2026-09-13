import torch
import torch.nn as nn
import torch.nn.functional as F
from dataclasses import dataclass
from typing import Optional, Tuple, Union
from einops import rearrange, repeat
from torch.utils.checkpoint import checkpoint
from transformers import PreTrainedModel
from transformers.modeling_outputs import ModelOutput

from .configuration_flashppi import FlashPPIConfig

# Detect Flash Attention installation
try:
    from flash_attn.layers.rotary import apply_rotary_emb_func
    from flash_attn import flash_attn_varlen_kvpacked_func
    from flash_attn.bert_padding import pad_input, unpad_input
    FLASH_ATTN_AVAILABLE = True
except ImportError:
    FLASH_ATTN_AVAILABLE = False
    unpad_input = pad_input = apply_rotary_emb_func = None
    flash_attn_varlen_kvpacked_func = None

def swiglu(x, y):
    return F.silu(x) * y

class RMSNorm(nn.Module):
    """RMSNorm without variance_epsilon buffer for checkpoint compatibility."""
    def __init__(self, dim, eps=1e-6):
        super().__init__()
        self.weight = nn.Parameter(torch.ones(dim))
        self.eps = eps

    def forward(self, hidden_states):
        input_dtype = hidden_states.dtype
        hidden_states = hidden_states.to(torch.float32)
        variance = hidden_states.pow(2).mean(-1, keepdim=True)
        hidden_states = hidden_states * torch.rsqrt(variance + self.eps)
        return (self.weight * hidden_states).to(input_dtype)
    
@dataclass
class FlashPPIOutput(ModelOutput):
    """Output type for FlashPPI model.
    
    Args:
        contact_map: (B, L1, L2) contact probabilities between residue pairs.
        contact_score: (B,) maximum contact probability per pair.
        clip_embed1: (B, D) CLIP embedding for first protein.
        clip_embed2: (B, D) CLIP embedding for second protein.
        clip_score: (B,) CLIP similarity score (cosine similarity).
    """
    contact_map: Optional[torch.FloatTensor] = None
    contact_score: Optional[torch.FloatTensor] = None
    clip_embed1: Optional[torch.FloatTensor] = None
    clip_embed2: Optional[torch.FloatTensor] = None
    clip_score: Optional[torch.FloatTensor] = None


def rotate_half(x, interleaved=False):
    if not interleaved:
        x1, x2 = x.chunk(2, dim=-1)
        return torch.cat((-x2, x1), dim=-1)
    else:
        x1, x2 = x[..., ::2], x[..., 1::2]
        return rearrange(torch.stack((-x2, x1), dim=-1), "... d two -> ... (d two)", two=2)


def apply_rotary_emb_torch(x, cos, sin, interleaved=False, position_ids=None):
    """Apply rotary embeddings using pure PyTorch."""
    if position_ids is not None:
        cos = cos[position_ids]
        sin = sin[position_ids]
    else:
        cos = cos[:x.shape[1]]
        sin = sin[:x.shape[1]]

    if not interleaved:
        cos = repeat(cos, "... d -> ... 1 (2 d)")
        sin = repeat(sin, "... d -> ... 1 (2 d)")
    else:
        cos = repeat(cos, "... d -> ... 1 (d 2)")
        sin = repeat(sin, "... d -> ... 1 (d 2)")

    ro_dim = cos.shape[-1]
    return torch.cat([
        x[..., :ro_dim] * cos + rotate_half(x[..., :ro_dim], interleaved) * sin,
        x[..., ro_dim:],
    ], dim=-1)


class RotaryEmbedding(nn.Module):
    """Rotary position embeddings with flash attention support."""

    def __init__(self, dim: int, base: float = 10000.0, interleaved: bool = False, device=None):
        super().__init__()
        self.dim = dim
        self.base = float(base)
        self.interleaved = interleaved
        inv_freq = 1.0 / (self.base ** (torch.arange(0, dim, 2, device=device, dtype=torch.float32) / dim))
        self.register_buffer("inv_freq", inv_freq, persistent=False)
        self._seq_len_cached = 0
        self._cos_cached = None
        self._sin_cached = None

    def _update_cos_sin_cache(self, seqlen, device=None, dtype=None):
        if seqlen > self._seq_len_cached or self._cos_cached is None or self._cos_cached.device != device:
            self._seq_len_cached = seqlen
            t = torch.arange(seqlen, device=device, dtype=torch.float32)
            freqs = torch.outer(t, self.inv_freq.to(device=device, dtype=torch.float32))
            self._cos_cached = torch.cos(freqs).to(dtype)
            self._sin_cached = torch.sin(freqs).to(dtype)

    def forward(
        self,
        q: torch.Tensor,
        k: torch.Tensor,
        cu_seqlens: Optional[torch.Tensor] = None,
        max_seqlen: Optional[int] = None,
        position_ids: Optional[torch.Tensor] = None,
    ) -> Tuple[torch.Tensor, torch.Tensor]:
        seqlen = q.shape[1] if max_seqlen is None else max_seqlen
        self._update_cos_sin_cache(seqlen, device=q.device, dtype=q.dtype)

        if FLASH_ATTN_AVAILABLE and cu_seqlens is not None:
            q = apply_rotary_emb_func(
                q, self._cos_cached, self._sin_cached,
                interleaved=self.interleaved, inplace=True,
                cu_seqlens=cu_seqlens, max_seqlen=max_seqlen,
            )
            k = apply_rotary_emb_func(
                k, self._cos_cached, self._sin_cached,
                interleaved=self.interleaved, inplace=True,
                cu_seqlens=cu_seqlens, max_seqlen=max_seqlen,
            )
        else:
            q = apply_rotary_emb_torch(q, self._cos_cached, self._sin_cached, self.interleaved, position_ids)
            k = apply_rotary_emb_torch(k, self._cos_cached, self._sin_cached, self.interleaved, position_ids)
        return q, k


class Attention(nn.Module):
    """Multi-head attention with optional flash attention."""

    def __init__(self, dim: int, num_heads: int, use_rope: bool = True):
        super().__init__()
        self.n_heads = num_heads
        self.head_dim = dim // num_heads
        self.wqkv = nn.Linear(dim, num_heads * self.head_dim * 3, bias=False)
        self.wo = nn.Linear(num_heads * self.head_dim, dim, bias=False)
        self.rotary_emb = RotaryEmbedding(self.head_dim) if use_rope else None

    def forward(
        self,
        x: torch.Tensor,
        cu_seqlens: Optional[torch.Tensor] = None,
        max_seq_len: Optional[int] = None,
        attention_mask: Optional[torch.Tensor] = None,
        position_ids: Optional[torch.Tensor] = None,
    ) -> torch.Tensor:
        qkv = self.wqkv(x)

        if cu_seqlens is not None and FLASH_ATTN_AVAILABLE:
            # Flash attention path (unpadded)
            total_seqlen = x.shape[0]
            q, k, v = torch.split(qkv, self.n_heads * self.head_dim, dim=-1)
            q = q.view(total_seqlen, self.n_heads, self.head_dim)
            k = k.view(total_seqlen, self.n_heads, self.head_dim)
            v = v.view(total_seqlen, self.n_heads, self.head_dim)

            if self.rotary_emb is not None:
                q, k = self.rotary_emb(q, k, cu_seqlens=cu_seqlens, max_seqlen=max_seq_len)

            kv = torch.stack([k, v], 1)
            output = flash_attn_varlen_kvpacked_func(
                q, kv,
                cu_seqlens_q=cu_seqlens, cu_seqlens_k=cu_seqlens,
                max_seqlen_q=max_seq_len, max_seqlen_k=max_seq_len,
                dropout_p=0.0, causal=False,
            )
            output = output.view(total_seqlen, self.n_heads * self.head_dim)
        else:
            # SDPA path (padded)
            bsz, seqlen, _ = x.shape
            q, k, v = torch.split(qkv, self.n_heads * self.head_dim, dim=-1)
            q = q.view(bsz, seqlen, self.n_heads, self.head_dim)
            k = k.view(bsz, seqlen, self.n_heads, self.head_dim)
            v = v.view(bsz, seqlen, self.n_heads, self.head_dim)

            if self.rotary_emb is not None:
                q, k = self.rotary_emb(q, k, position_ids=position_ids)

            q, k, v = q.transpose(1, 2), k.transpose(1, 2), v.transpose(1, 2)

            attn_mask = None
            if attention_mask is not None:
                attn_mask = attention_mask.unsqueeze(1).unsqueeze(2).bool()

            output = F.scaled_dot_product_attention(q, k, v, attn_mask=attn_mask, dropout_p=0.0, is_causal=False)
            output = output.transpose(1, 2).contiguous().view(bsz, seqlen, self.n_heads * self.head_dim)

        return self.wo(output)


class FeedForward(nn.Module):
    """SwiGLU feedforward network."""

    def __init__(self, dim: int, hidden_mult: float = 4.0, multiple_of: int = 256, ffn_dim_multiplier: float = None):
        super().__init__()
        hidden_dim = int(2 * dim * hidden_mult / 3)
        if ffn_dim_multiplier is not None:
            hidden_dim = int(ffn_dim_multiplier * hidden_dim)
        hidden_dim = multiple_of * ((hidden_dim + multiple_of - 1) // multiple_of)
        self.w1 = nn.Linear(dim, hidden_dim, bias=False)
        self.w2 = nn.Linear(hidden_dim, dim, bias=False)
        self.w3 = nn.Linear(dim, hidden_dim, bias=False)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.w2(swiglu(self.w1(x), self.w3(x)))


class TransformerBlock(nn.Module):
    """Pre-norm transformer block."""

    def __init__(self, dim: int, num_heads: int, norm_eps: float = 1e-6, 
                 multiple_of: int = 256, ffn_dim_multiplier: float = None, use_rope: bool = True):
        super().__init__()
        self.attention = Attention(dim, num_heads, use_rope)
        self.feed_forward = FeedForward(dim, multiple_of=multiple_of, ffn_dim_multiplier=ffn_dim_multiplier)
        self.attention_norm = RMSNorm(dim, eps=norm_eps)
        self.ffn_norm = RMSNorm(dim, eps=norm_eps)

    def forward(
        self,
        x: torch.Tensor,
        cu_seqlens: Optional[torch.Tensor] = None,
        max_seq_len: Optional[int] = None,
        attention_mask: Optional[torch.Tensor] = None,
        position_ids: Optional[torch.Tensor] = None,
    ) -> torch.Tensor:
        h = x + self.attention(self.attention_norm(x), cu_seqlens, max_seq_len, attention_mask, position_ids)
        return h + self.feed_forward(self.ffn_norm(h))


class TransformerLayers(nn.Module):
    """Stack of transformer blocks with optional flash attention."""

    def __init__(self, dim: int, num_heads: int, depth: int, norm_eps: float = 1e-6,
                 multiple_of: int = 256, ffn_dim_multiplier: float = None, use_rope: bool = True):
        super().__init__()
        self.dim = dim
        self.layers = nn.ModuleList([
            TransformerBlock(dim, num_heads, norm_eps, multiple_of, ffn_dim_multiplier, use_rope) 
            for _ in range(depth)
        ])
        self.gradient_checkpointing = False

    def forward(self, x: torch.Tensor, attention_mask: Optional[torch.Tensor] = None) -> torch.Tensor:
        batch_size, seq_len = x.shape[:2]
        cu_seqlens, max_seq_len_in_batch, indices, position_ids = None, None, None, None

        if FLASH_ATTN_AVAILABLE and attention_mask is not None and not attention_mask.all():
            x, indices, cu_seqlens, max_seq_len_in_batch, _ = unpad_input(x, attention_mask)
            mask_for_layers = None
        elif attention_mask is not None:
            mask_long = attention_mask.long()
            position_ids = (mask_long.cumsum(dim=1) - 1).clamp(min=0)
            mask_for_layers = attention_mask
        else:
            mask_for_layers = None

        for layer in self.layers:
            if self.training and self.gradient_checkpointing:
                x = checkpoint(layer, x, cu_seqlens, max_seq_len_in_batch, mask_for_layers, position_ids, use_reentrant=False)
            else:
                x = layer(x, cu_seqlens, max_seq_len_in_batch, mask_for_layers, position_ids)

        if FLASH_ATTN_AVAILABLE and indices is not None:
            x = pad_input(x, indices, batch_size, seq_len)

        return x


class GLM2Backbone(nn.Module):
    """gLM2 protein language model backbone."""

    def __init__(self, config: FlashPPIConfig):
        super().__init__()
        self.config = config
        self.tok_embeddings = nn.Embedding(config.plm_vocab_size, config.plm_dim)
        self.encoder = TransformerLayers(
            dim=config.plm_dim,
            num_heads=config.plm_heads,
            depth=config.plm_depth,
            norm_eps=config.plm_norm_eps,
            multiple_of=config.plm_swiglu_multiple_of,
            ffn_dim_multiplier=config.plm_ffn_dim_multiplier,
            use_rope=True,
        )

    def forward(self, input_ids: torch.Tensor, attention_mask: Optional[torch.Tensor] = None) -> torch.Tensor:
        h = self.tok_embeddings(input_ids)
        return self.encoder(h, attention_mask)


class MLPHead(nn.Module):
    """SwiGLU MLP projection head."""

    def __init__(self, in_dim: int, out_dim: int, hidden_mult: float = 2.0):
        super().__init__()
        hidden_dim = int(in_dim * hidden_mult)
        self.w1 = nn.Linear(in_dim, hidden_dim, bias=False)
        self.w2 = nn.Linear(hidden_dim, out_dim, bias=False)
        self.w3 = nn.Linear(in_dim, hidden_dim, bias=False)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.w2(F.silu(self.w1(x)) * self.w3(x))


class ContrastiveHead(nn.Module):
    """CLIP-style contrastive head with mean pooling."""

    def __init__(self, hidden_dim: int, embed_dim: int):
        super().__init__()
        self.head = MLPHead(hidden_dim, embed_dim)

    def forward(self, residue_embeds: torch.Tensor, mask: torch.Tensor) -> torch.Tensor:
        mask = mask.unsqueeze(-1).bool()
        embeds = torch.where(mask, residue_embeds, 0.0)
        embeds = embeds.sum(dim=1) / mask.sum(dim=1).float().clamp(min=1.0)
        return F.normalize(self.head(embeds), dim=-1)


class ContactHead(nn.Module):
    """Contact prediction head using cross-attention between protein pairs."""

    def __init__(self, input_dim: int, contact_dim: int, num_heads: int = 8, depth: int = 2):
        super().__init__()
        self.num_heads = num_heads
        self.head_dim = contact_dim // num_heads
        assert contact_dim % num_heads == 0

        self.segment_embed = nn.Embedding(2, input_dim)
        nn.init.normal_(self.segment_embed.weight, std=0.02)
        
        self.transformer = TransformerLayers(input_dim, num_heads, depth, use_rope=True)
        self.norm = nn.LayerNorm(input_dim)
        self.q_proj = nn.Linear(input_dim, contact_dim, bias=True)
        self.k_proj = nn.Linear(input_dim, contact_dim, bias=True)
        self.output_mix = nn.Linear(num_heads, 1)
        nn.init.constant_(self.output_mix.bias, -3.0)
        self.scale = self.head_dim ** -0.5

    def forward(
        self,
        embed1: torch.Tensor,
        embed2: torch.Tensor,
        mask1: torch.Tensor,
        mask2: torch.Tensor,
    ) -> Tuple[torch.Tensor, torch.Tensor]:
        B, L1, D = embed1.shape
        _, L2, _ = embed2.shape

        seg1 = self.segment_embed(torch.zeros(L1, device=embed1.device, dtype=torch.long))
        seg2 = self.segment_embed(torch.ones(L2, device=embed1.device, dtype=torch.long))

        x = torch.cat([embed1 + seg1.unsqueeze(0), embed2 + seg2.unsqueeze(0)], dim=1)
        combined_mask = torch.cat([mask1, mask2], dim=1).bool() if mask1 is not None and mask2 is not None else None

        x = self.transformer(x, attention_mask=combined_mask)

        embed1 = self.norm(x[:, :L1, :])
        embed2 = self.norm(x[:, L1:, :])

        q = self.q_proj(embed1).view(B, L1, self.num_heads, self.head_dim).transpose(1, 2)
        k = self.k_proj(embed2).view(B, L2, self.num_heads, self.head_dim).transpose(1, 2)

        attn_logits = torch.matmul(q, k.transpose(-2, -1)) * self.scale
        attn_logits = attn_logits.permute(0, 2, 3, 1).contiguous()
        contact_logits = self.output_mix(attn_logits).squeeze(-1)

        if mask1 is not None and mask2 is not None:
            valid_mask = (mask1.unsqueeze(2) * mask2.unsqueeze(1)).bool()
        else:
            valid_mask = torch.ones_like(contact_logits, dtype=torch.bool)

        return contact_logits, valid_mask


class FlashPPIPreTrainedModel(PreTrainedModel):
    """Base class for FlashPPI models."""
    
    config_class = FlashPPIConfig
    base_model_prefix = "flashppi"
    supports_gradient_checkpointing = True

    def _init_weights(self, module):
        if isinstance(module, nn.Linear):
            nn.init.normal_(module.weight, std=0.02)
            if module.bias is not None:
                nn.init.zeros_(module.bias)
        elif isinstance(module, nn.Embedding):
            nn.init.normal_(module.weight, std=0.02)
        elif isinstance(module, RotaryEmbedding):
            # Re-calculate the frequencies using the module's stored attributes
            inv_freq = 1.0 / (
                module.base
                ** (
                    torch.arange(0, module.dim, 2, device=module.inv_freq.device, dtype=torch.float32)
                    / module.dim
                )
            )
            # Force the buffer to update
            with torch.no_grad():
                module.inv_freq.copy_(inv_freq)

class FlashPPIModel(FlashPPIPreTrainedModel):
    """FlashPPI model."""

    def __init__(self, config: FlashPPIConfig):
        super().__init__(config)
        self.config = config

        # gLM2 backbone
        self.plm = GLM2Backbone(config)

        # CLIP heads (asymmetric for query/key)
        self.head_q = ContrastiveHead(config.plm_dim, config.clip_embed_dim)
        self.head_k = ContrastiveHead(config.plm_dim, config.clip_embed_dim)
        self.logit_scale = nn.Parameter(torch.ones([]) * 2.6593)  # ln(1/0.07)

        # Contact prediction head
        self.contact_head = ContactHead(
            config.plm_dim,
            config.contact_embed_dim,
            num_heads=config.contact_num_heads,
            depth=config.contact_transformer_depth,
        )

        self.post_init()

    def encode_protein(
        self,
        input_ids: torch.Tensor,
        attention_mask: Optional[torch.Tensor] = None,
    ) -> torch.Tensor:
        """Encode a protein sequence to residue-level embeddings.
        
        Args:
            input_ids: (B, L) token IDs from gLM2 tokenizer.
            attention_mask: (B, L) attention mask.
            
        Returns:
            (B, L, plm_dim) residue embeddings.
        """
        return self.plm(input_ids, attention_mask)

    def predict_contacts(
        self,
        embed1: torch.Tensor,
        embed2: torch.Tensor,
        mask1: torch.Tensor,
        mask2: torch.Tensor,
    ) -> Tuple[torch.Tensor, torch.Tensor]:
        """Predict contact map from pre-computed residue embeddings.
        
        This method is useful for efficient 2-stage inference where embeddings
        are pre-computed and cached.
        
        Args:
            embed1: (B, L1, D) residue embeddings for protein 1.
            embed2: (B, L2, D) residue embeddings for protein 2.
            mask1: (B, L1) attention mask for protein 1.
            mask2: (B, L2) attention mask for protein 2.
            
        Returns:
            contact_logits: (B, L1, L2) raw logits.
            valid_mask: (B, L1, L2) mask for valid positions.
        """
        return self.contact_head(embed1, embed2, mask1, mask2)

    def forward(
        self,
        input_ids1: torch.Tensor,
        input_ids2: torch.Tensor,
        attention_mask1: Optional[torch.Tensor] = None,
        attention_mask2: Optional[torch.Tensor] = None,
        return_dict: bool = True,
    ) -> Union[Tuple, FlashPPIOutput]:
        """Forward pass for protein pair interaction prediction.
        
        Args:
            input_ids1: (B, L1) token IDs for protein 1.
            input_ids2: (B, L2) token IDs for protein 2.
            attention_mask1: (B, L1) attention mask for protein 1.
            attention_mask2: (B, L2) attention mask for protein 2.
            return_dict: Whether to return a FlashPPIOutput or tuple.
            
        Returns:
            FlashPPIOutput with contact predictions and CLIP embeddings.
        """
        B = input_ids1.shape[0]
        L1, L2 = input_ids1.shape[1], input_ids2.shape[1]
        
        if attention_mask1 is None:
            attention_mask1 = torch.ones_like(input_ids1)
        if attention_mask2 is None:
            attention_mask2 = torch.ones_like(input_ids2)

        # Encode both proteins in a single batched PLM call for efficiency
        # Pad to same length if needed
        if L1 != L2:
            max_len = max(L1, L2)
            if L1 < max_len:
                pad_len = max_len - L1
                input_ids1 = F.pad(input_ids1, (0, pad_len), value=0)
                attention_mask1 = F.pad(attention_mask1, (0, pad_len), value=0)
            if L2 < max_len:
                pad_len = max_len - L2
                input_ids2 = F.pad(input_ids2, (0, pad_len), value=0)
                attention_mask2 = F.pad(attention_mask2, (0, pad_len), value=0)
        
        # Batch both sequences for single PLM forward pass
        batched_input_ids = torch.cat([input_ids1, input_ids2], dim=0)
        batched_attention_mask = torch.cat([attention_mask1, attention_mask2], dim=0)
        batched_embeds = self.encode_protein(batched_input_ids, batched_attention_mask)
        
        # Split and trim back to original lengths
        residue_embeds1 = batched_embeds[:B, :L1, :]
        residue_embeds2 = batched_embeds[B:, :L2, :]
        attention_mask1 = attention_mask1[:, :L1]
        attention_mask2 = attention_mask2[:, :L2]

        # Contrastive embeddings
        clip_embed1 = self.head_q(residue_embeds1, attention_mask1)
        clip_embed2 = self.head_k(residue_embeds2, attention_mask2)
        clip_score = (clip_embed1 * clip_embed2).sum(dim=-1)

        # Contact prediction
        contact_logits, valid_mask = self.contact_head(
            residue_embeds1, residue_embeds2, attention_mask1, attention_mask2
        )
        contact_map = torch.sigmoid(contact_logits)
        
        # Mask invalid positions before taking max
        contact_map_masked = contact_map.masked_fill(~valid_mask, 0.0)
        contact_score = contact_map_masked.flatten(1).max(dim=-1).values

        if not return_dict:
            return (contact_map, contact_score, clip_embed1, clip_embed2, clip_score)

        return FlashPPIOutput(
            contact_map=contact_map,
            contact_score=contact_score,
            clip_embed1=clip_embed1,
            clip_embed2=clip_embed2,
            clip_score=clip_score,
        )
