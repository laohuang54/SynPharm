"""FlashPPI model configuration."""

from transformers import PretrainedConfig


class FlashPPIConfig(PretrainedConfig):
        
    model_type = "flashppi"
    
    def __init__(
        self,
        # gLM2 backbone config (defaults match gLM2_650M)
        plm_dim: int = 1280,
        plm_depth: int = 33,
        plm_heads: int = 20,
        plm_vocab_size: int = 37,
        plm_norm_eps: float = 1e-5,
        plm_swiglu_multiple_of: int = 256,
        plm_ffn_dim_multiplier: float = None,
        # FlashPPI head config
        clip_embed_dim: int = 1024,
        contact_embed_dim: int = 1280,
        contact_num_heads: int = 8,
        contact_transformer_depth: int = 2,
        max_position_embeddings: int = 512,
        use_flash_attention: bool = True,
        **kwargs
    ):
        super().__init__(**kwargs)
        # gLM2 config
        self.plm_dim = plm_dim
        self.plm_depth = plm_depth
        self.plm_heads = plm_heads
        self.plm_vocab_size = plm_vocab_size
        self.plm_norm_eps = plm_norm_eps
        self.plm_swiglu_multiple_of = plm_swiglu_multiple_of
        self.plm_ffn_dim_multiplier = plm_ffn_dim_multiplier
        # FlashPPI config
        self.clip_embed_dim = clip_embed_dim
        self.contact_embed_dim = contact_embed_dim
        self.contact_num_heads = contact_num_heads
        self.contact_transformer_depth = contact_transformer_depth
        self.max_position_embeddings = max_position_embeddings
        self.use_flash_attention = use_flash_attention
