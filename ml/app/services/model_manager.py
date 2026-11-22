from typing import Optional
from fastapi import HTTPException, status
from app.config import settings
from app.schemas.health import MlStatusResponse
from app.schemas.features import SupplierDeliveryFeatureVector
from app.schemas.prediction import DeliveryRiskPredictionResponse

class ModelManager:
    """
    Manages active machine learning model artifacts and prediction states.
    For Phase ML-2, operates strictly in NO_VALIDATED_MODEL state without fabricated predictions.
    """
    def __init__(self):
        self._active_model_version: Optional[str] = None
        self._model_artifact = None
        self._training_status: str = "IDLE"
        self._latest_training_run: Optional[str] = None

    def get_status(self) -> MlStatusResponse:
        return MlStatusResponse(
            serviceVersion=settings.service_version,
            featureSchemaVersion=settings.feature_schema_version,
            modelAvailable=self._active_model_version is not None,
            activeModelVersion=self._active_model_version,
            trainingStatus=self._training_status,
            latestTrainingRun=self._latest_training_run,
            predictionsEnabled=self._active_model_version is not None,
            status="ACTIVE_MODEL" if self._active_model_version else "NO_VALIDATED_MODEL",
            statusMessage=(
                f"Model version {self._active_model_version} active for inference."
                if self._active_model_version
                else "No validated ML model artifact available. Predictions disabled until authentic pilot outcome evidence is collected and validated."
            ),
            eligibleHistoricalOutcomesCount=0,
            datasetProvenancePolicy="STRICT_SEPARATION_SYNTHETIC_VS_REAL"
        )

    def predict_delivery_risk(self, features: SupplierDeliveryFeatureVector) -> DeliveryRiskPredictionResponse:
        """
        Inference execution.
        If no validated model is loaded, explicitly rejects with HTTP 503 rather than fabricating random scores.
        """
        if self._active_model_version is None or self._model_artifact is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Predictions are disabled: NO_VALIDATED_MODEL is currently loaded. Collect verified pilot outcomes before training."
            )

        # When model is loaded in future phases, inference logic executes here.
        raise NotImplementedError("Inference execution reserved for Phase ML-3")

model_manager = ModelManager()
