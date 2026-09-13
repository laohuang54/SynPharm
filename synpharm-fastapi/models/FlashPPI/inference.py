"""
FlashPPI 蛋白质-蛋白质相互作用（PPI）推理脚本。

模型来源:
  - GitHub   : https://github.com/TattaBio/FlashPPI
  - 权重     : https://huggingface.co/tattabio/flashppi
  - 权重许可 : CC BY-NC 4.0（仅限非商业 / 学术研究）
  - 代码许可 : Apache 2.0

用法:
    # 两条序列直接预测
    python inference.py --seq1 MKTAYIAKQRQISFVKSHFSRQL --seq2 MSTAGKVIKCKAAVLW

    # 从 FASTA 读取（取前两条序列）
    python inference.py --fasta pair.fasta --device cpu

    # 保存残基级接触图
    python inference.py --seq1 SEQ1 --seq2 SEQ2 --save-contact-map contact.npy

依赖: torch, transformers, einops, numpy
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Optional, Tuple

import numpy as np
import torch
from transformers import AutoModel, AutoTokenizer

# 权重默认放在本脚本同级的 weights/ 目录
WEIGHTS_DIR = Path(__file__).resolve().parent / "weights"


def read_fasta(path: str) -> list[str]:
    """极简 FASTA 解析，返回序列列表（忽略注释行）。"""
    seqs: list[str] = []
    current: list[str] = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith(">"):
            if current:
                seqs.append("".join(current))
                current = []
        else:
            current.append(line)
    if current:
        seqs.append("".join(current))
    return seqs


class FlashPPIPredictor:
    """加载本地 FlashPPI 权重并执行单对 PPI 推理。

    可被 SynPharm FastAPI 算法引擎直接 import 复用。
    """

    def __init__(self, model_dir: Optional[str] = None, device: Optional[str] = None):
        model_dir = Path(model_dir) if model_dir else WEIGHTS_DIR
        if not model_dir.exists():
            raise FileNotFoundError(
                f"模型目录不存在: {model_dir}\n"
                f"请先运行 `python download_weights.py` 下载权重。"
            )
        if not (model_dir / "model.safetensors").exists():
            raise FileNotFoundError(
                f"缺少权重文件 {model_dir / 'model.safetensors'}\n"
                f"请先运行 `python download_weights.py` 下载权重。"
            )

        if device is None:
            device = "cuda" if torch.cuda.is_available() else "cpu"
        self.device = device

        # trust_remote_code=True 会加载目录内的 modeling_flashppi.py / configuration_flashppi.py
        self.tokenizer = AutoTokenizer.from_pretrained(str(model_dir), trust_remote_code=True)
        self.model = AutoModel.from_pretrained(str(model_dir), trust_remote_code=True)
        self.model.to(device).eval()

    def predict(self, seq1: str, seq2: str) -> dict:
        """预测两条蛋白序列的相互作用。

        返回:
            contact_score : 残基级最大接触概率（0~1）
            clip_score    : 对比学习相似度（CLIP 检索得分，主要交互信号）
            contact_map   : (L1, L2) 残基级 2D 接触图
            len1 / len2   : 两条序列的有效长度（去除 padding）
        """
        inputs1 = self.tokenizer(seq1, return_tensors="pt").to(self.device)
        inputs2 = self.tokenizer(seq2, return_tensors="pt").to(self.device)

        with torch.no_grad():
            outputs = self.model(
                input_ids1=inputs1["input_ids"],
                attention_mask1=inputs1["attention_mask"],
                input_ids2=inputs2["input_ids"],
                attention_mask2=inputs2["attention_mask"],
                return_dict=True,
            )

        len1 = int(inputs1["attention_mask"].sum().item())
        len2 = int(inputs2["attention_mask"].sum().item())

        contact_map = outputs.contact_map[0].cpu().numpy()[:len1, :len2]
        contact_score = float(outputs.contact_score[0].cpu().item())
        clip_score = float(outputs.clip_score[0].cpu().item())

        return {
            "contact_score": round(contact_score, 6),
            "clip_score": round(clip_score, 6),
            "contact_map": contact_map,
            "len1": len1,
            "len2": len2,
        }


def main() -> int:
    parser = argparse.ArgumentParser(description="FlashPPI PPI 推理")
    parser.add_argument("--seq1", type=str, help="第一条蛋白序列")
    parser.add_argument("--seq2", type=str, help="第二条蛋白序列")
    parser.add_argument("--fasta", type=str, help="FASTA 文件（取前两条序列作为 pair）")
    parser.add_argument("--model-dir", type=str, default=None, help="权重目录（默认 ./weights）")
    parser.add_argument("--device", type=str, default=None, help="cuda / cpu（默认自动选择）")
    parser.add_argument("--save-contact-map", type=str, default=None,
                        help="将接触图保存为 .npy / .png（按扩展名）")
    parser.add_argument("--output", type=str, default=None, help="将结果 JSON 写入文件")
    args = parser.parse_args()

    # 解析输入序列
    if args.seq1 and args.seq2:
        seq1, seq2 = args.seq1, args.seq2
    elif args.fasta:
        seqs = read_fasta(args.fasta)
        if len(seqs) < 2:
            print(f"[错误] FASTA 文件至少需要 2 条序列，实际 {len(seqs)} 条", file=sys.stderr)
            return 1
        seq1, seq2 = seqs[0], seqs[1]
    else:
        parser.print_help()
        return 1

    predictor = FlashPPIPredictor(model_dir=args.model_dir, device=args.device)
    result = predictor.predict(seq1, seq2)

    # 结果输出（contact_map 体积较大，不入 JSON 正文）
    summary = {k: v for k, v in result.items() if k != "contact_map"}
    print(json.dumps(summary, ensure_ascii=False, indent=2))

    if args.save_contact_map:
        save_contact_map(result["contact_map"], args.save_contact_map)

    if args.output:
        Path(args.output).write_text(
            json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(f"[已保存] 结果 -> {args.output}")

    return 0


def save_contact_map(contact_map: np.ndarray, path: str) -> None:
    """将接触图保存为 .npy 或 .png。"""
    path = str(path)
    if path.endswith(".npy"):
        np.save(path, contact_map)
        print(f"[已保存] 接触图 -> {path} (shape={contact_map.shape})")
    elif path.endswith(".png"):
        try:
            import matplotlib.pyplot as plt
        except ImportError:
            print("[警告] 需要安装 matplotlib 才能保存 PNG", file=sys.stderr)
            return
        plt.imshow(contact_map, cmap="Blues", vmin=0, vmax=1)
        plt.colorbar(label="contact probability")
        plt.xlabel("protein 2 residue")
        plt.ylabel("protein 1 residue")
        plt.savefig(path, dpi=150, bbox_inches="tight")
        print(f"[已保存] 接触图 -> {path}")
    else:
        print(f"[警告] 仅支持 .npy / .png，忽略: {path}", file=sys.stderr)


if __name__ == "__main__":
    raise SystemExit(main())
