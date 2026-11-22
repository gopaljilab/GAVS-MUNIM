from enum import Enum
from typing import Dict, List, Optional
from datetime import datetime
from pydantic import BaseModel, Field

class DatasetProvenanceEnum(str, Enum):
    """
    Source categorization for procurement dataset records.
    Prevents synthetic demo/test records from contaminating real-world evaluation models.
    """
    SYNTHETIC = "SYNTHETIC"   # Generated via test seeds, mock data generators, or integration tests
    REAL = "REAL"             # Authentic manufacturing workshop procurement outcomes
    BENCHMARK = "BENCHMARK"   # Curated, externally verified industrial benchmark dataset


class DatasetRecordMetadata(BaseModel):
    record_id: str
    target_po_id: str
    supplier_id: str
    material_id: str
    provenance: DatasetProvenanceEnum
    outcome_label: str
    is_eligible_for_training: bool
    exclusion_reason: Optional[str] = None


class DatasetManifest(BaseModel):
    """
    Reproducible manifest for a dataset extraction snapshot.
    """
    dataset_id: str
    dataset_version: str
    feature_schema_version: str = "1.0.0"
    created_at: datetime = Field(default_factory=datetime.utcnow)
    extracted_by: str = "munim-pipeline"
    provenance: DatasetProvenanceEnum
    total_records: int
    eligible_records: int
    excluded_records: int
    label_distribution: Dict[str, int]
    dataset_sha256: str
    source_query_hash: Optional[str] = None
    notes: Optional[str] = None
