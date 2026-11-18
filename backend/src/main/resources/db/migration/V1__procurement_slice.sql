CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE materials (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  grade TEXT,
  specification TEXT NOT NULL,
  base_unit TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE suppliers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  location TEXT,
  is_synthetic BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_synthetic),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE rfqs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  material_id UUID NOT NULL REFERENCES materials(id),
  specification TEXT NOT NULL,
  quantity NUMERIC(14,4) NOT NULL CHECK (quantity > 0),
  unit TEXT NOT NULL,
  production_lead_days INTEGER CHECK (production_lead_days >= 0),
  customer_required_by TIMESTAMPTZ,
  required_procurement_delivery_at TIMESTAMPTZ NOT NULL,
  reference_at TIMESTAMPTZ NOT NULL,
  timezone TEXT NOT NULL,
  allow_split_sourcing BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT allow_split_sourcing),
  status TEXT NOT NULL CHECK (status IN ('OPEN','EVALUATED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE supplier_quotes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rfq_id UUID NOT NULL REFERENCES rfqs(id),
  supplier_id UUID NOT NULL REFERENCES suppliers(id),
  current_revision_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (rfq_id, supplier_id, id),
  UNIQUE (id, rfq_id)
);
CREATE TABLE supplier_quote_revisions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  supplier_quote_id UUID NOT NULL REFERENCES supplier_quotes(id),
  rfq_id UUID NOT NULL,
  revision_no INTEGER NOT NULL CHECK (revision_no > 0),
  state TEXT NOT NULL CHECK (state IN ('RECEIVED','NEEDS_REVIEW','VERIFIED','SUPERSEDED')),
  received_at TIMESTAMPTZ,
  raw_message TEXT NOT NULL,
  raw_message_sha256 TEXT NOT NULL,
  fields JSONB NOT NULL,
  completeness_status TEXT NOT NULL CHECK (completeness_status IN ('COMPLETE','INCOMPLETE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (supplier_quote_id, revision_no),
  UNIQUE (id, rfq_id),
  UNIQUE (id, supplier_quote_id),
  FOREIGN KEY (supplier_quote_id, rfq_id) REFERENCES supplier_quotes(id, rfq_id)
);
ALTER TABLE supplier_quotes ADD CONSTRAINT supplier_quotes_current_revision_fk
  FOREIGN KEY (current_revision_id, id) REFERENCES supplier_quote_revisions(id, supplier_quote_id);
CREATE TABLE quote_evaluations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rfq_id UUID NOT NULL REFERENCES rfqs(id),
  quote_revision_id UUID NOT NULL,
  reference_at TIMESTAMPTZ NOT NULL,
  calculation_version TEXT NOT NULL,
  input_snapshot JSONB NOT NULL,
  snapshot_sha256 TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('COMPLETE','INCOMPLETE')),
  result JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (id, quote_revision_id),
  FOREIGN KEY (quote_revision_id, rfq_id) REFERENCES supplier_quote_revisions(id, rfq_id)
);
CREATE TABLE evaluation_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  evaluation_id UUID NOT NULL,
  quote_revision_id UUID NOT NULL REFERENCES supplier_quote_revisions(id),
  material_base NUMERIC(14,2) NOT NULL,
  material_gst NUMERIC(14,2) NOT NULL,
  freight_base NUMERIC(14,2) NOT NULL,
  freight_gst NUMERIC(14,2) NOT NULL,
  other_charges NUMERIC(14,2) NOT NULL,
  economic_landed_cost NUMERIC(14,2) NOT NULL,
  cash_payable NUMERIC(14,2) NOT NULL,
  currency TEXT NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
  UNIQUE (evaluation_id, quote_revision_id),
  FOREIGN KEY (evaluation_id, quote_revision_id) REFERENCES quote_evaluations(id, quote_revision_id)
);
CREATE TABLE idempotency_records (
  actor_role TEXT NOT NULL,
  operation TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  request_sha256 TEXT NOT NULL,
  response_body JSONB,
  state TEXT NOT NULL CHECK (state IN ('IN_PROGRESS','COMPLETED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(actor_role,operation,idempotency_key)
);
INSERT INTO materials(name,grade,specification,base_unit)
VALUES ('Mild Steel Sheet','IS 2062','2 mm mild-steel sheet','kg');
