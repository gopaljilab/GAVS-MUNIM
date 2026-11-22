from datetime import datetime
from app.schemas.dataset import DatasetManifest, DatasetProvenanceEnum, DatasetRecordMetadata
from app.services.dataset_validator import dataset_validator

def test_valid_dataset_manifest_service():
    manifest = DatasetManifest(
        dataset_id="ds-pilot-001",
        dataset_version="1.0.0",
        provenance=DatasetProvenanceEnum.REAL,
        total_records=100,
        eligible_records=85,
        excluded_records=15,
        label_distribution={
            "ON_TIME_COMPLETED": 70,
            "LATE_COMPLETED": 10,
            "FAILED_FULFILLMENT": 5,
            "OPEN_OR_CENSORED": 12,
            "CANCELLED": 3
        },
        dataset_sha256="e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    )
    is_valid, errors = dataset_validator.validate_manifest(manifest)
    assert is_valid is True
    assert len(errors) == 0

def test_manifest_sum_mismatch_detected():
    manifest = DatasetManifest(
        dataset_id="ds-err-002",
        dataset_version="1.0.0",
        provenance=DatasetProvenanceEnum.SYNTHETIC,
        total_records=100,
        eligible_records=60,
        excluded_records=20, # 60 + 20 = 80 != 100
        label_distribution={"ON_TIME_COMPLETED": 100},
        dataset_sha256="fakehash"
    )
    is_valid, errors = dataset_validator.validate_manifest(manifest)
    assert is_valid is False
    assert any("Total records does not match sum" in e for e in errors)

def test_provenance_contamination_rejected():
    records = [
        DatasetRecordMetadata(
            record_id="r1", target_po_id="po-1", supplier_id="s1", material_id="m1",
            provenance=DatasetProvenanceEnum.REAL, outcome_label="ON_TIME_COMPLETED", is_eligible_for_training=True
        ),
        DatasetRecordMetadata(
            record_id="r2", target_po_id="po-2", supplier_id="s2", material_id="m2",
            provenance=DatasetProvenanceEnum.SYNTHETIC, outcome_label="LATE_COMPLETED", is_eligible_for_training=True
        )
    ]
    is_valid, errors = dataset_validator.validate_provenance_isolation(records)
    assert is_valid is False
    assert any("Provenance contamination error" in e for e in errors)

def test_dataset_validate_manifest_api(client, auth_headers):
    payload = {
        "dataset_id": "ds-test-01",
        "dataset_version": "1.0.0",
        "feature_schema_version": "1.0.0",
        "provenance": "REAL",
        "total_records": 50,
        "eligible_records": 40,
        "excluded_records": 10,
        "label_distribution": {
            "ON_TIME_COMPLETED": 35,
            "LATE_COMPLETED": 5,
            "OPEN_OR_CENSORED": 10
        },
        "dataset_sha256": "fakehash123"
    }
    res = client.post("/api/v1/ml/datasets/validate-manifest", json=payload, headers=auth_headers)
    assert res.status_code == 200
    assert res.json()["is_valid"] is True
