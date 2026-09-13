from core.schemas import PredictionMetrics
from core.base_algo import BaseAlgo
from core.exceptions import InvalidInputError, ModelNotFoundError
from services.algorithm_adapters import get_dti_predictor


class DTIService(BaseAlgo):

    def __init__(self):
        self._predictor = None
        self._loaded = False

    def _get_predictor(self):
        if not self._loaded:
            self._predictor = get_dti_predictor()
            self._loaded = True
        return self._predictor

    def predict(self, data: dict) -> PredictionMetrics:
        smiles = data.get("drug_smiles", "")
        target_seq = data.get("target_seq", "")

        predictor = self._get_predictor()
        if predictor is None:
            # 权重/依赖缺失：直接报错，不再返回 mock
            raise ModelNotFoundError("DTI")

        # 真实推理：SMILES 非法 / 序列 < 31 残基 会抛 ValueError -> 400
        try:
            pred, score = predictor.predict(smiles, target_seq)
        except ValueError as e:
            raise InvalidInputError(str(e))
        return self._to_metrics(pred, score)

    def _to_metrics(self, pred: int, score: float) -> PredictionMetrics:
        # KAN-MoDTI 是二分类，无回归结合亲和力，binding_affinity 置空
        return PredictionMetrics(
            target_id="P00533",
            target_name="EGFR",
            binding_affinity=None,
            confidence_score=round(score, 4),
            confidence_level=_confidence_level(score),
            interactions=[]
        )


def _confidence_level(score: float) -> str:
    if score >= 0.8:
        return "high"
    if score >= 0.6:
        return "medium"
    return "low"
