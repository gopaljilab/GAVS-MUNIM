-- Migration V5: Purchase Orders, Receipts, Quality Inspections, and Financial Reconciliations

-- 1. Purchase Orders table (created upon RFQ award approval)
CREATE TABLE IF NOT EXISTS purchase_orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  po_number TEXT NOT NULL UNIQUE,
  rfq_id UUID NOT NULL REFERENCES rfqs(id),
  supplier_id UUID NOT NULL REFERENCES suppliers(id),
  quote_revision_id UUID NOT NULL REFERENCES supplier_quote_revisions(id),
  proposal_id UUID REFERENCES commercial_proposals(id),
  material_id UUID NOT NULL REFERENCES materials(id),
  ordered_quantity NUMERIC(14,4) NOT NULL CHECK (ordered_quantity > 0),
  unit TEXT NOT NULL,
  agreed_unit_price NUMERIC(12,2) NOT NULL CHECK (agreed_unit_price > 0),
  agreed_freight_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00 CHECK (agreed_freight_amount >= 0),
  freight_basis TEXT NOT NULL DEFAULT 'SEPARATE' CHECK (freight_basis IN ('SEPARATE', 'INCLUDED_IN_UNIT_PRICE')),
  material_tax_rate NUMERIC(5,2) NOT NULL DEFAULT 18.00 CHECK (material_tax_rate >= 0 AND material_tax_rate <= 100),
  material_itc_status TEXT NOT NULL CHECK (material_itc_status IN ('CREDITABLE', 'NON_CREDITABLE')),
  freight_tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0.00 CHECK (freight_tax_rate >= 0 AND freight_tax_rate <= 100),
  freight_itc_status TEXT NOT NULL CHECK (freight_itc_status IN ('CREDITABLE', 'NON_CREDITABLE')),
  quoted_economic_landed_cost NUMERIC(14,2) NOT NULL CHECK (quoted_economic_landed_cost >= 0),
  quoted_cash_payable NUMERIC(14,2) NOT NULL CHECK (quoted_cash_payable >= 0),
  promised_delivery_date TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('ISSUED', 'PARTIALLY_DELIVERED', 'COMPLETED', 'CANCELLED')),
  unfulfilled_quantity NUMERIC(14,4) NOT NULL DEFAULT 0.0000 CHECK (unfulfilled_quantity >= 0),
  closure_reason TEXT,
  closed_by TEXT,
  closed_at TIMESTAMPTZ,
  first_completed_at TIMESTAMPTZ,
  po_delivery_status TEXT CHECK (po_delivery_status IS NULL OR po_delivery_status IN ('PO_ON_TIME', 'PO_DELAYED', 'PO_SHORT_CLOSED')),
  notes TEXT,
  po_snapshot_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uq_purchase_orders_rfq UNIQUE (rfq_id),
  CONSTRAINT fk_po_quote_revision_rfq FOREIGN KEY (quote_revision_id, rfq_id) REFERENCES supplier_quote_revisions(id, rfq_id),
  CONSTRAINT chk_po_delivery_status_completed CHECK (
    (po_delivery_status = 'PO_SHORT_CLOSED' AND first_completed_at IS NULL) OR
    (po_delivery_status != 'PO_SHORT_CLOSED' OR po_delivery_status IS NULL)
  )
);

CREATE INDEX IF NOT EXISTS idx_purchase_orders_supplier ON purchase_orders(supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_rfq ON purchase_orders(rfq_id);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_status ON purchase_orders(status);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_delivery_status ON purchase_orders(po_delivery_status);

-- 2. Purchase Receipts table (Multi-stage delivery, quality, and invoice reconciliation)
CREATE TABLE IF NOT EXISTS purchase_receipts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE RESTRICT,
  supplier_id UUID NOT NULL REFERENCES suppliers(id),
  material_id UUID NOT NULL REFERENCES materials(id),
  receipt_sequence INTEGER NOT NULL DEFAULT 1 CHECK (receipt_sequence > 0),
  record_type TEXT NOT NULL DEFAULT 'ORIGINAL' CHECK (record_type IN ('ORIGINAL', 'CORRECTION_ADJUSTMENT')),
  superseded_receipt_id UUID REFERENCES purchase_receipts(id),
  adjustment_type TEXT CHECK (adjustment_type IS NULL OR adjustment_type IN ('PHYSICAL_RETURN', 'PHYSICAL_QUANTITY_CORRECTION', 'FINANCIAL_CREDIT_NOTE', 'FINANCIAL_DEBIT_NOTE')),
  correction_reason TEXT,

  -- Stage 1: Gate Delivery Intake (Applies to ORIGINAL records)
  stage_delivery TEXT NOT NULL DEFAULT 'PENDING' CHECK (stage_delivery IN ('PENDING', 'RECORDED')),
  actual_delivery_date TIMESTAMPTZ,
  received_quantity NUMERIC(14,4) CHECK (received_quantity IS NULL OR received_quantity > 0),
  delivery_challan_no TEXT,
  delivery_status TEXT CHECK (delivery_status IN ('EARLY', 'ON_TIME', 'DELAYED')),
  delay_days INTEGER,
  is_critical_delay BOOLEAN NOT NULL DEFAULT FALSE,

  -- Stage 2: Shopfloor Quality Inspection (Applies to ORIGINAL records)
  stage_quality TEXT NOT NULL DEFAULT 'PENDING' CHECK (stage_quality IN ('PENDING', 'INSPECTED', 'NOT_INSPECTED')),
  inspected_quantity NUMERIC(14,4) CHECK (inspected_quantity IS NULL OR inspected_quantity > 0),
  accepted_quantity NUMERIC(14,4) CHECK (accepted_quantity IS NULL OR accepted_quantity >= 0),
  rejected_quantity NUMERIC(14,4) CHECK (rejected_quantity IS NULL OR rejected_quantity >= 0),
  quality_acceptance_rate NUMERIC(5,2) CHECK (quality_acceptance_rate >= 0.00 AND quality_acceptance_rate <= 100.00),
  rejection_reason TEXT,
  inspector_id TEXT,
  inspector_notes TEXT,
  inspected_at TIMESTAMPTZ,

  -- Stage 3: Invoice & Landed Cost Reconciliation (Applies to ORIGINAL records; Tax-Exclusive Base)
  stage_invoice TEXT NOT NULL DEFAULT 'PENDING' CHECK (stage_invoice IN ('PENDING', 'RECONCILED')),
  invoice_number TEXT,
  invoice_date TIMESTAMPTZ,
  actual_material_base NUMERIC(14,2) CHECK (actual_material_base IS NULL OR actual_material_base >= 0),
  actual_material_gst NUMERIC(14,2) DEFAULT 0.00 CHECK (actual_material_gst >= 0),
  actual_freight_base NUMERIC(14,2) DEFAULT 0.00 CHECK (actual_freight_base >= 0),
  actual_freight_gst NUMERIC(14,2) DEFAULT 0.00 CHECK (actual_freight_gst >= 0),
  actual_other_expenses NUMERIC(14,2) DEFAULT 0.00 CHECK (actual_other_expenses >= 0),
  actual_other_expenses_gst NUMERIC(14,2) DEFAULT 0.00 CHECK (actual_other_expenses_gst >= 0),
  other_expenses_tax_rate NUMERIC(5,2) DEFAULT 18.00 CHECK (other_expenses_tax_rate >= 0 AND other_expenses_tax_rate <= 100),
  other_expenses_itc_status TEXT DEFAULT 'CREDITABLE' CHECK (other_expenses_itc_status IN ('CREDITABLE', 'NON_CREDITABLE')),
  other_expenses_description TEXT,
  actual_economic_landed_cost NUMERIC(14,2) CHECK (actual_economic_landed_cost IS NULL OR actual_economic_landed_cost >= 0),
  actual_cash_paid NUMERIC(14,2) CHECK (actual_cash_paid IS NULL OR actual_cash_paid >= 0),
  cost_variance_amount NUMERIC(14,2),
  cost_variance_percentage NUMERIC(6,2),
  currency TEXT NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),

  -- Dedicated Signed Adjustment Delta Fields (Populated ONLY when record_type = 'CORRECTION_ADJUSTMENT')
  delta_received_quantity NUMERIC(14,4) DEFAULT 0.0000,
  delta_inspected_quantity NUMERIC(14,4) DEFAULT 0.0000,
  delta_accepted_quantity NUMERIC(14,4) DEFAULT 0.0000,
  delta_rejected_quantity NUMERIC(14,4) DEFAULT 0.0000,
  delta_material_base NUMERIC(14,2) DEFAULT 0.00,
  delta_material_gst NUMERIC(14,2) DEFAULT 0.00,
  delta_freight_base NUMERIC(14,2) DEFAULT 0.00,
  delta_freight_gst NUMERIC(14,2) DEFAULT 0.00,
  delta_other_expenses NUMERIC(14,2) DEFAULT 0.00,
  delta_other_expenses_gst NUMERIC(14,2) DEFAULT 0.00,
  delta_economic_landed_cost NUMERIC(14,2) DEFAULT 0.00,
  delta_cash_paid NUMERIC(14,2) DEFAULT 0.00,
  credit_note_number TEXT,

  -- Stage 4: Owner Verification & Audit Seal
  verification_status TEXT NOT NULL DEFAULT 'UNVERIFIED' CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED')),
  verified_by TEXT,
  verified_at TIMESTAMPTZ,
  outcome_snapshot_hash TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Record Type Segregation Constraints
  CONSTRAINT chk_receipt_record_type CHECK (
    (record_type = 'ORIGINAL' AND superseded_receipt_id IS NULL AND adjustment_type IS NULL AND delta_received_quantity = 0.0000 AND delta_inspected_quantity = 0.0000 AND delta_accepted_quantity = 0.0000 AND delta_rejected_quantity = 0.0000 AND delta_material_base = 0.00 AND delta_material_gst = 0.00 AND delta_freight_base = 0.00 AND delta_freight_gst = 0.00 AND delta_other_expenses = 0.00 AND delta_other_expenses_gst = 0.00 AND delta_economic_landed_cost = 0.00 AND delta_cash_paid = 0.00) OR
    (record_type = 'CORRECTION_ADJUSTMENT' AND superseded_receipt_id IS NOT NULL AND adjustment_type IS NOT NULL AND correction_reason IS NOT NULL AND length(trim(correction_reason)) >= 10)
  ),
  -- Invariant for Financial-Only Adjustments
  CONSTRAINT chk_financial_only_adjustment_deltas CHECK (
    adjustment_type NOT IN ('FINANCIAL_CREDIT_NOTE', 'FINANCIAL_DEBIT_NOTE') OR
    (delta_received_quantity = 0.0000 AND delta_inspected_quantity = 0.0000 AND delta_accepted_quantity = 0.0000 AND delta_rejected_quantity = 0.0000)
  ),
  -- Quality constraints for ORIGINAL records
  CONSTRAINT chk_receipt_inspected_lte_received CHECK (
    record_type = 'CORRECTION_ADJUSTMENT' OR inspected_quantity IS NULL OR received_quantity IS NULL OR inspected_quantity <= received_quantity
  ),
  CONSTRAINT chk_receipt_accepted_lte_inspected CHECK (
    record_type = 'CORRECTION_ADJUSTMENT' OR accepted_quantity IS NULL OR inspected_quantity IS NULL OR (accepted_quantity >= 0 AND accepted_quantity <= inspected_quantity)
  ),
  CONSTRAINT chk_receipt_rejected_lte_inspected CHECK (
    record_type = 'CORRECTION_ADJUSTMENT' OR rejected_quantity IS NULL OR inspected_quantity IS NULL OR (rejected_quantity >= 0 AND rejected_quantity <= inspected_quantity)
  ),
  CONSTRAINT chk_receipt_inspection_sum CHECK (
    record_type = 'CORRECTION_ADJUSTMENT' OR inspected_quantity IS NULL OR (accepted_quantity + rejected_quantity = inspected_quantity)
  ),
  CONSTRAINT chk_receipt_rejection_reason CHECK (
    record_type = 'CORRECTION_ADJUSTMENT' OR (rejected_quantity IS NULL OR rejected_quantity = 0) OR (rejected_quantity > 0 AND rejection_reason IS NOT NULL AND length(trim(rejection_reason)) > 0)
  )
);

CREATE INDEX IF NOT EXISTS idx_purchase_receipts_po ON purchase_receipts(purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_purchase_receipts_supplier ON purchase_receipts(supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchase_receipts_material ON purchase_receipts(material_id);
CREATE INDEX IF NOT EXISTS idx_purchase_receipts_verified ON purchase_receipts(verification_status);
CREATE INDEX IF NOT EXISTS idx_purchase_receipts_delivery_status ON purchase_receipts(delivery_status);
