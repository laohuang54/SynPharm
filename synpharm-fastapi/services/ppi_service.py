from core.schemas import PredictionMetrics
from core.base_algo import BaseAlgo
from core.exceptions import InvalidInputError, ModelNotFoundError
from services.algorithm_adapters import get_ppi_predictor


class PPIService(BaseAlgo):

    def __init__(self):
        self._predictor = None
        self._loaded = False

    def _get_predictor(self):
        if not self._loaded:
            self._predictor = get_ppi_predictor()
            self._loaded = True
        return self._predictor

    def predict(self, data: dict) -> PredictionMetrics:
        protein_a = data.get("protein_a", "")
        protein_b = data.get("protein_b", "")

        predictor = self._get_predictor()
        if predictor is None:
            # 权重/依赖缺失：直接报错，不再返回 mock
            raise ModelNotFoundError("PPI")

        # 真实推理：contact_score 是残基级最大接触概率（0~1），作为置信度
        try:
            result = predictor.predict(protein_a, protein_b)
        except ValueError as e:
            raise InvalidInputError(str(e))
        return self._to_metrics(result)

    def _to_metrics(self, result: dict) -> PredictionMetrics:
        contact_score = float(result.get("contact_score", 0.0))
        return PredictionMetrics(
            target_id="PPI_TARGET",
            target_name="蛋白质相互作用",
            confidence_score=round(contact_score, 4),
            confidence_level=_confidence_level(contact_score),
            interactions=[]
        )


def _confidence_level(score: float) -> str:
    if score >= 0.8:
        return "high"
    if score >= 0.6:
        return "medium"
    return "low"
