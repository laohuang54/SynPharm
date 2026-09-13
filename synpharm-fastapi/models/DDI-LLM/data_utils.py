"""Data loading, feature extraction and link-split helpers for DDI-LLM.

Reproduces the preprocessing of the upstream notebook:

    1. Load the BioSNAP drug-drug interaction edge list.
    2. Load drug SMILES and descriptions; keep only edges whose endpoints have
       a valid SMILES *and* a description.
    3. Build a 100-dim Morgan fingerprint per node (deepchem's
       ``CircularFingerprint(size=100, radius=1)`` is approximated with RDKit's
       hashed Morgan fingerprint of the same size/radius).
"""

import numpy as np
import pandas as pd
from rdkit import Chem, RDLogger
from rdkit.Chem import AllChem

RDLogger.DisableLog('rdApp.*')  # silence RDKit SMILES parse warnings

N_BITS = 100   # fingerprint length (matches deepchem CircularFingerprint size)
RADIUS = 1     # Morgan radius


def load_ddi_graph(path):
    """Load BioSNAP edge list; rename Drug1/Drug2 -> src/dst."""
    df = pd.read_csv(path, sep='\t')
    df.rename(columns={'Drug1': 'src', 'Drug2': 'dst'}, inplace=True)
    return df


def load_smiles(path):
    """Load (DrugBank ID, SMILES) pairs."""
    df = pd.read_csv(path)
    df = df[['DrugBank ID', 'SMILES']].dropna()
    df = df.drop_duplicates('DrugBank ID').reset_index(drop=True)
    return df


def load_descriptions(path):
    """Load (Drug ID, description) pairs."""
    df = pd.read_csv(path)
    return df[['Drug ID', 'Discription']].dropna().reset_index(drop=True)


def is_valid_molecule(smiles):
    try:
        return Chem.MolFromSmiles(smiles) is not None
    except Exception:
        return False


def filter_graph(ddi_graph, drug_smiles, drug_desc):
    """Keep edges whose endpoints have a valid SMILES AND a description."""
    valid_smiles = drug_smiles[drug_smiles['SMILES'].apply(is_valid_molecule)]
    allowed = set(valid_smiles['DrugBank ID']) & set(drug_desc['Drug ID'])
    ddi_graph = ddi_graph[
        ddi_graph['src'].isin(allowed) & ddi_graph['dst'].isin(allowed)
    ].reset_index(drop=True)
    return ddi_graph


def morgan_fingerprint(smiles, n_bits=N_BITS, radius=RADIUS):
    """Hashed (count) Morgan fingerprint of length ``n_bits``."""
    mol = Chem.MolFromSmiles(smiles)
    if mol is None:
        return np.zeros(n_bits, dtype=np.float32)
    fp = AllChem.GetHashedMorganFingerprint(mol, radius=radius, nBits=n_bits)
    arr = np.zeros(n_bits, dtype=np.float32)
    for idx, cnt in fp.GetNonzeroElements().items():
        arr[idx] = float(cnt)
    return arr


def build_graph_and_features(ddi_graph, drug_smiles):
    """Return (features, directed_edges, node_id_map, nodes).

    ``features[i]`` is the fingerprint of ``nodes[i]``; ``node_id_map`` maps a
    DrugBank ID to its integer index.  ``directed_edges`` lists every edge in
    both directions.
    """
    nodes = sorted(pd.unique(ddi_graph[['src', 'dst']].values.ravel()))
    node_id_map = {n: i for i, n in enumerate(nodes)}
    smiles_map = dict(zip(drug_smiles['DrugBank ID'], drug_smiles['SMILES']))

    x = np.zeros((len(nodes), N_BITS), dtype=np.float32)
    for node in nodes:
        smi = smiles_map.get(node)
        if smi is not None:
            x[node_id_map[node]] = morgan_fingerprint(smi)

    directed = []
    for _, row in ddi_graph.iterrows():
        u = node_id_map[row['src']]
        v = node_id_map[row['dst']]
        directed.append((u, v))
        directed.append((v, u))
    return x, directed, node_id_map, nodes


def canonical_edges(directed_edges):
    """Deduplicate directed edges into unique undirected ``(u < v)`` edges."""
    seen = set()
    undirected = []
    for u, v in directed_edges:
        a, b = (u, v) if u < v else (v, u)
        if a == b or (a, b) in seen:
            continue
        seen.add((a, b))
        undirected.append((a, b))
    return undirected


def split_edges(edges, num_val=0.2, num_test=0.2, seed=42):
    """Random 60/20/20 split of undirected edges (train/val/test)."""
    rng = np.random.RandomState(seed)
    order = rng.permutation(len(edges))
    n = len(edges)
    n_test = int(n * num_test)
    n_val = int(n * num_val)
    test = [edges[i] for i in order[:n_test]]
    val = [edges[i] for i in order[n_test:n_test + n_val]]
    train = [edges[i] for i in order[n_test + n_val:]]
    return train, val, test


def negative_sampling(num_nodes, positive_edges, num_neg, seed=42):
    """Sample ``num_neg`` non-edges (u != v, not in ``positive_edges``)."""
    rng = np.random.RandomState(seed)
    pos = set()
    for u, v in positive_edges:
        pos.add((u, v))
        pos.add((v, u))
    negs = []
    while len(negs) < num_neg:
        u = int(rng.randint(0, num_nodes))
        v = int(rng.randint(0, num_nodes))
        if u == v or (u, v) in pos:
            continue
        negs.append((u, v))
        pos.add((u, v))
        pos.add((v, u))
    return negs
