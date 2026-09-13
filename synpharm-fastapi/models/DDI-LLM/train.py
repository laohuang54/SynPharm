"""Train the DDI-LLM GCN on BioSNAP and save weights + test set + results.

Outputs:
    weights/ddi_gcn_morgan.pt  - model weights + graph state (for inference)
    dataset/test_pairs.tsv     - held-out test edges (positive + negative)
    output/test_results.csv    - per-pair predictions for the test set
    output/metrics.txt         - AUROC / AUPR summary
"""

import argparse
import copy
import os

import numpy as np
import torch
from sklearn.metrics import auc, precision_recall_curve, roc_auc_score

from data_utils import (
    build_graph_and_features,
    canonical_edges,
    filter_graph,
    load_ddi_graph,
    load_descriptions,
    load_smiles,
    negative_sampling,
    split_edges,
)
from model import GCN, gcn_norm_adjacency

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, 'data')
WEIGHTS_DIR = os.path.join(BASE_DIR, 'weights')
DATASET_DIR = os.path.join(BASE_DIR, 'dataset')
OUTPUT_DIR = os.path.join(BASE_DIR, 'output')


def evaluate(model, x, norm_adj, label_index, label):
    model.eval()
    with torch.no_grad():
        z = model.encode(x, norm_adj)
        out = model.decode(z, label_index).sigmoid().cpu().numpy()
    label = label.cpu().numpy()
    roc = roc_auc_score(label, out)
    precision, recall, _ = precision_recall_curve(label, out)
    pr = auc(recall, precision)
    return roc, pr, label, out


def make_supervised(pos, neg):
    idx = torch.tensor(pos + neg, dtype=torch.long).t().contiguous()
    lab = torch.tensor([1] * len(pos) + [0] * len(neg), dtype=torch.float32)
    return idx, lab


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--lr', type=float, default=0.001)
    p.add_argument('--epochs', type=int, default=100)
    p.add_argument('--hidden', type=int, default=256)
    p.add_argument('--seed', type=int, default=42)
    p.add_argument('--data-dir', default=DATA_DIR)
    args = p.parse_args()

    torch.manual_seed(args.seed)
    np.random.seed(args.seed)

    ddi_graph = load_ddi_graph(
        os.path.join(args.data_dir,
                     'BioSNAP_ChCh-Miner_ChCh-Miner_durgbank-chem-chem.tsv'))
    drug_smiles = load_smiles(os.path.join(args.data_dir, 'structure_links.csv'))
    drug_desc = load_descriptions(os.path.join(args.data_dir, 'Drug_description.csv'))
    ddi_graph = filter_graph(ddi_graph, drug_smiles, drug_desc)

    x, directed, node_id_map, nodes = build_graph_and_features(ddi_graph, drug_smiles)
    num_nodes = len(nodes)
    undirected = canonical_edges(directed)
    print(f"nodes={num_nodes}  undirected_edges={len(undirected)}")

    train_edges, val_edges, test_edges = split_edges(undirected, seed=args.seed)
    print(f"split -> train={len(train_edges)}  val={len(val_edges)}  test={len(test_edges)}")

    # Message-passing edges = train edges in both directions.
    train_edge_index = []
    for u, v in train_edges:
        train_edge_index.append((u, v))
        train_edge_index.append((v, u))

    # Negative (non-edge) samples: true non-edges w.r.t. the full graph.
    train_neg = negative_sampling(num_nodes, undirected, len(train_edges), seed=args.seed + 1)
    val_neg = negative_sampling(num_nodes, undirected, len(val_edges), seed=args.seed + 2)
    test_neg = negative_sampling(num_nodes, undirected, len(test_edges), seed=args.seed + 3)

    train_idx, train_lab = make_supervised(train_edges, train_neg)
    val_idx, val_lab = make_supervised(val_edges, val_neg)
    test_idx, test_lab = make_supervised(test_edges, test_neg)

    x_t = torch.tensor(x, dtype=torch.float32)
    edge_t = torch.tensor(train_edge_index, dtype=torch.long).t().contiguous()
    norm_adj = gcn_norm_adjacency(edge_t, num_nodes)

    model = GCN(in_channels=x.shape[1], hidden_channels=args.hidden,
                out_channels=args.hidden)
    optimizer = torch.optim.Adam(model.parameters(), lr=args.lr)
    scheduler = torch.optim.lr_scheduler.MultiplicativeLR(
        optimizer, lr_lambda=lambda e: 0.96)
    criterion = torch.nn.BCEWithLogitsLoss()

    best_val_auc = 0.0
    best_state = None
    final = {'test_auc': 0.0, 'test_pr': 0.0, 'labels': None, 'scores': None}

    for epoch in range(1, args.epochs + 1):
        model.train()
        optimizer.zero_grad()
        z = model.encode(x_t, norm_adj)
        out = model.decode(z, train_idx).view(-1)
        loss = criterion(out, train_lab)
        loss.backward()
        optimizer.step()
        scheduler.step()

        val_auc, _, _, _ = evaluate(model, x_t, norm_adj, val_idx, val_lab)
        test_auc, test_pr, test_labels, test_scores = evaluate(
            model, x_t, norm_adj, test_idx, test_lab)
        if val_auc > best_val_auc:
            best_val_auc = val_auc
            final = {'test_auc': test_auc, 'test_pr': test_pr,
                     'labels': test_labels, 'scores': test_scores}
            best_state = copy.deepcopy(model.state_dict())
        if epoch == 1 or epoch % 10 == 0:
            print(f"epoch {epoch:03d}  loss {loss.item():.4f}  "
                  f"val_auc {val_auc:.4f}  test_auc {test_auc:.4f}")

    print(f"\nbest val AUC {best_val_auc:.4f} -> "
          f"test AUROC {final['test_auc']:.4f}  test AUPR {final['test_pr']:.4f}")

    # Save checkpoint (weights + graph state needed for transductive inference).
    os.makedirs(WEIGHTS_DIR, exist_ok=True)
    torch.save({
        'state_dict': best_state,
        'model_config': {'in_channels': x.shape[1],
                         'hidden_channels': args.hidden,
                         'out_channels': args.hidden},
        'nodes': nodes,
        'node_id_map': node_id_map,
        'node_features': x,
        'train_edge_index': train_edge_index,
        'feature_name': 'Morgan',
    }, os.path.join(WEIGHTS_DIR, 'ddi_gcn_morgan.pt'))
    print(f"saved {os.path.join(WEIGHTS_DIR, 'ddi_gcn_morgan.pt')}")

    # Write test set + results (positives first, then negatives).
    id2node = {i: n for i, n in enumerate(nodes)}
    test_pairs = test_edges + test_neg
    test_labels = [1] * len(test_edges) + [0] * len(test_neg)

    os.makedirs(DATASET_DIR, exist_ok=True)
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    with open(os.path.join(DATASET_DIR, 'test_pairs.tsv'), 'w') as f:
        f.write('Drug1\tDrug2\tlabel\n')
        for (u, v), lab in zip(test_pairs, test_labels):
            f.write(f"{id2node[u]}\t{id2node[v]}\t{lab}\n")

    with open(os.path.join(OUTPUT_DIR, 'test_results.csv'), 'w') as f:
        f.write('Drug1,Drug2,label,score,prediction\n')
        for (u, v), lab, sc in zip(test_pairs, test_labels, final['scores']):
            f.write(f"{id2node[u]},{id2node[v]},{lab},{sc:.6f},{1 if sc >= 0.5 else 0}\n")

    with open(os.path.join(OUTPUT_DIR, 'metrics.txt'), 'w') as f:
        f.write(f"test_auroc\t{final['test_auc']:.6f}\n")
        f.write(f"test_aupr\t{final['test_pr']:.6f}\n")
        f.write(f"num_nodes\t{num_nodes}\n")
        f.write(f"num_test_edges\t{len(test_edges)}\n")
        f.write(f"num_test_pairs\t{len(test_pairs)}\n")

    print(f"saved {os.path.join(DATASET_DIR, 'test_pairs.tsv')} "
          f"({len(test_pairs)} pairs)")
    print(f"saved {os.path.join(OUTPUT_DIR, 'test_results.csv')}")


if __name__ == '__main__':
    main()
