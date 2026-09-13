from core.schemas import PredictionMetrics
from core.base_algo import BaseAlgo
from core.exceptions import InvalidInputError, ModelNotFoundError
from services.algorithm_adapters import get_ddi_predictor


class DDIService(BaseAlgo):

    def __init__(self):
        self._predictor = None
        self._loaded = False

    def _get_predictor(self):
        if not self._loaded:
            self._predictor = get_ddi_predictor()
            self._loaded = True
        return self._predictor

    def predict(self, data: dict) -> PredictionMetrics:
        drug_a = data.get("drug_a", "")
        drug_b = data.get("drug_b", "")

        predictor = self._get_predictor()
        if predictor is None:
            # 权重/依赖缺失：直接报错，不再返回 mock
            raise ModelNotFoundError("DDI")

        # 真实推理：图内药物点积 -> sigmoid 概率；图外药物抛 ValueError -> 400
        try:
            confidence = predictor(drug_a, drug_b)
        except ValueError as e:
            raise InvalidInputError(str(e))
        return self._to_metrics(confidence)

    def _to_metrics(self, confidence: float) -> PredictionMetrics:
        return PredictionMetrics(
            target_id="DDI_TARGET",
            target_name="药物相互作用",
            confidence_score=round(confidence, 4),
            confidence_level=_confidence_level(confidence),
            interactions=[]
        )


def _confidence_level(score: float) -> str:
    if score >= 0.8:
        return "high"
    if score >= 0.6:
        return "medium"
    return "low"
