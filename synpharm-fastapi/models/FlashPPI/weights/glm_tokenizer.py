from tokenizers import Tokenizer
from tokenizers.models import BPE
from transformers import PreTrainedTokenizerFast


class gLM2Tokenizer(PreTrainedTokenizerFast):

    VOCAB = [
        "<cls>", "<pad>", "<eos>", "<unk>",
        "L", "A", "G", "V", "S", "E", "R", "T", "I", "D", "P", "K",
        "Q", "N", "F", "Y", "M", "H", "W", "C", "X", "B", "U", "Z",
        "O", "a", "t", "c", "g", "<+>", "<->", "<mask>", "<sep>",
    ]

    def __init__(
        self,
        unk_token="<unk>",
        cls_token="<cls>",
        pad_token="<pad>",
        mask_token="<mask>",
        eos_token="<eos>",
        sep_token="<sep>",
        pos_token="<+>",
        neg_token="<->",
        **kwargs,
    ):
        all_tokens = self.VOCAB
        token_to_id = {tok: ind for ind, tok in enumerate(all_tokens)}

        bpe = BPE(token_to_id, merges=[], unk_token=str(unk_token))
        tokenizer = Tokenizer(bpe)
        special_tokens = [cls_token, pad_token,
                          mask_token, eos_token, sep_token, pos_token, neg_token]

        tokenizer.add_special_tokens(
            special_tokens,
        )

        super().__init__(
            tokenizer_object=tokenizer,
            unk_token=unk_token,
            cls_token=cls_token,
            pad_token=pad_token,
            mask_token=mask_token,
            eos_token=eos_token,
            sep_token=sep_token,
            **kwargs,
        )
