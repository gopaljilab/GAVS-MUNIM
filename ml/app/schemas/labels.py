from enum import Enum
from typing import Optional
from datetime import datetime
from decimal import Decimal
from pydantic import BaseModel, Field

class OutcomeLabelEnum(str, Enum):
    """
    Exhaustive outcome classification for Purchase Order fulfillment.
    Maintains clean separation between physical transport timeliness, fulfillment failures, and administrative state.
    """
    ON_TIME_COMPLETED = "ON_TIME_COMPLETED"       # 100% contracted quantity fulfilled on or before agreed deadline
    LATE_COMPLETED = "LATE_COMPLETED"             # 100% contracted quantity fulfilled after agreed deadline
    FAILED_FULFILLMENT = "FAILED_FULFILLMENT"     # Short-closed by factory owner due to unsupplied balance (PO_SHORT_CLOSED)
    OPEN_OR_CENSORED = "OPEN_OR_CENSORED"         # Active open orders not yet due or under unverified intake
    CANCELLED = "CANCELLED"                       # Administratively terminated prior to delivery


class PurchaseOrderLifecycleRecord(BaseModel):
    """
    Represents the recorded lifecycle evidence of a purchase order used for ground-truth label derivation.
    """
    po_id: str
    status: str                                  # ISSUED, PARTIALLY_DELIVERED, COMPLETED, CANCELLED
    po_delivery_status: Optional[str] = None     # PO_ON_TIME, PO_DELAYED, PO_SHORT_CLOSED
    ordered_quantity: Decimal
    cumulative_received_quantity: Decimal
    unfulfilled_quantity: Decimal = Decimal("0.0000")
    promised_delivery_date: datetime
    first_completed_at: Optional[datetime] = None
    was_short_closed: bool = False
    was_reopened: bool = False
    evaluation_timestamp: datetime = Field(default_factory=datetime.utcnow)


def derive_outcome_label(record: PurchaseOrderLifecycleRecord) -> OutcomeLabelEnum:
    """
    Deterministic rule-based derivation of outcome labels according to Revision 2.6 invariants.

    Rules:
    1. CANCELLED -> CANCELLED (Excluded from delivery risk training).
    2. Short-Closed POs:
       - If currently closed or closed with unfulfilled_quantity > 0 and never completed -> FAILED_FULFILLMENT.
       - A short-closed PO represents failed fulfillment, NOT a normal late delivery.
    3. Reopened POs:
       - Evaluated against the ORIGINAL promised delivery deadline.
       - If subsequently completed on/before original deadline -> ON_TIME_COMPLETED.
       - If subsequently completed after original deadline -> LATE_COMPLETED.
       - If still open and overdue -> FAILED_FULFILLMENT.
    4. Completed POs:
       - If first_completed_at <= promised_delivery_date -> ON_TIME_COMPLETED.
       - If first_completed_at > promised_delivery_date -> LATE_COMPLETED.
    5. Open POs (ISSUED / PARTIALLY_DELIVERED):
       - If evaluation_timestamp > promised_delivery_date -> FAILED_FULFILLMENT (Deadline breached).
       - If evaluation_timestamp <= promised_delivery_date -> OPEN_OR_CENSORED (Excluded from training).
    """
    status_upper = record.status.upper()
    if status_upper == "CANCELLED":
        return OutcomeLabelEnum.CANCELLED

    # Check for short-closure invariants
    is_short_closed = (
        record.po_delivery_status == "PO_SHORT_CLOSED" or
        record.was_short_closed or
        (status_upper == "COMPLETED" and record.first_completed_at is None and record.unfulfilled_quantity > 0)
    )

    if is_short_closed and not record.was_reopened:
        return OutcomeLabelEnum.FAILED_FULFILLMENT

    # Fully completed orders
    if record.first_completed_at is not None:
        if record.first_completed_at <= record.promised_delivery_date:
            return OutcomeLabelEnum.ON_TIME_COMPLETED
        else:
            return OutcomeLabelEnum.LATE_COMPLETED

    # Open in-flight orders without completion timestamp
    if status_upper in ("ISSUED", "PARTIALLY_DELIVERED"):
        if record.evaluation_timestamp > record.promised_delivery_date:
            # Overdue past contracted deadline
            return OutcomeLabelEnum.FAILED_FULFILLMENT
        else:
            # Not yet due (right-censored)
            return OutcomeLabelEnum.OPEN_OR_CENSORED

    # Default fallback for unverified / ambiguous records
    return OutcomeLabelEnum.OPEN_OR_CENSORED
