#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
KAN-MoDTI 推理脚本
==================

KAN-MoDTI（Kolmogorov-Arnold Networks for Drug-Target Interaction）药物-靶点相互作用预测模型。
给定一个药物 SMILES 与一条靶点蛋白质序列，输出二者是否相互作用（二分类）以及交互概率。

模型输入为五元组 (protein_ngram, qsoctd_feature, molecule_word, graph_data, label)，其中：
  * molecule_word    —— 药物 SMILES 字符索引序列（CHAR_SMI_SET 映射）
  * graph_data       —— 药物分子图（原子序数 / 键），由 RDKit 解析
  * protein_ngram    —— 蛋白质序列 3-gram 索引（wordDict 映射）
  * qsoctd_feature   —— 蛋白质 QSO(准序列序) + CTD(组成/转换/分布) 特征，共 103 维
                      = QSOrder(nlag=30, 100 维) + CTDCClass(3 组, 3 维)

用法：
  # 单条推理
  python inference.py --smiles "Cc1ccc(...)..." --sequence "MSTG..."

  # 批量推理（TSV: smiles<TAB>sequence[<TAB>label]）
  python inference.py --pairs dataset/test_pairs.tsv --output output/predictions.csv

依赖：torch / numpy / rdkit
权重：weights/human_final.pth（human 数据集，AUC≈0.9949）
"""
import argparse
import os
import pickle

import numpy as np
import torch
from rdkit import Chem

from model import KAN_MoDTI

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
WEIGHTS_DIR = os.path.join(BASE_DIR, "weights")
DATA_DIR = os.path.join(BASE_DIR, "data")

# 与原作者 DataProcess.py 完全一致的超参数
ENC_DIM = 40            # 特征维度（main.py --features 默认 40）
MLP_DEPTH = 3           # 占位参数，模型内未实际使用
QSOCTD_INPUT_DIM = 103  # QSO+CTD 蛋白质特征维度
MAX_SEQ_LEN = 60        # 药物序列最大长度
NGRAM = 3               # 蛋白质 n-gram 的 n
MAXLAG = 30             # QSO 最大滞后距离（103 = 100 + 3 由 MAXLAG=30 决定）
CTD_GROUPS = ["RKEDQN", "GASTPHY", "CLVIMFW"]  # CTD 氨基酸分组


def load_pickle(path):
    with open(path, "rb") as f:
        return pickle.load(f)


def load_distance_matrix(path):
    """加载 iFeature 的 20x20 氨基酸距离矩阵（跳过首行表头，跳过每行首列标签）。"""
    with open(path) as f:
        records = f.readlines()[1:]
    rows = []
    for line in records:
        line = line.rstrip()
        parts = line.split()[1:] if line != "" else None
        rows.append(parts)
    return np.array(
        [[float(x) for x in row] for row in rows if row is not None], dtype=np.float32
    )


def qsorder_feature(sequence, nlag=MAXLAG, w=0.1):
    """计算 QSO（准序列序）特征，返回 100 维向量（40 组成 + 2*nlag 距离）。"""
    sw = load_distance_matrix(os.path.join(DATA_DIR, "Schneider-Wrede.txt"))
    gm = load_distance_matrix(os.path.join(DATA_DIR, "Grantham.txt"))
    AA = "ACDEFGHIKLMNPQRSTVWY"   # Schneider-Wrede 距离矩阵的行序
    AA1 = "ARNDCQEGHILKMFPSTWYV"  # Grantham 距离矩阵的行序
    dict_sw = {aa: i for i, aa in enumerate(AA)}
    dict_gm = {aa: i for i, aa in enumerate(AA1)}

    seq = sequence.replace("-", "")
    array_sw, array_gm = [], []
    for n in range(1, nlag + 1):
        array_sw.append(sum(
            sw[dict_sw[seq[j]]][dict_sw[seq[j + n]]] ** 2
            for j in range(len(seq) - n)
        ))
        array_gm.append(sum(
            gm[dict_gm[seq[j]]][dict_gm[seq[j + n]]] ** 2
            for j in range(len(seq) - n)
        ))

    feat = []
    for aa in AA1:
        feat.append(seq.count(aa) / (1 + w * sum(array_sw)))   # Schneider.Xr
    for aa in AA1:
        feat.append(seq.count(aa) / (1 + w * sum(array_gm)))   # Grantham.Xr
    for num in array_sw:
        feat.append((w * num) / (1 + w * sum(array_sw)))       # Schneider.Xd
    for num in array_gm:
        feat.append((w * num) / (1 + w * sum(array_gm)))       # Grantham.Xd
    return np.array(feat, dtype=np.float32)


def ctdc_feature(sequence):
    """计算 CTDC（组成）特征，返回 3 维向量（每组氨基酸的出现频率）。"""
    seq = sequence.replace("-", "")
    feat = []
    for group in CTD_GROUPS:
        feat.append(sum(seq.count(aa) for aa in group) / len(seq))
    return np.array(feat, dtype=np.float32)


def qsoctd_feature(sequence):
    """组合 QSO + CTDC，返回 103 维蛋白质特征。"""
    if len(sequence.replace("-", "")) < MAXLAG + 1:
        raise ValueError(
            f"蛋白质序列长度需 >= {MAXLAG + 1}（当前 {len(sequence.replace('-', ''))}）"
        )
    return np.concatenate([qsorder_feature(sequence), ctdc_feature(sequence)]).astype(np.float32)


def smiles_to_graph(smile):
    """SMILES -> 分子图字典（原子序数节点特征 / 边索引 / 键类型边特征）。"""
    mol = Chem.MolFromSmiles(smile)
    if mol is None:
        raise ValueError(f"无法解析的 SMILES：{smile}")

    node_features = np.array([[atom.GetAtomicNum()] for atom in mol.GetAtoms()])

    edge_index, edge_attr = [], []
    for bond in mol.GetBonds():
        i, j = bond.GetBeginAtomIdx(), bond.GetEndAtomIdx()
        edge_index.append([i, j])
        edge_index.append([j, i])
        bond_type = bond.GetBondTypeAsDouble()
        edge_attr.append([bond_type])
        edge_attr.append([bond_type])

    if edge_index:
        edge_index = np.array(edge_index).T
        edge_attr = np.array(edge_attr, dtype=np.float32)
    else:
        edge_index = np.empty((2, 0), dtype=np.int64)
        edge_attr = np.empty((0, 1), dtype=np.float32)

    return {"x": node_features, "edge_index": edge_index, "edge_attr": edge_attr}


def smiles_to_molecule_word(smile, char_map):
    """SMILES -> 字符索引序列（CHAR_SMI_SET 映射）。"""
    try:
        return np.array([char_map[ch] for ch in smile], dtype=np.int32)
    except KeyError as e:
        raise ValueError(f"SMILES 含未注册字符 {e.args[0]}（需在原 CHAR_SMI_SET 内）")


def sequence_to_ngram(sequence, word_dict, ngram=NGRAM):
    """蛋白质序列 -> 3-gram 索引序列（wordDict 映射，OOV 词跳过，全 OOV 回退到 0）。"""
    s = "-" + sequence.replace("-", "") + "="
    words = [word_dict[s[i:i + ngram]] for i in range(len(s) - ngram + 1)
             if s[i:i + ngram] in word_dict]
    if not words:
        words = [0]
    return np.array(words, dtype=np.int32)


class KANMoDTIPredictor:
    """加载权重并封装单条/批量 DTI 推理。"""

    def __init__(self, weight_path=None, device=None):
        if weight_path is None:
            weight_path = os.path.join(WEIGHTS_DIR, "human_final.pth")
        if device is None:
            device = "cuda" if torch.cuda.is_available() else "cpu"
        self.device = torch.device(device)

        # 加载映射字典（决定模型嵌入表大小）
        char_map = load_pickle(os.path.join(WEIGHTS_DIR, "smiles_mapping.pickle"))
        word_dict = load_pickle(os.path.join(WEIGHTS_DIR, "wordDict.pickle"))
        self.char_map = char_map
        self.word_dict = word_dict

        # 构造模型（超参数与训练时严格一致）
        self.model = KAN_MoDTI(
            num_drug=len(char_map),          # 64
            num_protein=len(word_dict),      # 8393
            enc_dim=ENC_DIM,                 # 40
            MLP_depth=MLP_DEPTH,
            affinity_threshold=None,
            device=self.device,
            QSOCTD_input_dim=QSOCTD_INPUT_DIM,
            max_seq_len=MAX_SEQ_LEN,
        ).to(self.device)

        state = torch.load(weight_path, map_location=self.device)
        self.model.load_state_dict(state)
        self.model.eval()
        print(f"[KAN-MoDTI] 加载权重: {weight_path}")
        print(f"[KAN-MoDTI] 设备: {self.device}  药物字典: {len(char_map)}  蛋白字典: {len(word_dict)}")

    def predict(self, smiles, sequence):
        """单条推理，返回 (预测类别 0/1, 交互概率)。"""
        molecule_word = torch.LongTensor(
            smiles_to_molecule_word(smiles, self.char_map)
        ).to(self.device)
        graph_data = smiles_to_graph(smiles)
        protein_ngram = torch.LongTensor(
            sequence_to_ngram(sequence, self.word_dict)
        ).to(self.device)
        qsoctd = torch.FloatTensor(qsoctd_feature(sequence)).to(self.device)

        sample = (protein_ngram, qsoctd, molecule_word, graph_data)
        with torch.no_grad():
            _, preds, scores = self.model([sample], train=False)
        return int(preds[0]), float(scores[0])


def run_single(predictor, smiles, sequence):
    pred, score = predictor.predict(smiles, sequence)
    label = "相互作用" if pred == 1 else "不相互作用"
    print(f"SMILES: {smiles}")
    print(f"序列:   {sequence[:60]}{'...' if len(sequence) > 60 else ''}")
    print(f"预测:   {label} (类别 {pred})  交互概率: {score:.6f}")
    return pred, score


def run_pairs(predictor, pairs_file, output_file):
    """批量推理 TSV 文件（smiles<TAB>sequence[<TAB>label]），写出 CSV。"""
    rows = []
    with open(pairs_file, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            smiles, sequence = parts[0], parts[1]
            label = parts[2] if len(parts) >= 3 else ""
            rows.append((smiles, sequence, label))

    results = []
    for smiles, sequence, label in rows:
        try:
            pred, score = predictor.predict(smiles, sequence)
            results.append({
                "smiles": smiles,
                "sequence": sequence,
                "true_label": label,
                "pred_label": pred,
                "interaction_prob": f"{score:.6f}",
            })
        except Exception as e:  # noqa: BLE001
            results.append({
                "smiles": smiles,
                "sequence": sequence,
                "true_label": label,
                "pred_label": "ERROR",
                "interaction_prob": str(e),
            })

    header = ["smiles", "sequence", "true_label", "pred_label", "interaction_prob"]
    with open(output_file, "w", encoding="utf-8") as f:
        f.write(",".join(header) + "\n")
        for r in results:
            f.write(",".join(
                '"' + str(r[h]).replace('"', '""') + '"' for h in header
            ) + "\n")

    print(f"[KAN-MoDTI] 已推理 {len(results)} 条，结果写入 {output_file}")
    return results


def main():
    parser = argparse.ArgumentParser(description="KAN-MoDTI 药物-靶点相互作用推理")
    parser.add_argument("--smiles", type=str, help="药物 SMILES（单条推理）")
    parser.add_argument("--sequence", type=str, help="靶点蛋白质序列（单条推理）")
    parser.add_argument("--pairs", type=str, help="批量 TSV 文件：smiles<TAB>sequence[<TAB>label]")
    parser.add_argument("--output", type=str, default="output/predictions.csv",
                        help="批量推理结果输出 CSV")
    parser.add_argument("--weights", type=str, default=None,
                        help="权重 .pth 路径（默认 weights/human_final.pth）")
    args = parser.parse_args()

    predictor = KANMoDTIPredictor(weight_path=args.weights)

    if args.pairs:
        run_pairs(predictor, args.pairs, args.output)
    elif args.smiles and args.sequence:
        run_single(predictor, args.smiles, args.sequence)
    else:
        parser.error("请提供 --pairs，或同时提供 --smiles 与 --sequence")


if __name__ == "__main__":
    main()
