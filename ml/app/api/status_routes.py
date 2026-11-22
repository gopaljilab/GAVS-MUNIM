from fastapi import APIRouter, Depends
from app.auth import verify_internal_ml_key
from app.schemas.health import MlStatusResponse
from app.services.model_manager import model_manager

router = APIRouter(prefix="/api/v1/ml", tags=["ML Status"])

@router.get("/status", response_model=MlStatusResponse)
def get_ml_status(authenticated: bool = Depends(verify_internal_ml_key)):
    """
    Returns the current status of the machine learning engine.
    Requires internal service-to-service authentication.
    Reports NO_VALIDATED_MODEL when no validated model is loaded.
    """
    return model_manager.get_status()
