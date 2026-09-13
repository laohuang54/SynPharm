"""权重加载适配层：把三个算法（DDI-LLM / KAN-MoDTI / FlashPPI）的真实权重接进引擎。

三个算法的 ``model.py`` 同名，且内部用 ``from model import X`` 相对导入，直接塞进
``sys.path`` 会互相覆盖。这里用 importlib 按**绝对路径 + 唯一模块名**加载，并把目标算法的
``model.py`` 临时注入 ``sys.modules['model']``（KAN-MoDTI 还额外注入 ``kan``），
保证每个算法解析到自己的模块。

三个 getter 都是**懒加载单例**：首次调用才加载权重，加载结果缓存；加载失败
（缺依赖 / 缺权重）缓存 ``None``，上层 service 据此抛 ``ModelNotFoundError``（404），
避免在启动时因某个算法权重没就绪而整体崩溃。

输入非法时抛出 ``ValueError``（不在此处 import core.exceptions，避免把 fastapi 依赖
拖进本模块），由 service 层统一转成 ``InvalidInputError``（HTTP 400）。
"""

from __future__ import annotations

import importlib.util
import logging
import sys
from pathlib import Path

import torch

logger = logging.getLogger(__name__)

# models/ 目录（本文件在 services/ 下，上一级即 synpharm-fastapi 根）
MODELS_DIR = Path(__file__).resolve().parent.parent / "models"

_UNSET = object()
_cache: dict = {}


def _auto_device() -> str:
    """优先 GPU，无 GPU 回退 CPU。"""
    return "cuda" if torch.cuda.is_available() else "cpu"


def _load_module(module_name: str, file_path: Path):
    """按绝对路径把单个 .py 文件加载为独立模块（不依赖 sys.path 顺序）。"""
    spec = importlib.util.spec_from_file_location(module_name, file_path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


def _get_or_load(key: str, loader):
    """懒加载 + 缓存（缓存 None 表示加载失败，不重试）。"""
    if key not in _cache:
        try:
            _cache[key] = loader()
        except Exception as e:  # noqa: BLE001 —— 兜底，任何异常都缓存 None
            logger.warning("加载 %s 失败: %s", key, e)
            _cache[key] = None
    return _cache[key]


# --------------------------------------------------------------------------- #
# DDI-LLM：转导式 GCN，图内药物点积 -> sigmoid 概率
# --------------------------------------------------------------------------- #
def _load_ddi_predictor():
    base = MODELS_DIR / "DDI-LLM"
    ckpt_path = base / "weights" / "ddi_gcn_morgan.pt"
    if not ckpt_path.exists():
        logger.warning("[DDI-LLM] 权重缺失: %s", ckpt_path)
        return None

    # 先注册 model 模块，再导入 inference（inference.py 内部 from model import GCN）
    model_mod = _load_module("ddi_llm.model", base / "model.py")
    sys.modules["model"] = model_mod
    try:
        inf = _load_module("ddi_llm.inference", base / "inference.py")
    finally:
        sys.modules.pop("model", None)

    ckpt, z = inf.load_model(str(ckpt_path))
    node_id_map = ckpt["node_id_map"]

    def predict(drug_a: str, drug_b: str) -> float:
        if drug_a not in node_id_map or drug_b not in node_id_map:
            missing = [d for d in (drug_a, drug_b) if d not in node_id_map]
            raise ValueError(f"药物不在训练图内，无法预测: {', '.join(missing)}")
        u = node_id_map[drug_a]
        v = node_id_map[drug_b]
        logit = float((z[u] * z[v]).sum().item())
        return float(torch.sigmoid(torch.tensor(logit)).item())

    logger.info("[DDI-LLM] 权重加载完成（%d 个图内药物）", len(node_id_map))
    return predict


def get_ddi_predictor():
    """返回 ``(drug_a, drug_b) -> 概率`` 的可调用对象；加载失败返回 None。"""
    return _get_or_load("ddi", _load_ddi_predictor)


# --------------------------------------------------------------------------- #
# KAN-MoDTI：药物-靶点二分类，KANMoDTIPredictor.predict -> (类别, 概率)
# --------------------------------------------------------------------------- #
def _load_dti_predictor():
    base = MODELS_DIR / "KAN-MoDTI"
    weight = base / "weights" / "human_final.pth"
    if not weight.exists():
        logger.warning("[KAN-MoDTI] 权重缺失: %s", weight)
        return None

    # 依赖链：inference.py -> model.py -> kan.py
    kan_mod = _load_module("kan_modti.kan", base / "kan.py")
    sys.modules["kan"] = kan_mod
    model_mod = _load_module("kan_modti.model", base / "model.py")
    sys.modules["model"] = model_mod
    try:
        inf = _load_module("kan_modti.inference", base / "inference.py")
    finally:
        sys.modules.pop("model", None)
        sys.modules.pop("kan", None)

    predictor = inf.KANMoDTIPredictor(device=_auto_device())
    logger.info("[KAN-MoDTI] 权重加载完成")
    return predictor


def get_dti_predictor():
    """返回 ``KANMoDTIPredictor`` 实例；加载失败返回 None。"""
    return _get_or_load("dti", _load_dti_predictor)


# --------------------------------------------------------------------------- #
# FlashPPI：蛋白-蛋白相互作用，FlashPPIPredictor.predict -> dict
# --------------------------------------------------------------------------- #
def _load_ppi_predictor():
    base = MODELS_DIR / "FlashPPI"
    weight = base / "weights" / "model.safetensors"
    if not weight.exists():
        logger.warning("[FlashPPI] 权重缺失: %s", weight)
        return None

    inf = _load_module("flashppi.inference", base / "inference.py")
    predictor = inf.FlashPPIPredictor(device=_auto_device())
    logger.info("[FlashPPI] 权重加载完成")
    return predictor


def get_ppi_predictor():
    """返回 ``FlashPPIPredictor`` 实例；加载失败返回 None。"""
    return _get_or_load("ppi", _load_ppi_predictor)
