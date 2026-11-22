from datetime import datetime, timedelta
from decimal import Decimal
from app.schemas.labels import (
    OutcomeLabelEnum,
    PurchaseOrderLifecycleRecord,
    derive_outcome_label
)

def test_on_time_completed():
    deadline = datetime.utcnow() + timedelta(days=5)
    delivered = datetime.utcnow() + timedelta(days=3) # Delivered 2 days early
    record = PurchaseOrderLifecycleRecord(
        po_id="po-1",
        status="COMPLETED",
        po_delivery_status="PO_ON_TIME",
        ordered_quantity=Decimal("1000"),
        cumulative_received_quantity=Decimal("1000"),
        promised_delivery_date=deadline,
        first_completed_at=delivered
    )
    assert derive_outcome_label(record) == OutcomeLabelEnum.ON_TIME_COMPLETED

def test_late_completed():
    deadline = datetime.utcnow() + timedelta(days=5)
    delivered = datetime.utcnow() + timedelta(days=8) # Delivered 3 days late
    record = PurchaseOrderLifecycleRecord(
        po_id="po-2",
        status="COMPLETED",
        po_delivery_status="PO_DELAYED",
        ordered_quantity=Decimal("1000"),
        cumulative_received_quantity=Decimal("1000"),
        promised_delivery_date=deadline,
        first_completed_at=delivered
    )
    assert derive_outcome_label(record) == OutcomeLabelEnum.LATE_COMPLETED

def test_short_closed_po_is_failed_fulfillment_not_late():
    # Crucial ML-2 requirement: Short-closure indicates failed fulfillment, NOT a normal late delivery
    deadline = datetime.utcnow() + timedelta(days=5)
    record = PurchaseOrderLifecycleRecord(
        po_id="po-3",
        status="COMPLETED",
        po_delivery_status="PO_SHORT_CLOSED",
        ordered_quantity=Decimal("1000"),
        cumulative_received_quantity=Decimal("600"),
        unfulfilled_quantity=Decimal("400"),
        promised_delivery_date=deadline,
        first_completed_at=None,
        was_short_closed=True
    )
    label = derive_outcome_label(record)
    assert label == OutcomeLabelEnum.FAILED_FULFILLMENT
    assert label != OutcomeLabelEnum.LATE_COMPLETED

def test_reopened_po_evaluated_against_original_deadline():
    original_deadline = datetime.utcnow() + timedelta(days=5)
    subsequent_completion = datetime.utcnow() + timedelta(days=12) # Late against original promise
    record = PurchaseOrderLifecycleRecord(
        po_id="po-4",
        status="COMPLETED",
        ordered_quantity=Decimal("1000"),
        cumulative_received_quantity=Decimal("1000"),
        promised_delivery_date=original_deadline,
        first_completed_at=subsequent_completion,
        was_short_closed=True,
        was_reopened=True
    )
    # Subsequent delivery after deadline must evaluate as LATE_COMPLETED, not on-time
    assert derive_outcome_label(record) == OutcomeLabelEnum.LATE_COMPLETED

def test_cancelled_po_is_cancelled():
    deadline = datetime.utcnow() + timedelta(days=5)
    record = PurchaseOrderLifecycleRecord(
        po_id="po-5",
        status="CANCELLED",
        ordered_quantity=Decimal("1000"),
        cumulative_received_quantity=Decimal("0"),
        promised_delivery_date=deadline
    )
    assert derive_outcome_label(record) == OutcomeLabelEnum.CANCELLED

def test_open_not_yet_due_is_censored():
    future_deadline = datetime.utcnow() + timedelta(days=10)
    record = PurchaseOrderLifecycleRecord(
        po_id="po-6",
        status="ISSUED",
        ordered_quantity=Decimal("1000"),
        cumulative_received_quantity=Decimal("0"),
        promised_delivery_date=future_deadline,
        evaluation_timestamp=datetime.utcnow()
    )
    assert derive_outcome_label(record) == OutcomeLabelEnum.OPEN_OR_CENSORED

def test_open_overdue_is_failed_fulfillment():
    past_deadline = datetime.utcnow() - timedelta(days=2)
    record = PurchaseOrderLifecycleRecord(
        po_id="po-7",
        status="PARTIALLY_DELIVERED",
        ordered_quantity=Decimal("1000"),
        cumulative_received_quantity=Decimal("400"),
        promised_delivery_date=past_deadline,
        evaluation_timestamp=datetime.utcnow()
    )
    assert derive_outcome_label(record) == OutcomeLabelEnum.FAILED_FULFILLMENT
