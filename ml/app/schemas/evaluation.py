from typing import Dict, List, Optional
from datetime import datetime
from pydantic import BaseModel, Field

class BaselineComparisonMetrics(BaseModel):
    majority_class_accuracy: float
    majority_class_brier_score: float
    heuristic_precision: float
    heuristic_recall: float
    heuristic_f1: float


class ModelEvaluationMetrics(BaseModel):
    precision: float
    recall: float
    f1_score: float
    pr_auc: Optional[float] = None
    roc_auc: Optional[float] = None
    brier_score: float
    expected_calibration_error: Optional[float] = None


class ConfusionMatrix(BaseModel):
    true_positives: int
    false_positives: int
    true_negatives: int
    false_negatives: int


class ModelEvaluationReport(BaseModel):
    """
    Standardized, reproducible evaluation report for delivery-risk models.
    Enforces chronological walk-forward evaluation and multi-metric validation beyond raw accuracy.
    """
    report_id: str
    model_id: str
    model_version: str
    dataset_id: str
    dataset_version: str
    feature_schema_version: str = "1.0.0"
    evaluation_timestamp: datetime = Field(default_factory=datetime.utcnow)
    validation_methodology: str = "CHRONOLOGICAL_WALK_FORWARD"

    # Dataset Scope
    training_date_range_start: str
    training_date_range_end: str
    evaluation_date_range_start: str
    evaluation_date_range_end: str
    total_samples: int
    eligible_samples: int
    excluded_samples: int
    outcome_distribution: Dict[str, int]

    # Comparative Results
    baseline_metrics: BaselineComparisonMetrics
    model_metrics: ModelEvaluationMetrics
    confusion_matrix: ConfusionMatrix

    # Risk & Guardrails
    supplier_concentration_warning: bool = False
    material_concentration_warning: bool = False
    class_imbalance_ratio: float
    validation_warnings: List[str] = Field(default_factory=list)
    promotion_eligible: bool = False
    eligibility_notes: str
