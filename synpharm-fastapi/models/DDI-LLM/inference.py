"""Predict DDI probability for drug pairs with a trained DDI-LLM GCN.

The GCN link-prediction model is *transductive*: it can only score pairs whose
drugs already appear in the training graph (BioSNAP), because a drug's node
embedding is produced by message passing over that graph.  Drugs outside the
graph have no embedding and are reported as ``N/A``.

Usage:
    python inference.py --drug1 DB00862 --drug2 DB00966
    python inference.py --input dataset/test_pairs.tsv --output output/pred.csv
"""

import argparse
import csv
import os

import torch

from model import GCN, gcn_norm_adjacency

BASE_DIR = os.path.dirname(os.path.abspath(__file__))


def load_model(checkpoint_path):
    ckpt = torch.load(checkpoint_path, map_location='cpu', weights_only=False)
    model = GCN(**ckpt['model_config'])
    model.load_state_dict(ckpt['state_dict'])
    model.eval()

    x = torch.tensor(ckpt['node_features'], dtype=torch.float32)
    edge_index = torch.tensor(ckpt['train_edge_index'], dtype=torch.long).t().contiguous()
    norm_adj = gcn_norm_adjacency(edge_index, len(ckpt['nodes']))
    with torch.no_grad():
        z = model.encode(x, norm_adj)
    return ckpt, z


def main():
    p = argparse.ArgumentParser(description='DDI-LLM inference')
    p.add_argument('--checkpoint', default=os.path.join(BASE_DIR, 'weights', 'ddi_gcn_morgan.pt'))
    p.add_argument('--drug1')
    p.add_argument('--drug2')
    p.add_argument('--input', help='TSV file with Drug1/Drug2 columns')
    p.add_argument('--output', help='Optional output TSV for predictions')
    args = p.parse_args()

    ckpt, z = load_model(args.checkpoint)
    node_id_map = ckpt['node_id_map']

    def score(d1, d2):
        if d1 not in node_id_map or d2 not in node_id_map:
            return None
        u, v = node_id_map[d1], node_id_map[d2]
        logit = (z[u] * z[v]).sum().item()
        return float(torch.sigmoid(torch.tensor(logit)).item())

    results = []
    if args.drug1 and args.drug2:
        results.append((args.drug1, args.drug2, score(args.drug1, args.drug2)))
    elif args.input:
        with open(args.input, newline='') as f:
            reader = csv.reader(f, delimiter='\t')
            for row in reader:
                if not row or row[0] in ('Drug1', 'drug1'):
                    continue
                results.append((row[0], row[1], score(row[0], row[1])))
    else:
        p.error('provide --drug1/--drug2 or --input')

    for d1, d2, s in results:
        if s is None:
            print(f"{d1}\t{d2}\tN/A (drug not in training graph)")
        else:
            print(f"{d1}\t{d2}\t{s:.6f}\t{'DDI' if s >= 0.5 else 'no-DDI'}")

    if args.output:
        with open(args.output, 'w', newline='') as f:
            f.write('Drug1\tDrug2\tscore\tprediction\n')
            for d1, d2, s in results:
                if s is None:
                    continue
                f.write(f"{d1}\t{d2}\t{s:.6f}\t{1 if s >= 0.5 else 0}\n")
        print(f"wrote {args.output}")


if __name__ == '__main__':
    main()
