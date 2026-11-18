# PRD v1.1 change log and validation record

Date: 2026-11-18

## Source inspection

Available project files:
- `Tech4Bharath - 4(2).pdf`, inspected and verified as official PS 4.1 (“Small factories overpay for raw material and quote orders at a loss”).
- Original strategy/build document for MUNIM PS 4.1 (contains synthetic demo data).
- PRD v1.0.

Not available:
- Technical review with individual C1–C7, H1–H19, M1–M12 and L1–L7 texts.
- Separate proposed acceptance-criteria source.

The official PS 4.1 PDF has been verified against the strategy summary. No exact finding text is reconstructed from IDs alone.

## Changes from v1.0

| Area | Correction |
|---|---|
| Structure | Retained sections 1–27; added sections 28–29 and appendices C–D, for 29 numbered sections and four appendices A–D. |
| Source integrity | Verified official PS 4.1 against supplied PDF and separated official statements, product decisions and engineering recommendations. |
| Finding traceability | Added ID register for every C/H/M/L finding, all explicitly marked requiring source reconciliation. Topic map is separate from exact review traceability. |
| Negotiation | Defined policy bounds, negotiable terms, seller-visible allowlist, forbidden disclosure list, review and failure conditions. |
| Lineage | Added RFQ/quote/evaluation/BOM/cost-sheet/award foreign keys and consistency rules. |
| GST/freight | Separate material and freight tax fields, price basis and ITC; documented no freight-rate inference or bundled tax allocation. Accountant validation remains required. |
| Unknown values | Null/UNKNOWN never becomes zero; completeness blocks ranking; explicit zero has provenance. |
| Quantity rules | Defined increment ceiling, MOQ ceiling, wastage gross-up and separate rounding/MOQ surplus calculations. |
| Quote revisions | Defined states, immutable revisions, received_at vs ingested_at, freight basis, validity interval and decision-time semantics. |
| Workflow | Added persisted states, step states, two automatic retries, single manual retry and no retry of approval side effects. |
| APIs | Added read endpoints for materials, suppliers, owner policy and approvals; materialized OpenAPI 3.1 contract. |
| Delivery timing | Added production_lead_days and resolved procurement deadline behavior. |
| Extraction/audit | Added confidence bands, evidence requirement, manual fallback and model/prompt/schema versions, hashes, latency and error fields. |
| Demo values | Recalculated only what supplied inputs support; stated that no confirmed winner or award is justified without additional labeled synthetic clarifications. |
| Approval security | Snapshot hash, dependency invalidation, authorization recheck, and ApprovalService sole-writer transaction retained and made explicit. |
| Feasibility | Kept mid-Day 1 vertical slice and four owners; scope limited to single vertical and local Compose. |

## Validation checks performed

- Compared API path inventory with PRD route catalogue and schema document; route/entity relationships are described together.
- Reviewed FK chain: RFQ → supplier quote → immutable quote revision → persisted evaluation line → BOM/cost sheet → proposal/approval. Cross-table same-parent checks are explicitly assigned to transactional services/constraint trigger where ordinary FK is insufficient.
- Reviewed arithmetic examples: margin 20% and markup 25% for cost ₹10,000/sale ₹12,500; wastage 100/0.95 rounded to 106 kg; original suppliers evaluated with tax unknowns preserved.
- GST and freight stated separately; no inferred freight tax or creditability.
- Workflow transitions and retry caps documented.
- Approval decisions are role-gated, hash-bound, dependency-checked, atomic, and written only through ApprovalService.
- Acceptance coverage includes the named engineering topics and source gaps remain visible.
- Three-day scope retains only one vertical and one deployable; advanced auth/marketplace/split sourcing/cloud are excluded or deferred.

## Open validation limits

OpenAPI YAML syntax validation and execution of JUnit/API acceptance tests are implementation-time tasks; no application code or tests were run for this documentation deliverable. The official source and full technical review are required to close source validation. GST treatment requires accountant review. See PRD Appendix B.

