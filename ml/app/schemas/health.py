from pydantic import BaseModel, Field
from typing import Optional

class HealthResponse(BaseModel):
    status: str = "UP"
    service: str = "munim-ml-service"
    version: str = "0.1.0"
    timestamp: str

class ReadinessResponse(BaseModel):
    status: str = "READY"
    serviceReady: bool = True
    modelReady: bool = False
    activeModelStatus: str = "NO_VALIDATED_MODEL"
    timestamp: str

class MlStatusResponse(BaseModel):
    serviceVersion: str = "0.1.0"
    featureSchemaVersion: str = "1.0.0"
    modelAvailable: bool = False
    activeModelVersion: Optional[str] = None
    trainingStatus: str = "IDLE"
    latestTrainingRun: Optional[str] = None
    predictionsEnabled: bool = False
    status: str = "NO_VALIDATED_MODEL"
    statusMessage: str = "No validated ML model artifact available. Predictions disabled until authentic pilot outcome evidence is collected and validated."
    eligibleHistoricalOutcomesCount: int = 0
    datasetProvenancePolicy: str = "STRICT_SEPARATION_SYNTHETIC_VS_REAL"
