from app.schemas.model_lifecycle import ModelMetadata, ModelPromotionStateEnum
from app.schemas.evaluation import ModelEvaluationReport, BaselineComparisonMetrics, ModelEvaluationMetrics, ConfusionMatrix

def test_model_metadata_lifecycle():
    meta = ModelMetadata(
        model_id="mod-001",
        model_version="1.0.0",
        algorithm="LogisticRegression(class_weight='balanced')",
        training_dataset_id="ds-pilot-001",
        training_dataset_sha256="sha256sample",
        feature_names=["ordered_quantity", "agreed_unit_price", "promised_lead_days", "hist_point_in_time_otd_rate"],
        hyperparameters={"C": "1.0", "max_iter": "100"}
    )
    assert meta.promotion_state == ModelPromotionStateEnum.DRAFT
    assert meta.is_active is False

def test_evaluation_report_api_validation(client, auth_headers):
    report_payload = {
        "report_id": "rep-001",
        "model_id": "mod-001",
        "model_version": "1.0.0",
        "dataset_id": "ds-001",
        "dataset_version": "1.0.0",
        "training_date_range_start": "2026-01-01",
        "training_date_range_end": "2026-06-30",
        "evaluation_date_range_start": "2026-07-01",
        "evaluation_date_range_end": "2026-08-31",
        "total_samples": 120,
        "eligible_samples": 100,
        "excluded_samples": 20,
        "outcome_distribution": {"ON_TIME_COMPLETED": 80, "LATE_COMPLETED": 15, "FAILED_FULFILLMENT": 5},
        "baseline_metrics": {
            "majority_class_accuracy": 0.80,
            "majority_class_brier_score": 0.16,
            "heuristic_precision": 0.55,
            "heuristic_recall": 0.60,
            "heuristic_f1": 0.57
        },
        "model_metrics": {
            "precision": 0.72,
            "recall": 0.80,
            "f1_score": 0.76,
            "pr_auc": 0.78,
            "roc_auc": 0.84,
            "brier_score": 0.11,
            "expected_calibration_error": 0.04
        },
        "confusion_matrix": {
            "true_positives": 16,
            "false_positives": 6,
            "true_negatives": 74,
            "false_negatives": 4
        },
        "supplier_concentration_warning": True, # Warning present!
        "class_imbalance_ratio": 4.0,
        "promotion_eligible": True,
        "eligibility_notes": "Passed technical metric thresholds"
    }
    res = client.post("/api/v1/ml/evaluations/validate-report", json=report_payload, headers=auth_headers)
    assert res.status_code == 200
    data = res.json()
    assert data["is_valid"] is True
    # Crucial rule: High concentration warning blocks promotion eligibility
    assert data["can_promote"] is False
    assert any("High supplier concentration detected" in w for w in data["warnings"])
