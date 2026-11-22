from typing import List, Optional
from datetime import datetime
from pydantic import BaseModel, Field
from app.schemas.features import SupplierDeliveryFeatureVector

class DeliveryRiskPredictionRequest(BaseModel):
    features: SupplierDeliveryFeatureVector


class DeliveryRiskPredictionResponse(BaseModel):
    prediction_id: str
    target_po_id: str
    model_version: str
    predicted_late_probability: float = Field(ge=0.0, le=1.0)
    risk_category: str                          # LOW, MEDIUM, HIGH
    confidence_tier: str                        # HIGH, MEDIUM, LOW, UNRATED
    decision_threshold: float = 0.50
    warnings: List[str] = Field(default_factory=list)
    prediction_timestamp: datetime = Field(default_factory=datetime.utcnow)
    is_advisory_only: bool = True               # Strict non-autonomous safeguard
