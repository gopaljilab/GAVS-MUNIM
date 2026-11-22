from typing import List, Tuple
from app.schemas.dataset import DatasetManifest, DatasetProvenanceEnum, DatasetRecordMetadata
from app.schemas.labels import OutcomeLabelEnum

class DatasetValidator:
    """
    Validates dataset manifests and records for training/evaluation pipelines.
    Enforces provenance boundaries (never merge synthetic and real data) and valid label taxonomy.
    """
    @staticmethod
    def validate_manifest(manifest: DatasetManifest) -> Tuple[bool, List[str]]:
        errors = []
        if manifest.total_records != (manifest.eligible_records + manifest.excluded_records):
            errors.append("Total records does not match sum of eligible and excluded records.")

        sum_labels = sum(manifest.label_distribution.values())
        if sum_labels != manifest.total_records:
            errors.append(f"Label distribution count ({sum_labels}) does not equal total records ({manifest.total_records}).")

        # Check for invalid labels in distribution
        valid_labels = {e.value for e in OutcomeLabelEnum}
        for label in manifest.label_distribution.keys():
            if label not in valid_labels:
                errors.append(f"Unrecognized outcome label in distribution: {label}")

        return len(errors) == 0, errors

    @staticmethod
    def validate_provenance_isolation(records: List[DatasetRecordMetadata]) -> Tuple[bool, List[str]]:
        errors = []
        provenances = {r.provenance for r in records}
        if DatasetProvenanceEnum.SYNTHETIC in provenances and DatasetProvenanceEnum.REAL in provenances:
            errors.append(
                "Provenance contamination error: Dataset contains both SYNTHETIC and REAL records. "
                "Synthetic records must never be merged into real-world training/evaluation datasets."
            )
        return len(errors) == 0, errors

dataset_validator = DatasetValidator()
