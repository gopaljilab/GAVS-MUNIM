# MUNIM PRD v1.1 traceability matrix

## Source status

The official PS 4.1 PDF `Tech4Bharath - 4(2).pdf` was inspected and matches MUNIM's target problem statement. The technical review texts and original proposed acceptance criteria are absent. Official source verification is **COMPLETE**; exact C/H/M/L finding reconciliation is **OPEN**. Rows below map only the concerns explicitly named in the user's v1.1 correction request and do not claim to be contents of unseen findings.

## Review concern to resolution

| Concern supplied in correction request | PRD resolution | Companion | Acceptance / check |
|---|---|---|---|
| Negotiation limits and information leakage | FR-09, §§11,19; bounded rates/terms, seller allowlist, disclosure denylist | OpenAPI owner policy; financial rules | FR-09, AC 13 |
| RFQ-to-award-to-cost lineage | §§16–17, schema lineage contract | database/schema-design.md | FK and lineage checks |
| GST creditability and price basis | §12.2; distinct material/freight basis and ITC | financial-rules.md | AC 4, 7 |
| Unknown values/completeness | FR-05, §§12,18 | OpenAPI missing fields; schema completeness status | AC 3–4 |
| Snapshot invalidation | FR-10/13/14, §§15,19 | database/schema-design.md | AC 11–12 |
| Untrusted messages/extraction verification | FR-03/04, §§11,19 | OpenAPI extraction schema | AC 5, 13–14 |
| Mid-Day 1 integration | §21, §22 | change log | AC 17 |
| Endpoints/OpenAPI | §17 | docs/api/openapi.yaml | Route/schema inventory |
| Persisted evaluations | FR-07/08, §16 | schema + OpenAPI | AC 8 |
| Revisions/provenance | FR-02/04, §16 | schema + OpenAPI | AC 5 |
| Relative dates/fixed clock | §11, §16 | schema reference_at/timezone | AC 1 |
| MOQ/partial/excess | §12.3 | financial-rules.md | AC 16 |
| Wastage | §12.4 | financial-rules.md | AC 16 |
| Conversion | §12.5 | schema unit_conversions | AC 16 |
| Margin/markup | §12.7 | financial-rules.md | AC 10 |
| Precision/rounding | §12.1 | financial-rules.md | AC 7,16 |
| Workflow persistence/retries | §11 | schema agent_runs/steps | AC 14,16 |
| Low-confidence extraction | §11 confidence bands, FR-03 | OpenAPI FieldValue | AC 5,14 |
| Owner overrides/authorization | FR-04/14, §§15,19 | schema cost_input_sources | AC 12 |
| Demo reliability | §§10,20–23,27 | change log | AC 15–17 |
| Official PS 4.1 source verification | §§1,3,28, Appendix B | — | VERIFIED against supplied Tech4Bharath - 4(2).pdf |

## Individual technical-review finding register

The user supplied only ranges, not finding titles or descriptions. Every ID is retained individually and must be linked to the exact finding source before it can be labeled resolved.

| ID | Status | Evidence needed |
|---|---|---|
| C1 | Requires source reconciliation | Original C1 text and severity rationale |
| C2 | Requires source reconciliation | Original C2 text and severity rationale |
| C3 | Requires source reconciliation | Original C3 text and severity rationale |
| C4 | Requires source reconciliation | Original C4 text and severity rationale |
| C5 | Requires source reconciliation | Original C5 text and severity rationale |
| C6 | Requires source reconciliation | Original C6 text and severity rationale |
| C7 | Requires source reconciliation | Original C7 text and severity rationale |
| H1 | Requires source reconciliation | Original H1 text |
| H2 | Requires source reconciliation | Original H2 text |
| H3 | Requires source reconciliation | Original H3 text |
| H4 | Requires source reconciliation | Original H4 text |
| H5 | Requires source reconciliation | Original H5 text |
| H6 | Requires source reconciliation | Original H6 text |
| H7 | Requires source reconciliation | Original H7 text |
| H8 | Requires source reconciliation | Original H8 text |
| H9 | Requires source reconciliation | Original H9 text |
| H10 | Requires source reconciliation | Original H10 text |
| H11 | Requires source reconciliation | Original H11 text |
| H12 | Requires source reconciliation | Original H12 text |
| H13 | Requires source reconciliation | Original H13 text |
| H14 | Requires source reconciliation | Original H14 text |
| H15 | Requires source reconciliation | Original H15 text |
| H16 | Requires source reconciliation | Original H16 text |
| H17 | Requires source reconciliation | Original H17 text |
| H18 | Requires source reconciliation | Original H18 text |
| H19 | Requires source reconciliation | Original H19 text |
| M1 | Requires source reconciliation | Original M1 text |
| M2 | Requires source reconciliation | Original M2 text |
| M3 | Requires source reconciliation | Original M3 text |
| M4 | Requires source reconciliation | Original M4 text |
| M5 | Requires source reconciliation | Original M5 text |
| M6 | Requires source reconciliation | Original M6 text |
| M7 | Requires source reconciliation | Original M7 text |
| M8 | Requires source reconciliation | Original M8 text |
| M9 | Requires source reconciliation | Original M9 text |
| M10 | Requires source reconciliation | Original M10 text |
| M11 | Requires source reconciliation | Original M11 text |
| M12 | Requires source reconciliation | Original M12 text |
| L1 | Requires source reconciliation | Original L1 text |
| L2 | Requires source reconciliation | Original L2 text |
| L3 | Requires source reconciliation | Original L3 text |
| L4 | Requires source reconciliation | Original L4 text |
| L5 | Requires source reconciliation | Original L5 text |
| L6 | Requires source reconciliation | Original L6 text |
| L7 | Requires source reconciliation | Original L7 text |

