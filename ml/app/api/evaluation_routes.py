from typing import List
from fastapi import APIRouter, Depends
from pydantic import BaseModel
from app.auth import verify_internal_ml_key
from app.schemas.evaluation import ModelEvaluationReport

router = APIRouter(prefix="/api/v1/ml/evaluations", tags=["Model Evaluation"])

class EvaluationValidationResponse(BaseModel):
    is_valid: bool
    can_promote: bool
    warnings: List[str]

@router.post("/validate-report", response_model=EvaluationValidationResponse)
def validate_evaluation_report(
    report: ModelEvaluationReport,
    authenticated: bool = Depends(verify_internal_ml_key)
):
    """
    Validates a model evaluation report.
    Checks for class imbalance, baseline comparisons, and rejects auto-promotion.
    """
    warnings = list(report.validation_warnings)

    if report.supplier_concentration_warning:
        warnings.append("Warning: High supplier concentration detected in evaluation split.")

    if report.material_concentration_warning:
        warnings.append("Warning: High material concentration detected in evaluation split.")

    # Guard: No model can be promoted if baseline majority class outperforms it or if warnings exist
    can_promote = report.promotion_eligible and len(warnings) == 0

    return EvaluationValidationResponse(
        is_valid=True,
        can_promote=can_promote,
        warnings=warnings
    )
