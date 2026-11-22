from datetime import datetime
from decimal import Decimal
from typing import Optional
from uuid import UUID
from pydantic import BaseModel, Field, field_validator

class SupplierDeliveryFeatureVector(BaseModel):
    """
    Versioned feature contract (v1.0.0) for Supplier Delivery Risk Prediction.
    Represents the exact feature snapshot available at the moment a Purchase Order is placed (T_predict).
    Enforces point-in-time safety: zero post-award fields permitted.
    Preserves nulls for missing historical data (Non-Zero Unknown Principle).
    """
    feature_schema_version: str = Field(default="1.0.0", description="Feature schema specification version")
    feature_extraction_timestamp: datetime = Field(description="Point-in-time timestamp when features were calculated")

    # Target PO Identifiers & Parameters
    target_po_id: UUID = Field(description="UUID of the purchase order being evaluated")
    supplier_id: UUID = Field(description="UUID of the supplier")
    material_id: UUID = Field(description="UUID of the material specified in the order")

    # Contract Commitments at T_predict
    ordered_quantity: Decimal = Field(gt=0, description="Contracted volume in base units (kg, sheets)")
    agreed_unit_price: Decimal = Field(gt=0, description="Contracted unit price in INR")
    agreed_freight_amount: Decimal = Field(ge=0, default=Decimal("0.00"), description="Contracted freight allocation")
    freight_basis: str = Field(pattern="^(SEPARATE|INCLUDED_IN_UNIT_PRICE)$", description="Freight arrangement")

    # Lead Time Dynamics at T_predict
    promised_lead_days: int = Field(ge=0, description="Supplier promised turnaround in days (promised_delivery_date - created_at)")
    factory_lead_days: Optional[int] = Field(default=None, description="Factory required delivery turnaround in days")
    lead_time_slack_days: Optional[int] = Field(default=None, description="Buffer between vendor promise and factory deadline")

    # Historical Point-in-Time Metrics (Computed strictly over POs with completion/closure < T_predict)
    # Missing values remain None (null) and MUST NOT be defaulted to 0.0
    hist_eligible_po_count: int = Field(ge=0, default=0, description="Count of prior completed/closed POs before T_predict")
    hist_ontime_po_count: int = Field(ge=0, default=0, description="Count of prior on-time POs before T_predict")
    hist_point_in_time_otd_rate: Optional[Decimal] = Field(
        default=None,
        ge=0,
        le=100,
        description="Historical Contract OTD % (null if eligible_count == 0)"
    )
    hist_avg_delay_days: Optional[Decimal] = Field(
        default=None,
        ge=0,
        description="Average delay in days on late prior orders (null if no late orders)"
    )
    hist_partial_delivery_rate: Optional[Decimal] = Field(
        default=None,
        ge=0,
        le=100,
        description="Percentage of prior completed orders requiring multiple deliveries"
    )
    hist_net_usable_yield: Optional[Decimal] = Field(
        default=None,
        ge=0,
        le=100,
        description="Post-inspection usable yield rate % on shopfloor (null if uninspected)"
    )
    hist_material_po_count: int = Field(
        ge=0,
        default=0,
        description="Prior PO count for this specific supplier-material pair"
    )
    has_material_evidence: bool = Field(
        default=False,
        description="True if supplier has prior verified completions for this specific material"
    )

    @field_validator("hist_point_in_time_otd_rate")
    @classmethod
    def validate_otd_null_when_zero_eligible(cls, v, info):
        # Non-Zero Unknown Principle: if 0 eligible orders exist, OTD rate must remain None
        eligible = info.data.get("hist_eligible_po_count", 0)
        if eligible == 0 and v is not None:
            raise ValueError("Historical OTD rate must be null (None) when hist_eligible_po_count is 0")
        return v
