# MUNIM PostgreSQL schema design

Version 1.1. This is the logical relational contract for the MVP. `backend/src/main/resources/db/migration/V1__procurement_slice.sql` materializes the initial procurement slice; the remaining tables below are planned and are not yet migrated. UUIDs are primary keys. Timestamps use TIMESTAMPTZ. Financial arithmetic uses Java BigDecimal; rates/quantities NUMERIC(14,4), money NUMERIC(14,2), percentages NUMERIC(8,4). JSONB snapshots are immutable audit copies, not substitutes for relational lineage.

## Tables and relationships

| Table | Keys and principal fields |
|---|---|
| materials | PK id; name, grade, specification, base_unit, active |
| suppliers | PK id; name, contact_name, phone, location, is_synthetic, active |
| unit_conversions | PK id; FK material_id; from_unit, to_unit, factor NUMERIC(14,8), source, effective_from/to, verified_by |
| owner_policies | PK id; unique version; effective_at; min_margin_percent; max_purchase_budget; min_unit_rate/max_unit_rate; max_discount_percent; max_payment_days; allowed_negotiable_terms, seller_visible_fields, forbidden_disclosure_fields JSONB; created_by |
| rfqs | PK id; FK material_id; specification; required_quantity/unit; production_lead_days; customer_required_by; required_procurement_delivery_at; timezone; reference_at; allow_split_sourcing=false; status; awarded_quote_id nullable FK supplier_quotes; version |
| supplier_quotes | PK id; FK rfq_id, supplier_id; current_revision_id FK revisions; awarded_revision_id FK revisions nullable; status |
| supplier_quote_revisions | PK id; FK supplier_quote_id; redundant rfq_id constrained with composite FK to the owning quote for traceability; revision_no unique per quote; state; received_at; ingested_at; raw_message/source_hash; price_basis; unit_price; quoted_quantity/unit; material_gst_rate/basis/itc_status; freight_amount/basis/gst_rate/gst_basis/itc_status; other_charges JSONB; MOQ; purchase_increment; wastage_percent; quote_quantity_basis; payment_terms_days; promised_delivery_at; valid_from/valid_until; field_data JSONB; extraction/completeness status; version; created_by |
| clarification_messages | PK id; FK quote_revision_id; missing_fields JSONB; draft_text; response_text/received_at; simulated; status |
| quote_evaluations | PK id; FK rfq_id, owner_policy_id; calculation_version; reference_at/timezone; state; input_revision_ids JSONB; normalized_input_snapshot/result_snapshot; snapshot_hash; created_by |
| evaluation_lines | PK id; FK evaluation_id, quote_revision_id; eligible; reason_codes; normalized_quantities; material_base/material_gst/freight_base/freight_gst/other_charges/economic_landed_cost/cash_payable; currency; unique(evaluation_id,quote_revision_id) |
| customer_enquiries | PK id; product_description, customer_name nullable, quantity/unit, required_by, status |
| bom_lines | PK id; FK enquiry_id, material_id; FK source_evaluation_line_id/source_quote_revision_id nullable; usable_quantity/unit; wastage_percent; conversion_id nullable FK; estimated_cost; source_type; version |
| cost_sheets | PK id; FK enquiry_id; evaluation_id/evaluation_line_id nullable FKs; awarded_quote_id/awarded_quote_revision_id nullable FKs; price_basis; material/labour/overhead/freight/other costs nullable; completeness; total_cost nullable; version |
| cost_input_sources | PK id; FK cost_sheet_id; component; source_type; source_entity_id/revision_id; value; override_reason; actor_id; created_at |
| commercial_proposals | PK id; type; evaluation_id, quote_revision_id, cost_sheet_id nullable FKs; immutable snapshot JSONB; snapshot_hash; policy_version; status; invalidated_at/reason; version |
| approvals | PK id; FK proposal_id; actor_id; decision; reason; snapshot_hash; decided_at |
| agent_runs | PK id; workflow_type/reference_type/reference_id; state; reference_at/timezone; automatic_retry_count; max_automatic_retries=2; manual_retry_used; unique idempotency_key; version |
| agent_steps | PK id; FK agent_run_id; step_name/state/attempt_no; input_hash/output_hash; redacted input/output JSONB; provider/model/prompt/schema versions; confidence; latency_ms; error_code/detail; timestamps |
| audit_events | PK id; actor_id/role; action; entity_type/id/version; request_id/correlation_id; occurred_at; reason; policy_version; before_hash/after_hash; metadata JSONB |
| idempotency_records | PK (actor_role, operation, idempotency_key); request_sha256; response_body JSONB; state; created_at; retained through demo session |

## Required foreign keys and consistency constraints

1. rfqs.material_id → materials.id; supplier_quotes.rfq_id → rfqs.id; supplier_quotes.supplier_id → suppliers.id.
2. supplier_quote_revisions.supplier_quote_id → supplier_quotes.id and `(supplier_quote_id, rfq_id)` → supplier_quotes `(id, rfq_id)`. Current revision uses `(current_revision_id, id)` → revisions `(id, supplier_quote_id)`.
3. quote_evaluations `(quote_revision_id, rfq_id)` → supplier_quote_revisions `(id, rfq_id)`; evaluation_lines `(evaluation_id, quote_revision_id)` → quote_evaluations `(id, quote_revision_id)`. These composite keys prevent mixing an RFQ with another RFQ's quote revision or attaching an evaluation line to the wrong revision.
4. rfqs.awarded_quote_id → supplier_quotes.id; supplier_quotes.awarded_revision_id → supplier_quote_revisions.id. Award transaction verifies quote belongs to RFQ and revision belongs to quote.
5. bom_lines.source_evaluation_line_id → evaluation_lines.id; bom_lines.source_quote_revision_id → supplier_quote_revisions.id. When both populated, service verifies revision matches the evaluation line.
6. cost_sheets.evaluation_id → quote_evaluations.id; evaluation_line_id → evaluation_lines.id; awarded_quote_id → supplier_quotes.id; awarded_quote_revision_id → supplier_quote_revisions.id. Service validates consistent lineage. AWARDED_QUOTE requires award FKs; EVALUATED_QUOTE requires evaluation and line; OWNER_OVERRIDE requires cost_input_sources with actor and reason.
7. Proposal and approval FKs as listed. Agent steps reference run. Audit event references entity where possible.

## RFQ production timing and quote lifecycle

production_lead_days is owner-entered calendar days from material receipt to production completion. For customer deadline, required_procurement_delivery_at = customer_required_by - production_lead_days in RFQ timezone; direct procurement RFQ can enter procurement deadline. Store resolved deadline and inputs. Supplier eligibility uses stored deadline only.

Revision states: DRAFT, RECEIVED, EXTRACTED, NEEDS_REVIEW, VERIFIED, SUPERSEDED, WITHDRAWN. received_at is supplier offer receipt time, distinct from ingested_at. Editing creates a new revision; prior revision remains immutable and becomes SUPERSEDED. Absolute valid_from/valid_until are supplier-stated offset-aware values. Explicit relative duration resolves from received_at in the supplier's known timezone using calendar days, storing provenance; if timezone or receipt time is missing/ambiguous, validity is unknown. Unstated valid_from is set to received_at with explicit default provenance. Missing validity blocks award. At decision time t, valid iff valid_from <= t <= valid_until; evaluation stores t and result. No default validity duration is invented.

## Initial migration coverage

The initial SQL migration creates materials, suppliers, RFQs, supplier quotes/revisions, quote evaluations, evaluation lines, and idempotency records. In this slice, structured quote values are retained in a JSONB `fields` object rather than separate nullable monetary columns; missing values remain missing and are blocked during completeness/evaluation. It seeds one generic mild-steel material record (not a supplier offer). It does not yet implement owner policies, customer enquiries, BOM/cost sheets, approvals, or persisted agent workflow/audit tables; those remain future schema work. No award or approval endpoint is enabled by this migration.

## Constraints, indexes and transactions

- Positive quantity/rate/increment; 0 <= wastage < 1; valid_until >= valid_from; percentages within defined domain.
- RFQ must provide direct procurement deadline or customer deadline plus lead days; resolved procurement timestamp is always stored.
- Unique (supplier_quote_id, revision_no); one current revision per quote; unique evaluation line per evaluation/quote revision.
- Append-only quote revisions and audit events; no cascade delete of commercial records. Snapshot hashes are mandatory SHA-256.
- Index RFQs by status/deadline, quotes by RFQ/supplier, revisions by quote/revision, evaluations by RFQ/time, pending proposals by status/time, steps by run/attempt, audit by entity/time.
- Approval decision, state transition and audit event are one ApprovalService transaction. Award also sets RFQ/quote award links, invalidates competing pending proposals and emits audit. Any change to policy, quote revision, price/tax/quantity, deadline, conversion factor, evaluation or cost input stales dependent pending proposal with a reason.

