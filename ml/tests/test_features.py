import pytest
from datetime import datetime
from decimal import Decimal
from uuid import uuid4
from pydantic import ValidationError
from app.schemas.features import SupplierDeliveryFeatureVector

def test_valid_feature_vector():
    now = datetime.utcnow()
    fv = SupplierDeliveryFeatureVector(
        feature_extraction_timestamp=now,
        target_po_id=uuid4(),
        supplier_id=uuid4(),
        material_id=uuid4(),
        ordered_quantity=Decimal("1000.0000"),
        agreed_unit_price=Decimal("95.00"),
        agreed_freight_amount=Decimal("1500.00"),
        freight_basis="SEPARATE",
        promised_lead_days=5,
        factory_lead_days=7,
        lead_time_slack_days=2,
        hist_eligible_po_count=3,
        hist_ontime_po_count=3,
        hist_point_in_time_otd_rate=Decimal("100.00"),
        hist_avg_delay_days=Decimal("0.0"),
        hist_partial_delivery_rate=Decimal("33.33"),
        hist_net_usable_yield=Decimal("98.50"),
        hist_material_po_count=2,
        has_material_evidence=True
    )
    assert fv.feature_schema_version == "1.0.0"
    assert fv.ordered_quantity == Decimal("1000.0000")
    assert fv.hist_point_in_time_otd_rate == Decimal("100.00")

def test_non_zero_unknown_principle_enforcement():
    # When 0 eligible historical orders exist, OTD rate must remain None (rendered as '—' in UI)
    now = datetime.utcnow()

    # 1. Null OTD is valid for new supplier
    fv = SupplierDeliveryFeatureVector(
        feature_extraction_timestamp=now,
        target_po_id=uuid4(),
        supplier_id=uuid4(),
        material_id=uuid4(),
        ordered_quantity=Decimal("500.0000"),
        agreed_unit_price=Decimal("92.00"),
        freight_basis="INCLUDED_IN_UNIT_PRICE",
        promised_lead_days=3,
        hist_eligible_po_count=0,
        hist_ontime_po_count=0,
        hist_point_in_time_otd_rate=None  # Explicitly None
    )
    assert fv.hist_point_in_time_otd_rate is None

    # 2. Assigning 0.0 or 100.0 to OTD when eligible_po_count is 0 must fail validation
    with pytest.raises(ValidationError):
        SupplierDeliveryFeatureVector(
            feature_extraction_timestamp=now,
            target_po_id=uuid4(),
            supplier_id=uuid4(),
            material_id=uuid4(),
            ordered_quantity=Decimal("500.0000"),
            agreed_unit_price=Decimal("92.00"),
            freight_basis="INCLUDED_IN_UNIT_PRICE",
            promised_lead_days=3,
            hist_eligible_po_count=0,
            hist_ontime_po_count=0,
            hist_point_in_time_otd_rate=Decimal("0.00")  # Invalid: zero-fill penalized unfairly
        )

def test_negative_quantity_or_price_rejected():
    now = datetime.utcnow()
    with pytest.raises(ValidationError):
        SupplierDeliveryFeatureVector(
            feature_extraction_timestamp=now,
            target_po_id=uuid4(),
            supplier_id=uuid4(),
            material_id=uuid4(),
            ordered_quantity=Decimal("-100.0000"),  # Invalid negative quantity
            agreed_unit_price=Decimal("95.00"),
            freight_basis="SEPARATE",
            promised_lead_days=5
        )
