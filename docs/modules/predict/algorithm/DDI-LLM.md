# DDI-LLM — 药物-药物相互作用（DDI）预测

本目录集成了 **DDI-LLM**（https://github.com/sshaghayeghs/DDI-LLM）药物-药物相互作用（Drug-Drug Interaction, DDI）预测算法到 SynPharm 平台。

## 核心结论：上游无预训练权重

上游 DDI-LLM 仓库是一个 **Colab 训练 notebook**（`PyG__link_pred_DDI.ipynb`），**不提供任何预训练权重文件**。它通过 GCN（图卷积网络）链接预测 + LLM 嵌入（BERT/GPT/LLaMA 等）来做 DDI 预测，但训练需自行完成。

因此本目录做了以下工作：

1. 从上游 notebook 中提取出 GCN 模型、数据预处理、训练/评估流程；
2. 用**纯 PyTorch** 重写了 GCN（上游依赖 `torch_geometric`，在 Python 3.13 + torch 2.13 环境下无对应 wheel，故改为标准化的邻接矩阵实现，模型结构完全一致）；
3. 从零训练并**产出权重文件** `weights/ddi_gcn_morgan.pt`；
4. 产出**测试集**与**测试结果**文件。

## 算法原理

- **模型**：3 层 GCN 编码器（隐藏维度 256）+ 点积解码器（`decode` 计算两个节点嵌入的内积），与上游 `Net` 一致：

  ```
  conv1 -> dropout(0.3) -> ReLU -> conv2 -> dropout(0.3) -> ReLU -> conv3
  ```

- **节点特征**：Morgan 分子指纹（100 维，radius=1），对应上游 deepchem 的 `CircularFingerprint(size=100, radius=1)`（用 RDKit 的哈希 Morgan 指纹近似）。
- **数据集**：BioSNAP 药物相互作用图（`data/BioSNAP_...tsv`），配合 `structure_links.csv`（SMILES）与 `Drug_description.csv`（药物描述）过滤出同时具有合法 SMILES 与描述的节点/边。
- **训练**：`RandomLinkSplit(0.2/0.2)` 划分 60/20/20，BCEWithLogitsLoss + Adam + MultiplicativeLR(0.96)，100 轮，负采样比例 1:1。

## 目录结构

```
DDI-LLM/
├── model.py           # GCN 模型 + 归一化邻接矩阵（纯 PyTorch）
├── data_utils.py      # 数据加载、Morgan 指纹、图构建、边划分、负采样
├── train.py           # 训练 + 评估，产出权重 / 测试集 / 测试结果
├── inference.py       # 加载权重，对药物对做 DDI 推理
├── requirements.txt
├── data/              # 数据集（已提交，自包含）
│   ├── BioSNAP_ChCh-Miner_ChCh-Miner_durgbank-chem-chem.tsv
│   ├── DrugbankDDI.csv
│   ├── structure_links.csv
│   └── Drug_description.csv
├── upstream/          # 上游原始文件（参考）
│   ├── PyG__link_pred_DDI.ipynb
│   ├── get_bert_embeddings.py
│   └── get_llama_embedding.py
├── weights/
│   └── ddi_gcn_morgan.pt   # 训练得到的权重（含图状态，供推理）
├── dataset/
│   └── test_pairs.tsv      # 测试集（16654 个药物对 + 标签）
└── output/
    ├── test_results.csv    # 每个测试对的预测分数与预测标签
    └── metrics.txt         # 测试指标汇总
```

## 测试结果

在 BioSNAP 上，Morgan 特征 + 3 层 GCN（lr=0.001，seed=42，100 轮）：

| 指标 | 值 |
| --- | --- |
| Test AUROC | **0.8732** |
| Test AUPR | **0.8507** |
| 节点数 | 1323 |
| 测试边数（正样本） | 8327 |
| 测试对总数（含负样本） | 16654 |

> 与论文量级一致（论文中 Morgan 特征在 BioSNAP 上的 AUROC 约 0.90；此处节点/边数略不同，见下文说明）。

## 复现步骤

```bash
pip install -r requirements.txt
python train.py              # 训练并产出 weights/dataset/output
python inference.py --drug1 DB00862 --drug2 DB00966   # 单对推理
python inference.py --input dataset/test_pairs.tsv --output output/pred.csv  # 批量推理
```

## 推理

```bash
python inference.py --drug1 DB00862 --drug2 DB00966
# DB00862  DB00966  0.647395  DDI
```

模型是**转导式（transductive）**链接预测：只能对出现在训练图（BioSNAP）内的药物对打分；图外的药物无节点嵌入，会返回 `N/A`。

## 说明与注意事项

1. **LLM 嵌入（BERT/GPT/LLaMA）**：上游还支持 8 种 LLM 嵌入（BERT/GPT/LLaMA/LLaMA2 × SMILES/Description），但这些嵌入文件存放在 Google Drive（`Dataset/Embedding/README.md` 中给出的链接），仓库内不含，需自行下载。本目录以自包含的 Morgan 指纹作为演示，`upstream/get_bert_embeddings.py`、`get_llama_embedding.py` 为对应的嵌入生成脚本（参考用）。
2. **SMILES 数据源**：上游 notebook 用的是配套仓库 `sshaghayeghs/molSMILES` 的 `structure links 2.csv`；本目录改用 DDI-LLM 仓库自带的 `Dataset/Drug Information/structure links.csv`（更自包含），因此过滤后得到 1323 节点 / 41639 边（notebook 中约 1278 / 27800）。
3. **依赖差异**：上游依赖 `torch_geometric`/`deepchem`/`mol2vec`/`gensim`；本实现仅依赖 `torch`/`numpy`/`pandas`/`scikit-learn`/`rdkit`，模型结构等价，已避开 `torch_geometric` 的安装问题。
