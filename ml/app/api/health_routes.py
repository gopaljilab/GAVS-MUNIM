from datetime import datetime
from fastapi import APIRouter
from app.config import settings
from app.schemas.health import HealthResponse, ReadinessResponse
from app.services.model_manager import model_manager

router = APIRouter(tags=["Health"])

@router.get("/health", response_model=HealthResponse)
def get_health():
    """
    Basic service health probe. Excluded from internal authentication for container liveness monitoring.
    """
    return HealthResponse(
        status="UP",
        service=settings.service_name,
        version=settings.service_version,
        timestamp=datetime.utcnow().isoformat() + "Z"
    )

@router.get("/ready", response_model=ReadinessResponse)
def get_readiness():
    """
    Readiness probe indicating whether the microservice is operational.
    Explicitly reports modelReady=False when no validated model artifact is available.
    """
    status = model_manager.get_status()
    return ReadinessResponse(
        status="READY",
        serviceReady=True,
        modelReady=status.modelAvailable,
        activeModelStatus=status.status,
        timestamp=datetime.utcnow().isoformat() + "Z"
    )
