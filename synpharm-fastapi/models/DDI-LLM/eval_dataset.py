"""Batch-evaluate the trained DDI-LLM GCN on the held-out test set.

Mirrors ``FlashPPI/run_dataset.py``: loads the checkpoint (weights + graph
state) and scores every pair in ``dataset/test_pairs.tsv``, then writes:

    output/test_results.csv   - per-pair score + prediction
    output/metrics.txt        - AUROC / AUPR summary

Usage:
    python eval_dataset.py [--checkpoint weights/ddi_gcn_morgan.pt]
"""

import argparse
import os

import numpy as np
import torch
from sklearn.metrics import auc, precision_recall_curve, roc_auc_score

from inference import load_model

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATASET_DIR = os.path.join(BASE_DIR, 'dataset')
OUTPUT_DIR = os.path.join(BASE_DIR, 'output')
DEFAULT_CKPT = os.path.join(BASE_DIR, 'weights', 'ddi_gcn_morgan.pt')


def main() -> int:
    p = argparse.ArgumentParser(description='DDI-LLM 测试集批量评估')
    p.add_argument('--checkpoint', default=DEFAULT_CKPT)
    args = p.parse_args()

    ckpt, z = load_model(args.checkpoint)
    node_id_map = ckpt['node_id_map']

    pairs = []
    with open(os.path.join(DATASET_DIR, 'test_pairs.tsv'), encoding='utf-8') as f:
        next(f)  # 跳过表头 Drug1/Drug2/label
        for line in f:
            line = line.rstrip('\n')
            if not line:
                continue
            d1, d2, lab = line.split('\t')
            pairs.append((d1, d2, int(lab)))

    rows, labels, scores = [], [], []
    skipped = 0
    for d1, d2, lab in pairs:
        if d1 not in node_id_map or d2 not in node_id_map:
            skipped += 1
            continue
        u, v = node_id_map[d1], node_id_map[d2]
        logit = float((z[u] * z[v]).sum().item())
        s = float(torch.sigmoid(torch.tensor(logit)).item())
        labels.append(lab)
        scores.append(s)
        rows.append((d1, d2, lab, s))

    labels = np.asarray(labels, dtype=np.float32)
    scores = np.asarray(scores, dtype=np.float32)
    roc = roc_auc_score(labels, scores)
    precision, recall, _ = precision_recall_curve(labels, scores)
    pr = auc(recall, precision)

    num_nodes = len(ckpt['nodes'])
    num_test_edges = int((labels == 1).sum())
    num_test_pairs = len(rows)

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    out_path = os.path.join(OUTPUT_DIR, 'test_results.csv')
    with open(out_path, 'w', newline='', encoding='utf-8') as f:
        f.write('Drug1,Drug2,label,score,prediction\n')
        for d1, d2, lab, s in rows:
            f.write(f"{d1},{d2},{lab},{s:.6f},{1 if s >= 0.5 else 0}\n")

    metrics_path = os.path.join(OUTPUT_DIR, 'metrics.txt')
    with open(metrics_path, 'w', encoding='utf-8') as f:
        f.write(f"test_auroc\t{roc:.6f}\n")
        f.write(f"test_aupr\t{pr:.6f}\n")
        f.write(f"num_nodes\t{num_nodes}\n")
        f.write(f"num_test_edges\t{num_test_edges}\n")
        f.write(f"num_test_pairs\t{num_test_pairs}\n")

    print(f"pairs={num_test_pairs}  skipped={skipped}  nodes={num_nodes}")
    print(f"test AUROC {roc:.6f}  AUPR {pr:.6f}")
    print(f"wrote {out_path}")
    print(f"wrote {metrics_path}")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
