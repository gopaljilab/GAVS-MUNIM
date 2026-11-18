-- Migration V3: AI Supplier Quote Intelligence, Extractions and Clarifications

-- 1. Alter supplier_quote_revisions state check to include EXTRACTED and add version column
ALTER TABLE supplier_quote_revisions DROP CONSTRAINT IF EXISTS supplier_quote_revisions_state_check;
ALTER TABLE supplier_quote_revisions ADD CONSTRAINT supplier_quote_revisions_state_check 
  CHECK (state IN ('DRAFT', 'RECEIVED', 'EXTRACTED', 'NEEDS_REVIEW', 'VERIFIED', 'SUPERSEDED', 'WITHDRAWN'));

ALTER TABLE supplier_quote_revisions ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- 2. Quote Extractions audit table
CREATE TABLE IF NOT EXISTS quote_extractions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  quote_revision_id UUID NOT NULL REFERENCES supplier_quote_revisions(id),
  provider TEXT NOT NULL,
  model_version TEXT NOT NULL,
  prompt_version TEXT NOT NULL,
  schema_version TEXT NOT NULL DEFAULT '1.0',
  raw_response TEXT,
  candidates JSONB NOT NULL,
  missing_fields JSONB NOT NULL DEFAULT '[]'::jsonb,
  ambiguities JSONB NOT NULL DEFAULT '[]'::jsonb,
  validation_status TEXT NOT NULL CHECK (validation_status IN ('VALID', 'FLAGGED', 'INVALID')),
  validation_errors JSONB NOT NULL DEFAULT '[]'::jsonb,
  duration_ms INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_quote_extractions_revision ON quote_extractions(quote_revision_id);
CREATE INDEX IF NOT EXISTS idx_quote_extractions_created_at ON quote_extractions(created_at);

-- Link revision to latest extraction
ALTER TABLE supplier_quote_revisions ADD COLUMN IF NOT EXISTS extraction_id UUID REFERENCES quote_extractions(id);

-- 3. Clarification Messages table
CREATE TABLE IF NOT EXISTS clarification_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  quote_revision_id UUID NOT NULL REFERENCES supplier_quote_revisions(id),
  missing_fields JSONB NOT NULL,
  draft_text TEXT NOT NULL,
  response_text TEXT,
  received_at TIMESTAMPTZ,
  simulated BOOLEAN NOT NULL DEFAULT true,
  status TEXT NOT NULL CHECK (status IN ('DRAFT', 'SENT', 'RESPONDED', 'CANCELLED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_clarifications_revision ON clarification_messages(quote_revision_id);
CREATE INDEX IF NOT EXISTS idx_clarifications_status ON clarification_messages(status);
