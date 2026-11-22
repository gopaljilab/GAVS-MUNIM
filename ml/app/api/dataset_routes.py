from typing import Dict, List
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from app.auth import verify_internal_ml_key
from app.schemas.dataset import DatasetManifest, DatasetRecordMetadata
from app.services.dataset_validator import dataset_validator

router = APIRouter(prefix="/api/v1/ml/datasets", tags=["Datasets"])

class ManifestValidationResponse(BaseModel):
    is_valid: bool
    errors: List[str]

class ProvenanceValidationRequest(BaseModel):
    records: List[DatasetRecordMetadata]

class ProvenanceValidationResponse(BaseModel):
    is_valid: bool
    errors: List[str]

@router.post("/validate-manifest", response_model=ManifestValidationResponse)
def validate_manifest(
    manifest: DatasetManifest,
    authenticated: bool = Depends(verify_internal_ml_key)
):
    """
    Validates a dataset manifest for structural consistency, record sums, and label distribution.
    """
    is_valid, errors = dataset_validator.validate_manifest(manifest)
    return ManifestValidationResponse(is_valid=is_valid, errors=errors)

@router.post("/validate-provenance", response_model=ProvenanceValidationResponse)
def validate_provenance(
    request: ProvenanceValidationRequest,
    authenticated: bool = Depends(verify_internal_ml_key)
):
    """
    Enforces strict provenance boundary: rejects datasets blending synthetic and real records.
    """
    is_valid, errors = dataset_validator.validate_provenance_isolation(request.records)
    return ProvenanceValidationResponse(is_valid=is_valid, errors=errors)
