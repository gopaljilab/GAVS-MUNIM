from enum import Enum
from typing import Dict, List, Optional
from datetime import datetime
from pydantic import BaseModel, Field

class ModelPromotionStateEnum(str, Enum):
    """
    Lifecycle promotion states for a trained model artifact.
    Automatic promotion is strictly forbidden.
    """
    DRAFT = "DRAFT"               # Initial local training experiment
    CANDIDATE = "CANDIDATE"       # Evaluated against baselines, pending review
    VALIDATED = "VALIDATED"       # Passed chronological & calibration criteria, eligible for promotion
    PROMOTED = "PROMOTED"         # Formally activated for inference in production
    REJECTED = "REJECTED"         # Failed evaluation criteria or safety guardrails
    ARCHIVED = "ARCHIVED"         # Deprecated previous version superseded by newer model


class ModelMetadata(BaseModel):
    """
    Metadata contract accompanying every serialized model artifact.
    """
    model_id: str
    model_version: str
    algorithm: str
    feature_schema_version: str = "1.0.0"
    training_dataset_id: str
    training_dataset_sha256: str
    trained_at: datetime = Field(default_factory=datetime.utcnow)
    promotion_state: ModelPromotionStateEnum = ModelPromotionStateEnum.DRAFT
    is_active: bool = False
    feature_names: List[str]
    hyperparameters: Dict[str, str] = Field(default_factory=dict)
    promoted_by: Optional[str] = None
    promoted_at: Optional[datetime] = None
    promotion_rationale: Optional[str] = None
    artifact_path: Optional[str] = None
    artifact_sha256: Optional[str] = None
