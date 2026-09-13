# FlashPPI — 蛋白质-蛋白质相互作用（PPI）预测

用于 SynPharm 平台 **PPI** 算法的一个权重与推理脚本。

## 来源与许可

| 项目 | 地址 | 许可 |
| :--- | :--- | :--- |
| 代码仓库 | https://github.com/TattaBio/FlashPPI | Apache 2.0 |
| 模型权重 | https://huggingface.co/tattabio/flashppi | **CC BY-NC 4.0（仅限非商业 / 学术研究）** |
| 论文 | PNAS 2026, doi:10.1073/pnas.2610619123 | — |

> ⚠️ **权重为 CC BY-NC 4.0 非商业许可**：如需商用，请自行联系原作者（TattaBio）取得授权，或改用其他 PPI 模型。

## 模型简介

FlashPPI 是一个对比学习训练的 PPI 预测模型，将 PPI 预测重构为「稠密检索」任务，绕过了传统全对全结构筛选的 O(N²) 瓶颈：

- **可扩展**：全蛋白质组筛选从数天/数月缩短到数分钟
- **可解释**：输出残基级 2D 接触图
- **骨干网络**：基于 [gLM2](https://huggingface.co/tattabio/gLM2_650M)（约 650M 参数的蛋白语言模型）

模型输入两条蛋白序列，输出：

| 输出字段 | 含义 |
| :--- | :--- |
| `contact_map` | (L1, L2) 残基级接触概率图 |
| `contact_score` | 残基级最大接触概率（0~1） |
| `clip_score` | 对比学习相似度（CLIP 检索得分，主要交互信号） |

## 目录结构

```
FlashPPI/
├── inference.py            # 推理脚本（单对 PPI 预测）
├── download_weights.py     # 从 HuggingFace 下载权重
├── requirements.txt        # 推理依赖
├── README.md               # 本文件
└── weights/                # 模型文件
    ├── config.json
    ├── configuration_flashppi.py   # 自定义配置类
    ├── modeling_flashppi.py        # 自定义模型类（推理代码）
    ├── glm_tokenizer.py            # 自定义 tokenizer
    ├── tokenizer.json / tokenizer_config.json / special_tokens_map.json
    └── model.safetensors           # 权重（约 2.7GB，不随 git 提交）
```

## 使用

### 1. 安装依赖

```bash
pip install -r requirements.txt huggingface_hub
```

### 2. 下载权重

```bash
python download_weights.py
```

### 3. 运行推理

```bash
# 两条序列直接预测
python inference.py --seq1 MKTAYIAKQRQISFVKSHFSRQL --seq2 MSTAGKVIKCKAAVLW

# 从 FASTA 读取（取前两条序列作为 pair）
python inference.py --fasta pair.fasta --device cpu

# 保存残基级接触图（.npy 或 .png）
python inference.py --seq1 SEQ1 --seq2 SEQ2 --save-contact-map contact.png
```

输出示例：

```json
{
  "contact_score": 0.8734,
  "clip_score": 0.6421,
  "len1": 24,
  "len2": 16
}
```

### 4. 集成到 SynPharm 算法引擎

`FlashPPIPredictor` 类可直接被 `synpharm-fastapi` 的 PPI 服务 import：

```python
from FlashPPI.inference import FlashPPIPredictor  # 示例，实际按工程路径调整

predictor = FlashPPIPredictor(model_dir="FlashPPI/weights", device="cpu")
result = predictor.predict(protein_a, protein_b)   # -> dict
```

## 补充说明

- 全蛋白质组/跨蛋白质组批量筛选可参考原仓库脚本 `predict_proteome.py` / `predict_cross_proteome.py`（未包含在本目录，需自行从 GitHub 获取）。
- 推理可在 CPU 上运行（约 733M 参数，占用内存约 3GB）；GPU 上可安装 `flash-attn` 加速。
