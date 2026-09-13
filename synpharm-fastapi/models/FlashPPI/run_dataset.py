"""
批量测试 FlashPPI 推理：读取 dataset/pairs.csv + dataset/sequences.fasta，
对每对蛋白执行推理，结果写入 output/predictions.csv，接触图存入 output/contact_maps/。

用法:
    python run_dataset.py [--device cpu|cuda]
"""

from __future__ import annotations

import argparse
import csv
import time
from pathlib import Path

from inference import FlashPPIPredictor

BASE = Path(__file__).resolve().parent
DATASET_DIR = BASE / "dataset"
OUTPUT_DIR = BASE / "output"
CONTACT_DIR = OUTPUT_DIR / "contact_maps"
WEIGHTS_DIR = BASE / "weights"


def load_sequences(fasta_path: Path) -> dict[str, str]:
    """解析 FASTA，返回 {header: sequence}。"""
    seqs: dict[str, str] = {}
    header: str | None = None
    parts: list[str] = []
    for line in fasta_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith(">"):
            if header is not None:
                seqs[header] = "".join(parts)
            header = line[1:].strip()
            parts = []
        else:
            parts.append(line)
    if header is not None:
        seqs[header] = "".join(parts)
    return seqs


def main() -> int:
    parser = argparse.ArgumentParser(description="FlashPPI 数据集批量测试")
    parser.add_argument("--device", type=str, default=None, help="cuda / cpu（默认自动）")
    args = parser.parse_args()

    seqs = load_sequences(DATASET_DIR / "sequences.fasta")
    pairs_path = DATASET_DIR / "pairs.csv"
    with pairs_path.open(encoding="utf-8") as f:
        pairs = list(csv.DictReader(f))

    OUTPUT_DIR.mkdir(exist_ok=True)
    CONTACT_DIR.mkdir(exist_ok=True)

    predictor = FlashPPIPredictor(model_dir=str(WEIGHTS_DIR), device=args.device)

    fieldnames = [
        "pair_id", "label", "protein_a", "protein_b",
        "contact_score", "clip_score", "len1", "len2", "elapsed_s",
    ]
    rows: list[dict] = []

    for p in pairs:
        pid = p["pair_id"]
        s1 = seqs.get(p["protein_a"], "")
        s2 = seqs.get(p["protein_b"], "")
        if not s1 or not s2:
            print(f"[跳过] {pid}: 缺少序列 {p['protein_a'] or p['protein_b']}")
            continue

        t0 = time.time()
        r = predictor.predict(s1, s2)
        elapsed = time.time() - t0

        # 保存接触图 (.npy)
        import numpy as np
        np.save(CONTACT_DIR / f"{pid}.npy", r["contact_map"])

        row = {
            "pair_id": pid,
            "label": p["label"],
            "protein_a": p["protein_a"],
            "protein_b": p["protein_b"],
            "contact_score": r["contact_score"],
            "clip_score": r["clip_score"],
            "len1": r["len1"],
            "len2": r["len2"],
            "elapsed_s": round(elapsed, 2),
        }
        rows.append(row)
        print(f"[{pid}] clip={r['clip_score']:.4f} contact={r['contact_score']:.4f} "
              f"({r['len1']}x{r['len2']}, {elapsed:.1f}s)")

    out_path = OUTPUT_DIR / "predictions.csv"
    with out_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    print(f"\n完成，结果 -> {out_path}")
    print(f"接触图 -> {CONTACT_DIR}/*.npy")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
