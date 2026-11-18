-- Migration V2: Commercial Proposals, Approvals and Immutable Audit Trail

-- 1. Alter rfqs to support awarded quote link and status transition
ALTER TABLE rfqs DROP CONSTRAINT IF EXISTS rfqs_status_check;
ALTER TABLE rfqs ADD CONSTRAINT rfqs_status_check CHECK (status IN ('OPEN', 'EVALUATED', 'AWARDED'));
ALTER TABLE rfqs ADD COLUMN IF NOT EXISTS awarded_quote_id UUID REFERENCES supplier_quotes(id);

-- 2. Alter supplier_quotes to support awarded revision link
ALTER TABLE supplier_quotes ADD COLUMN IF NOT EXISTS awarded_revision_id UUID REFERENCES supplier_quote_revisions(id);

-- 3. Commercial Proposals table
CREATE TABLE IF NOT EXISTS commercial_proposals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type TEXT NOT NULL CHECK (type IN ('SUPPLIER_AWARD', 'CUSTOMER_QUOTE')),
  entity_type TEXT NOT NULL,
  entity_id UUID NOT NULL,
  evaluation_id UUID REFERENCES quote_evaluations(id),
  quote_revision_id UUID REFERENCES supplier_quote_revisions(id),
  cost_sheet_id UUID,
  requested_action TEXT NOT NULL,
  requesting_user TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'STALE')),
  deciding_user TEXT,
  decision_reason TEXT,
  decided_at TIMESTAMPTZ,
  snapshot_payload JSONB NOT NULL,
  snapshot_hash TEXT NOT NULL,
  policy_version INTEGER NOT NULL DEFAULT 1,
  invalidated_at TIMESTAMPTZ,
  invalidation_reason TEXT,
  version INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_proposals_status_created ON commercial_proposals(status, created_at);
CREATE INDEX IF NOT EXISTS idx_proposals_evaluation_id ON commercial_proposals(evaluation_id);
CREATE INDEX IF NOT EXISTS idx_proposals_quote_revision_id ON commercial_proposals(quote_revision_id);
CREATE INDEX IF NOT EXISTS idx_proposals_entity ON commercial_proposals(entity_type, entity_id);

-- 4. Append-only Approvals log table
CREATE TABLE IF NOT EXISTS approvals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  proposal_id UUID NOT NULL REFERENCES commercial_proposals(id),
  entity_type TEXT NOT NULL,
  entity_id UUID NOT NULL,
  requested_action TEXT NOT NULL,
  requesting_user TEXT NOT NULL,
  decision_status TEXT NOT NULL CHECK (decision_status IN ('APPROVED', 'REJECTED')),
  deciding_user TEXT NOT NULL,
  decision_reason TEXT,
  snapshot_payload JSONB NOT NULL,
  snapshot_hash TEXT NOT NULL,
  decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_approvals_proposal_id ON approvals(proposal_id);
CREATE INDEX IF NOT EXISTS idx_approvals_deciding_user ON approvals(deciding_user, decided_at);

-- 5. Immutable Audit Events table
CREATE TABLE IF NOT EXISTS audit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_id TEXT NOT NULL,
  actor_role TEXT NOT NULL,
  action TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id UUID NOT NULL,
  entity_version INTEGER,
  request_id TEXT,
  reason TEXT,
  policy_version INTEGER,
  before_hash TEXT,
  after_hash TEXT,
  metadata JSONB,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_events_entity ON audit_events(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_action ON audit_events(action);
CREATE INDEX IF NOT EXISTS idx_audit_events_occurred_at ON audit_events(occurred_at);
