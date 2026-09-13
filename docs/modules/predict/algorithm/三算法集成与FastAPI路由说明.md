# 三算法集成与 FastAPI 路由说明

本文档说明 SynPharm 平台三个预测算法（DDI / DTI / PPI）的**权重、推理脚本、测试、测试集、预测结果分别放在哪里**，以及 FastAPI 算法引擎**如何根据 `algo_type` 选择路由到对应算法**。

面向不熟悉该模块的同学，尽量用白话讲清楚。

---

## 1. 一张表看懂三算法

| 算法 | 中文含义 | 目录 | 权重 | 是否入库 |
|------|---------|------|------|---------|
| **DDI-LLM** | 药物–药物相互作用 | [DDI-LLM/](../../../../DDI-LLM/) | `weights/ddi_gcn_morgan.pt`（约 1.75MB） | ✅ 已入库 |
| **KAN-MoDTI** | 药物–靶点相互作用 | [KAN-MoDTI/](../../../../KAN-MoDTI/) | `weights/human_final.pth`（约 2.5MB）+ 2 个映射文件 | ✅ 已入库 |
| **FlashPPI** | 蛋白质–蛋白质相互作用 | [FlashPPI/](../../../../FlashPPI/) | `weights/model.safetensors`（约 2.93GB） | ⚠️ 未入库（超 100MB 限制） |

> 三个算法的详细文档分别在 [DDI-LLM.md](DDI-LLM.md)、[KAN-MoDTI.md](KAN-MoDTI.md)、[FlashPPI.md](FlashPPI.md)。

### 各算法文件一览

| 内容 | DDI-LLM | KAN-MoDTI | FlashPPI |
|------|---------|-----------|----------|
| **权重** | `weights/ddi_gcn_morgan.pt` | `weights/human_final.pth`、`smiles_mapping.pickle`、`wordDict.pickle` | `weights/model.safetensors`、`config.json`、`modeling_flashppi.py`、`tokenizer.json` 等 |
| **推理脚本** | `inference.py` | `inference.py` | `inference.py` |
| **训练脚本** | `train.py`（本仓库训练） | 无（用上游预训练权重） | 无（用上游预训练权重） |
| **测试集** | `dataset/test_pairs.tsv` | `dataset/test_pairs.tsv` | `dataset/pairs.csv`、`dataset/sequences.fasta` |
| **预测结果** | `output/test_results.csv`、`output/metrics.txt` | `output/predictions.csv` | `output/predictions.csv`、`output/contact_maps/*.npy` |
| **引擎测试** | — | — | — |

引擎（FastAPI 侧）的测试脚本在 `synpharm-fastapi/test.py`（路由）与 `synpharm-fastapi/test_real_inference.py`（真实推理）。

---

## 2. 各算法文件清单

### 2.1 DDI-LLM（药物–药物相互作用）

```
DDI-LLM/
├── inference.py                     # 推理脚本：--drug1/--drug2 或 --input/--output
├── train.py                         # 训练脚本（本仓库从零训练 GCN）
├── model.py                         # GCN 模型定义（3 层 + 点积解码）
├── data_utils.py                    # 数据加载 / 摩根指纹 / 图构建 / 划分
├── weights/
│   └── ddi_gcn_morgan.pt            # 训练好的权重（约 1.75MB，含节点特征与 id 映射）
├── data/                            # 原始数据（BioSNAP DDI 图、DrugBank、描述、SMILES）
│   ├── BioSNAP_ChCh-Miner_ChCh-Miner_durgbank-chem-chem.tsv
│   ├── DrugbankDDI.csv
│   ├── Drug_description.csv
│   └── structure_links.csv
├── dataset/
│   └── test_pairs.tsv               # 测试集（Drug1 / Drug2 对）
├── output/
│   ├── test_results.csv             # 测试集预测结果
│   └── metrics.txt                  # 评估指标（AUROC / AUPR）
└── upstream/                        # 上游仓库参考（Colab 训练 notebook、嵌入脚本）
```

- **权重怎么来的**：上游没有现成权重，本仓库用 [train.py](../../../../DDI-LLM/train.py) 从零训练，产出 `weights/ddi_gcn_morgan.pt`。
- **模型是"转导式"**：只能预测训练图（BioSNAP）里出现过的药物，图外药物返回"不可预测"。

### 2.2 KAN-MoDTI（药物–靶点相互作用）

```
KAN-MoDTI/
├── inference.py                     # 推理脚本：--smiles/--sequence 或 --pairs/--output
├── download_weights.py              # 重新拉取权重（缺失/损坏时用）
├── model.py                         # 模型定义 KAN_MoDTI
├── kan.py                           # KAN 网络实现
├── weights/
│   ├── human_final.pth              # 训练好的权重（约 2.5MB，human 数据集，AUC≈0.9949）
│   ├── smiles_mapping.pickle        # SMILES 字符映射（64 项）
│   └── wordDict.pickle              # 蛋白质 3-gram 字典（8393 项）
├── data/
│   ├── Schneider-Wrede.txt          # QSO 特征氨基酸距离矩阵
│   └── Grantham.txt                 # Grantham 距离矩阵
├── dataset/
│   └── test_pairs.tsv               # 测试集（smiles<TAB>sequence[<TAB>label]）
└── output/
    └── predictions.csv              # 批量推理结果
```

- 权重与映射文件均**已入库**，无需额外下载。

### 2.3 FlashPPI（蛋白质–蛋白质相互作用）

```
FlashPPI/
├── inference.py                     # 推理脚本：--seq1/--seq2 或 --fasta
├── run_dataset.py                   # 批量跑数据集脚本
├── download_weights.py              # 下载权重（首次部署必须运行）
├── weights/
│   ├── model.safetensors            # 权重（约 2.93GB，未入库，需本地下载）
│   ├── config.json                  # 模型配置
│   ├── modeling_flashppi.py         # 模型实现（trust_remote_code 加载）
│   ├── configuration_flashppi.py    # 配置类
│   ├── tokenizer.json 等            # 分词器文件
│   └── README.md                    # 权重来源与许可说明
├── dataset/
│   ├── pairs.csv                    # 测试对
│   └── sequences.fasta              # 测试序列
└── output/
    ├── predictions.csv              # 批量推理结果
    └── contact_maps/*.npy           # 残基级接触图（本地生成，未入库）
```

- **只有权重文件 `model.safetensors`（2.93GB）没进 Git**（超 GitHub 100MB 上限）。代码和配置都入库了。
- 换新机器部署时，需先运行 `python download_weights.py` 下载权重，PPI 才走真实推理。

---

## 3. FastAPI 引擎的路由选择逻辑

FastAPI 算法引擎代码在 [synpharm-fastapi/](../../../../synpharm-fastapi/)。核心路由文件：

- [main.py](../../../../synpharm-fastapi/main.py) —— 应用入口，挂载路由
- [api/v1/predict.py](../../../../synpharm-fastapi/api/v1/predict.py) —— **路由分发（按 `algo_type` 选算法）**
- [services/batch_service.py](../../../../synpharm-fastapi/services/batch_service.py) —— 批量分发
- [services/algorithm_adapters.py](../../../../synpharm-fastapi/services/algorithm_adapters.py) —— 权重加载适配层
- [services/ddi_service.py](../../../../synpharm-fastapi/services/ddi_service.py) 等 —— 三个算法的 service

### 3.1 请求入口

```
POST /v1/predict/single    单条预测
POST /v1/predict/batch     批量预测
```

请求体（`core/schemas.py` 里的 `SingleRequest`）只带一个 `algo_type` 字段决定走哪个算法，其余字段按需填：

| `algo_type` | 需要填的字段 | 含义 |
|-------------|-------------|------|
| `"DTI"` | `drug_smiles` + `target_seq` | 药物 SMILES + 靶点蛋白序列 |
| `"PPI"` | `protein_a` + `protein_b` | 两条蛋白序列 |
| `"DDI"` | `drug_a` + `drug_b` | 两个药物（DrugBank 编号） |

### 3.2 `algo_type` 分发（`predict.py`）

`predict_single` 里是一段 if/elif 分发，这是"路由选择"的核心：

```python
if req.algo_type == "DTI":
    if not req.drug_smiles or not req.target_seq:
        raise InvalidInputError("DTI预测需要drug_smiles和target_seq")
    result = dti_engine.predict({"drug_smiles": ..., "target_seq": ...})
elif req.algo_type == "PPI":
    if not req.protein_a or not req.protein_b:
        raise InvalidInputError("PPI预测需要protein_a和protein_b")
    result = ppi_engine.predict({"protein_a": ..., "protein_b": ...})
elif req.algo_type == "DDI":
    if not req.drug_a or not req.drug_b:
        raise InvalidInputError("DDI预测需要drug_a和drug_b")
    result = ddi_engine.predict({"drug_a": ..., "drug_b": ...})
else:
    raise InvalidInputError(f"未知算法类型: {req.algo_type}")
```

要点：

- **字段校验先行**：缺必填字段直接抛 `InvalidInputError`，返回 HTTP 400。
- **未知 `algo_type`** 返回 400 `未知算法类型: XXX`。
- 批量路由 `predict_batch` 调 [batch_service.py](../../../../synpharm-fastapi/services/batch_service.py) 的 `BatchPredictor.run(data_list, algo_type)`，其 `_get_engine(algo_type)` 用同样的 if/elif 选出 engine，再逐条预测；单条出错只记为 `{"error": ...}`，不中断整批。

### 3.3 Service 层：真实推理优先，权重缺失报错

每个 service（如 `ddi_service.py`）的 `predict()` 逻辑一致：

```
predictor = adapters.get_xxx_predictor()     # 尝试拿真实权重
if predictor is None:
    raise ModelNotFoundError(...)            # 权重缺失 -> 404
... 真实推理，输入非法则抛 InvalidInputError -> 400 ...
return 真实结果
```

三个 service 的差异只在"输入→模型→输出"这一段：

| service | 真实模型 | 输入 | 输出映射到 `confidence_score` |
|---------|---------|------|------------------------------|
| `ddi_service.py` | DDI-LLM GCN | `drug_a`/`drug_b` | 相互作用概率（0~1） |
| `dti_service.py` | KAN-MoDTI | `drug_smiles`/`target_seq` | 交互概率（0~1） |
| `ppi_service.py` | FlashPPI | `protein_a`/`protein_b` | 接触概率 `contact_score`（0~1） |

统一返回 `PredictionMetrics`（`core/schemas.py`）：

```python
PredictionMetrics(
    target_id,          # 目标标识（DTI 为 None，DDI/PPI 用固定占位）
    target_name,        # 目标名（如 "药物相互作用"）
    binding_affinity,   # 结合亲和力（KAN-MoDTI 为二分类，置空）
    confidence_score,   # 置信度/概率（核心字段，0~1）
    confidence_level,   # high / medium / low
    interactions,       # 残基级相互作用（当前留空）
)
```

### 3.4 适配层：权重怎么被"找到"并加载

[algorithm_adapters.py](../../../../synpharm-fastapi/services/algorithm_adapters.py) 做三件事：

1. **隔离导入**：三个算法各自的 `model.py` 同名，且内部用 `from model import X`，直接塞进 `sys.path` 会互相覆盖。适配层用 `importlib` 按**绝对路径 + 唯一模块名**加载，并把目标算法的 `model.py` 临时注入 `sys.modules['model']`，保证解析到正确模块。
2. **懒加载单例**：首次调用才加载权重（避免启动时因缺依赖/缺权重崩溃）；加载结果缓存。
3. **失败兜底**：加载失败（缺依赖 / 缺权重）缓存 `None`，上层 service 据此抛 `ModelNotFoundError`（HTTP 404）。

三个 getter：

| getter | 加载 | 返回 |
|--------|------|------|
| `get_ddi_predictor()` | `DDI-LLM/weights/ddi_gcn_morgan.pt` | 函数 `(drug_a, drug_b) -> 概率` |
| `get_dti_predictor()` | `KAN-MoDTI/weights/human_final.pth` | `KANMoDTIPredictor.predict(smiles, seq)` |
| `get_ppi_predictor()` | `FlashPPI/weights/model.safetensors` | `FlashPPIPredictor.predict(seq1, seq2)` |

> 说明：旧的 [core/loader.py](../../../../synpharm-fastapi/core/loader.py)（加载 `models/dti_model.pt` 等占位权重）已不再被三个 service 引用，保留仅为向后兼容；真实权重统一走 `algorithm_adapters.py`。

### 3.5 完整数据流（以 DDI 单条预测为例）

```
浏览器/前端
   │  POST {"algo_type":"DDI","drug_a":"DB00862","drug_b":"DB00966"}
   ▼
SpringBoot 后端 ──HTTP──> FastAPI 引擎 main.py
   │                        │ 挂载 /v1/predict（先过 verify_api_key 鉴权）
   ▼                        ▼
                     api/v1/predict.py  predict_single
                        │  algo_type == "DDI" → 校验 drug_a/drug_b
                        ▼
                     services/ddi_service.py  predict()
                        │  adapters.get_ddi_predictor()
                        ▼
                     algorithm_adapters.py（懒加载 DDI-LLM 权重）
                        ▼
                     DDI-LLM GCN 图嵌入点积 → sigmoid → 概率 0.6125
                        ▼
                     PredictionMetrics(confidence_score=0.6125, ...)
                        ▼
                     返回 JSON 给前端显示
```

---

## 4. 权重加载与缺失时的错误处理

| 场景 | 表现 |
|------|------|
| 权重 + 依赖齐全 | 走真实推理，返回真实概率 |
| 权重缺失（如 PPI 的 2.93GB 未下载） | 适配层缓存 `None` → service 抛 `ModelNotFoundError`，返回 HTTP 404 |
| 依赖缺失（如缺 `rdkit`/`transformers`/`einops`） | 同上，返回 HTTP 404 |
| 输入非法（DDI 药物不在图内 / DTI 序列 < 31 残基 / SMILES 解析失败） | 抛 `InvalidInputError`，返回 HTTP 400 |

这样设计的好处：**算法权重没就绪时，接口返回明确的 404 错误，而不是静默返回假数据**。

---

## 5. 如何测试

引擎测试脚本在 [synpharm-fastapi/](../../../../synpharm-fastapi/) 下，需在具备 `fastapi + torch + rdkit`（+ 可选 `transformers/einops`）的环境运行。

### 5.1 路由测试 [test.py](../../../../synpharm-fastapi/test.py)

验证"路由能不能收到请求 → 按 `algo_type` 分发 → 返回结构化响应"，覆盖单条/批量/异常用例：

```bash
python test.py
```

通过标准：`status=="success"` 且 `confidence_score` 为 0~1 数值（权重就绪时走真实推理）。

### 5.2 真实推理测试 [test_real_inference.py](../../../../synpharm-fastapi/test_real_inference.py)

验证"真实权重确实被接入、能跑出真实结果"：

```bash
python test_real_inference.py
```

三个算法各自独立 try/catch，缺依赖/缺权重时报告 SKIP，互不影响。

---

## 6. 注意事项

1. **PPI 权重（2.93GB）未入库**：部署到新机器时，先 `python FlashPPI/download_weights.py` 下载，否则 PPI 返回 HTTP 404。
2. **DDI 是转导式**：只能预测 BioSNAP 训练图内的药物（DrugBank 编号），图外药物返回 400。
3. **DTI 序列长度下限**：蛋白序列需 ≥ 31 个氨基酸；SMILES 只支持 `CHAR_SMI_SET` 内的 64 个字符。
4. **引擎依赖**：真实推理需要 `rdkit`（DTI）、`transformers + einops`（PPI），已补充进 [synpharm-fastapi/requirements.txt](../../../../synpharm-fastapi/requirements.txt)。
