-- Migration V4: Profitability Intelligence, Customer Enquiries, BOMs, Costing Sheets, and Customer Quotations

-- 1. Customer Enquiries
CREATE TABLE IF NOT EXISTS customer_enquiries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_name TEXT NOT NULL,
  customer_reference TEXT,
  contact_email TEXT,
  contact_phone TEXT,
  required_delivery_date TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'COSTED', 'QUOTED', 'WON', 'LOST')),
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_customer_enquiries_status ON customer_enquiries(status);
CREATE INDEX IF NOT EXISTS idx_customer_enquiries_delivery ON customer_enquiries(required_delivery_date);

-- 2. Bill of Materials (BOMs)
CREATE TABLE IF NOT EXISTS boms (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  enquiry_id UUID NOT NULL REFERENCES customer_enquiries(id) ON DELETE CASCADE,
  bom_version INTEGER NOT NULL DEFAULT 1 CHECK (bom_version > 0),
  batch_quantity NUMERIC(14,4) NOT NULL CHECK (batch_quantity > 0),
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'FINALIZED', 'SUPERSEDED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (enquiry_id, bom_version)
);

CREATE INDEX IF NOT EXISTS idx_boms_enquiry ON boms(enquiry_id);

-- 3. BOM Line Items with Strict Sourcing Basis and Scrap Recovery Checks
CREATE TABLE IF NOT EXISTS bom_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  bom_id UUID NOT NULL REFERENCES boms(id) ON DELETE CASCADE,
  material_id UUID NOT NULL REFERENCES materials(id),
  part_name TEXT NOT NULL,
  net_quantity NUMERIC(14,4) NOT NULL CHECK (net_quantity > 0),
  unit TEXT NOT NULL,
  scrap_percentage NUMERIC(5,2) NOT NULL DEFAULT 0.00 CHECK (scrap_percentage >= 0.00 AND scrap_percentage < 100.00),
  scrap_recovery_supported BOOLEAN NOT NULL DEFAULT FALSE,
  scrap_recovery_price NUMERIC(12,2) CHECK (scrap_recovery_price >= 0.00),
  sourcing_basis TEXT NOT NULL CHECK (sourcing_basis IN ('AWARDED_QUOTE', 'ESTIMATE')),
  awarded_quote_revision_id UUID REFERENCES supplier_quote_revisions(id),
  estimated_unit_cost NUMERIC(12,2) CHECK (estimated_unit_cost > 0.00),
  estimate_rationale TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT bom_items_sourcing_check CHECK (
    (sourcing_basis = 'AWARDED_QUOTE' AND awarded_quote_revision_id IS NOT NULL AND estimated_unit_cost IS NULL AND estimate_rationale IS NULL) OR
    (sourcing_basis = 'ESTIMATE' AND awarded_quote_revision_id IS NULL AND estimated_unit_cost IS NOT NULL AND estimate_rationale IS NOT NULL AND length(trim(estimate_rationale)) > 0)
  ),
  CONSTRAINT bom_items_scrap_recovery_check CHECK (
    (scrap_recovery_supported = FALSE AND scrap_recovery_price IS NULL) OR
    (scrap_recovery_supported = TRUE AND scrap_recovery_price IS NOT NULL)
  )
);

CREATE INDEX IF NOT EXISTS idx_bom_items_bom_id ON bom_items(bom_id);
CREATE INDEX IF NOT EXISTS idx_bom_items_material_id ON bom_items(material_id);
CREATE INDEX IF NOT EXISTS idx_bom_items_quote_revision ON bom_items(awarded_quote_revision_id);

-- 4. Costing Sheets (Immutable snapshots of calculated production costs and margins)
CREATE TABLE IF NOT EXISTS costing_sheets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  enquiry_id UUID NOT NULL REFERENCES customer_enquiries(id),
  bom_id UUID NOT NULL REFERENCES boms(id),
  batch_quantity NUMERIC(14,4) NOT NULL CHECK (batch_quantity > 0),
  material_cost NUMERIC(12,2) NOT NULL CHECK (material_cost >= 0.00),
  conversion_cost NUMERIC(12,2) NOT NULL CHECK (conversion_cost >= 0.00),
  overhead_cost NUMERIC(12,2) NOT NULL CHECK (overhead_cost >= 0.00),
  packaging_cost NUMERIC(12,2) NOT NULL CHECK (packaging_cost >= 0.00),
  freight_cost NUMERIC(12,2) NOT NULL CHECK (freight_cost >= 0.00),
  total_cost NUMERIC(12,2) NOT NULL CHECK (total_cost >= 0.00),
  target_margin NUMERIC(5,2) CHECK (target_margin >= 0.00 AND target_margin < 100.00),
  target_price NUMERIC(12,2) CHECK (target_price > 0.00),
  quoted_price NUMERIC(12,2) CHECK (quoted_price > 0.00),
  realized_margin NUMERIC(5,2),
  margin_classification TEXT CHECK (margin_classification IN ('HEALTHY_MARGIN', 'ACCEPTABLE_MARGIN', 'BELOW_MINIMUM_MARGIN', 'LOSS_MAKING')),
  calculation_version TEXT NOT NULL DEFAULT '1.0',
  calculation_snapshot JSONB NOT NULL,
  snapshot_sha256 TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_costing_sheets_enquiry ON costing_sheets(enquiry_id);
CREATE INDEX IF NOT EXISTS idx_costing_sheets_bom ON costing_sheets(bom_id);
CREATE INDEX IF NOT EXISTS idx_costing_sheets_created_at ON costing_sheets(created_at);

-- 5. Customer Quotations
CREATE TABLE IF NOT EXISTS customer_quotations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  enquiry_id UUID NOT NULL REFERENCES customer_enquiries(id),
  costing_sheet_id UUID NOT NULL REFERENCES costing_sheets(id),
  selling_price NUMERIC(12,2) NOT NULL CHECK (selling_price > 0.00),
  output_gst_rate NUMERIC(5,2) NOT NULL DEFAULT 18.00 CHECK (output_gst_rate >= 0.00 AND output_gst_rate <= 100.00),
  customer_payable NUMERIC(12,2) NOT NULL CHECK (customer_payable > 0.00),
  valid_from TIMESTAMPTZ NOT NULL,
  valid_until TIMESTAMPTZ NOT NULL,
  payment_terms TEXT NOT NULL,
  margin_classification TEXT NOT NULL CHECK (margin_classification IN ('HEALTHY_MARGIN', 'ACCEPTABLE_MARGIN', 'BELOW_MINIMUM_MARGIN', 'LOSS_MAKING')),
  approval_status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (approval_status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'EXPIRED')),
  commercial_proposal_id UUID REFERENCES commercial_proposals(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT customer_quotations_validity_check CHECK (valid_until > valid_from)
);

CREATE INDEX IF NOT EXISTS idx_customer_quotations_enquiry ON customer_quotations(enquiry_id);
CREATE INDEX IF NOT EXISTS idx_customer_quotations_costing ON customer_quotations(costing_sheet_id);
CREATE INDEX IF NOT EXISTS idx_customer_quotations_status ON customer_quotations(approval_status);
CREATE INDEX IF NOT EXISTS idx_customer_quotations_validity ON customer_quotations(valid_from, valid_until);
