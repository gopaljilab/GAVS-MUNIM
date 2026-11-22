from fastapi import APIRouter, Depends
from app.auth import verify_internal_ml_key
from app.schemas.prediction import DeliveryRiskPredictionRequest, DeliveryRiskPredictionResponse
from app.services.model_manager import model_manager

router = APIRouter(prefix="/api/v1/ml", tags=["Inference"])

@router.post("/predict/delivery-risk", response_model=DeliveryRiskPredictionResponse)
def predict_delivery_risk(
    request: DeliveryRiskPredictionRequest,
    authenticated: bool = Depends(verify_internal_ml_key)
):
    """
    Delivery risk inference endpoint.
    Guaranteed behavior in Phase ML-2: Rejects with HTTP 503 (NO_VALIDATED_MODEL).
    Prevents fabricated, random, or premature predictions.
    """
    return model_manager.predict_delivery_risk(request.features)
