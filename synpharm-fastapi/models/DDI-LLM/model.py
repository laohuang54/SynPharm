"""GCN link-prediction model for DDI (pure-PyTorch reimplementation of DDI-LLM).

DDI-LLM (https://github.com/sshaghayeghs/DDI-LLM) predicts drug-drug
interactions with a 3-layer GCN encoder + dot-product decoder.  The upstream
notebook uses torch_geometric's ``GCNConv``; this module reimplements the same
model with plain PyTorch (a symmetrically normalized adjacency matrix) so it
runs without a torch_geometric installation.

The layer stack is kept identical to the upstream ``Net``:

    conv1 -> dropout(0.3) -> relu -> conv2 -> dropout(0.3) -> relu -> conv3
"""

import torch
import torch.nn as nn
import torch.nn.functional as F


def gcn_norm_adjacency(edge_index, num_nodes):
    """Symmetric GCN normalization ``D^-1/2 (A + I) D^-1/2``.

    Args:
        edge_index: LongTensor of shape ``(2, E)`` holding directed edges.
        num_nodes: Number of nodes in the graph.

    Returns:
        FloatTensor ``(num_nodes, num_nodes)`` normalized adjacency (dense).
    """
    A = torch.zeros(num_nodes, num_nodes)
    A[edge_index[0], edge_index[1]] = 1.0
    A = A + torch.eye(num_nodes)  # self-loops (GCNConv add_self_loops=True)
    deg = A.sum(dim=1)
    deg_inv_sqrt = deg.pow(-0.5)
    deg_inv_sqrt[torch.isinf(deg_inv_sqrt)] = 0.0
    d_inv_sqrt = torch.diag(deg_inv_sqrt)
    return d_inv_sqrt @ A @ d_inv_sqrt


class GCN(nn.Module):
    """3-layer GCN encoder + dot-product decoder (matches DDI-LLM ``Net``)."""

    def __init__(self, in_channels, hidden_channels, out_channels):
        super().__init__()
        self.lin1 = nn.Linear(in_channels, hidden_channels)
        self.lin2 = nn.Linear(hidden_channels, hidden_channels)
        self.lin3 = nn.Linear(hidden_channels, out_channels)

    def encode(self, x, norm_adj):
        x = self.lin1(x)
        x = norm_adj @ x
        x = F.dropout(x, p=0.3, training=self.training)
        x = F.relu(x)
        x = self.lin2(x)
        x = norm_adj @ x
        x = F.dropout(x, p=0.3, training=self.training)
        x = F.relu(x)
        x = self.lin3(x)
        x = norm_adj @ x
        return x

    def decode(self, z, edge_index):
        # Dot product of the two node embeddings on each edge.
        return (z[edge_index[0]] * z[edge_index[1]]).sum(dim=-1)

    def decode_all(self, z):
        prob_adj = z @ z.t()
        return (prob_adj > 0).nonzero(as_tuple=False).t()
