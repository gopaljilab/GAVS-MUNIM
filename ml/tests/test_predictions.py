from datetime import datetime
from uuid import uuid4

def test_prediction_endpoint_rejects_when_no_validated_model(client, auth_headers):
    # Calling inference without a validated model MUST return HTTP 503
    request_payload = {
        "features": {
            "feature_schema_version": "1.0.0",
            "feature_extraction_timestamp": datetime.utcnow().isoformat() + "Z",
            "target_po_id": str(uuid4()),
            "supplier_id": str(uuid4()),
            "material_id": str(uuid4()),
            "ordered_quantity": "1000.0000",
            "agreed_unit_price": "95.00",
            "freight_basis": "SEPARATE",
            "promised_lead_days": 5,
            "hist_eligible_po_count": 0,
            "hist_ontime_po_count": 0,
            "hist_point_in_time_otd_rate": None
        }
    }
    res = client.post("/api/v1/ml/predict/delivery-risk", json=request_payload, headers=auth_headers)
    assert res.status_code == 503
    detail = res.json()["detail"]
    assert "Predictions are disabled: NO_VALIDATED_MODEL is currently loaded" in detail
