# KAN-MoDTI

KAN-MoDTI（**K**olmogorov–**A**rnold **N**etworks for **Mo**lecule–**D**rug–**T**arget **I**nteraction），
基于 Kolmogorov–Arnold 网络的药物–靶点相互作用（DTI）预测模型，用于 SynPharm 平台的 DTI 算法模块。

- 上游仓库：https://github.com/jiahaoxin/KAN-MoDTI
- 任务类型：DTI 二分类（药物与靶点蛋白是否相互作用）
- 权重：`weights/human_final.pth`（human 数据集，训练 AUC ≈ 0.9949）
- 权重大小：约 2.5MB，已随仓库提交，无需额外下载

## 目录结构

```
KAN-MoDTI/
├── inference.py            # 推理脚本：SMILES + 蛋白质序列 -> 交互概率
├── download_weights.py     # 重新拉取权重与依赖文件（缺失/损坏时使用）
├── model.py                # 模型定义 KAN_MoDTI（自上游仓库）
├── kan.py                  # KAN（Kolmogorov–Arnold 网络）实现（自上游仓库）
├── requirements.txt        # 依赖：torch / numpy / rdkit
├── weights/
│   ├── human_final.pth          # 模型权重（约 2.5MB，正式训练好的模型）
│   ├── smiles_mapping.pickle   # SMILES 字符映射（64 项，CHAR_SMI_SET）
│   └── wordDict.pickle         # 蛋白质 3-gram 字典（8393 项）
├── data/
│   ├── Schneider-Wrede.txt     # QSO 特征所需的氨基酸距离矩阵
│   └── Grantham.txt            # QSO 特征所需的 Grantham 距离矩阵
├── dataset/
│   └── test_pairs.tsv          # 推理测试样例（真实 human 数据集子集）
└── output/
    └── predictions.csv         # 批量推理结果
```

## 安装

```bash
python -m venv .venv
# Windows
.venv\Scripts\activate
# Linux/macOS
source .venv/bin/activate

pip install torch numpy rdkit
```

> 注：CPU 环境下 `pip install torch` 会默认安装 CPU 版；有 GPU 时请按
> PyTorch 官网安装对应 CUDA 版本。

## 用法

### 单条推理

```bash
python inference.py \
  --smiles "Cc1ccc(cc1Nc2nccc(n2)c3cccnc3)NC(=O)c4ccc(cc4)CN5CCN(CC5)C" \
  --sequence "MGSSHHHHHHSSGLVPRGSHM..."
```

输出预测类别（0=不相互作用，1=相互作用）与交互概率。

### 批量推理

准备一个 TSV 文件，每行 `SMILES<TAB>序列[<TAB>标签]`（标签可选），例如：

```
Cc1ccc(cc1Nc2nccc(n2)c3cccnc3)NC(=O)c4ccc(cc4)CN5CCN(CC5)C	MSTG...	1
```

```bash
python inference.py --pairs dataset/test_pairs.tsv --output output/predictions.csv
```

## 模型说明

模型输入为 `(protein_ngram, qsoctd_feature, molecule_word, graph_data)` 四元组：

| 分量 | 含义 | 来源 |
|------|------|------|
| `molecule_word` | 药物 SMILES 字符索引序列 | `CHAR_SMI_SET`（64 字符） |
| `graph_data` | 药物分子图（原子序数节点 + 键边） | RDKit 解析 |
| `protein_ngram` | 蛋白质序列 3-gram 索引 | `wordDict.pickle` |
| `qsoctd_feature` | 蛋白质 QSO+CTD 特征（103 维） | QSOrder(nlag=30, 100 维) + CTDC(3 组, 3 维) |

模型由三支编码器组成：药物侧 Transformer 序列编码 + 图 KAN 编码（`FeatureFusionKAN` 融合），
蛋白质侧 n-gram 嵌入 + QSOCTD 编码（`FeatureFusionKAN` 融合），最终拼接后经 KAN 分类头输出 2 类 logits。

## 已知限制

1. **蛋白质词典固定**：`wordDict.pickle`（8393 项）由 human 训练集构建，模型 `protein_embed`
   嵌入表尺寸固定为 8393。对训练集之外的蛋白质，其 3-gram 中未命中的词会被跳过；
   若全部未命中则回退到索引 0。跨物种/全新蛋白质的预测精度可能下降。
2. **序列长度下限**：QSO 特征要求蛋白质序列长度 ≥ `MAXLAG + 1 = 31` 个氨基酸，更短的序列无法推理。
3. **SMILES 字符集固定**：`CHAR_SMI_SET` 仅覆盖 64 个字符，含未注册字符的 SMILES 会报错。

> 关于权重命名：上游仓库 `checkpoints/` 目录下的 `human_latest.pth` 是训练早期/未收敛的
> 中间检查点（实测正例召回仅约 0.25），真正的训练结果保存在 `human/human_final.pth`
> （实测正例召回约 0.89、负例特异约 1.0），本目录以 `human_final.pth` 作为正式权重。

## 许可证

上游仓库未声明开源许可证（README 仅为 `# KAN-MoDTI\nDTI`）。本目录仅用于 SynPharm 平台内部
集成与复现，权重与代码版权归原作者所有。
