# MUNIM implementation-readiness audit

**Date:** 2026-11-18  
**Scope:** PS 4.1 only; documentation contract audit and first manual procurement vertical slice.  
**Authority:** The repository's official PS 4.1 source PDF. No PS 4.2 requirements are used. Technical review findings and the separate acceptance-criteria document were not supplied; their exact contents are not claimed as reconciled.

## Initial repository state

At inspection, the workspace contained the PRD v1.0/v1.1 and related product, API, database, financial, traceability, change-log and strategy documents, plus the PS 4.1 PDF. It had no application source, migrations, package/build configuration, tests, Docker setup, environment template, or Git metadata. Therefore no current branch or Git status was available. The repository had no established framework versions to preserve. The installed Java/Node/Docker versions were environment facts, not project pins.

## Current readiness findings

| ID | Severity | File(s) | Exact issue / resolution | Owner |
|---|---|---|---|---|
| IRA-01 | ACCEPTED | PS 4.1 PDF, PRD v1.1 | Keep the product scoped to PS 4.1; distinguish sourced PS requirements from product choices and engineering recommendations. Review findings are unavailable and remain unverified. | Product |
| IRA-02 | ACCEPTED | `docs/api/openapi.yaml`, `backend/src/main/java/org/munim/security/DemoAuthFilter.java` | API declares roles; implemented demo routes enforce local OWNER/PROCUREMENT_OPERATOR/READ_ONLY_EVALUATOR roles. These bearer tokens are local demo credentials, not production authentication. Future roles/modules are contract-only. | Backend |
| IRA-03 | MEDIUM | OpenAPI, Java service | Persisted idempotency is implemented for supplier, RFQ, quote and evaluation creation. Other contract writes remain unimplemented. Same key and changed request is a conflict; response replay is stored in PostgreSQL. | Backend |
| IRA-04 | HIGH | `backend/src/main/resources/db/migration/V1__procurement_slice.sql`, schema design | The first migration covers the manual procurement slice only. Composite FKs now ensure current quote revision belongs to its quote, quote revision belongs to its RFQ, evaluation references a revision for that RFQ, and evaluation line references the revision used by its evaluation. Owner policies, BOM, cost sheets, proposals, approvals, workflow and audit tables remain planned. Migration execution against PostgreSQL is unverified because Compose startup was rejected by the managed approval gate. | Database |
| IRA-05 | MEDIUM | OpenAPI, Java DTOs, TypeScript API | Handwritten DTOs are used; no generated DTO parity or automated OpenAPI operation validation exists. YAML parses, and the contract has 27 paths / 30 operations. Runtime coverage is only the first slice. | Integration |
| IRA-06 | ACCEPTED | Financial rules, Java evaluator | Java BigDecimal handles inclusive/exclusive material and freight GST, ITC cash/economic distinction, MOQ/increment/wastage, and unknown treatment blocking. Included freight is intentionally unrankable until explicit tax allocation exists. Accountant validation is still required before real GST use. | Backend + Finance |
| IRA-07 | MEDIUM | Quote DTO, migration, service | Quote receipt time, raw message/hash, manually entered structured fields, revision state and validity timestamps persist. Current implementation supports a single initial revision and does not yet implement quote revision editing or extraction provenance. | Backend |
| IRA-08 | ACCEPTED | OpenAPI, idempotency service | Persisted commands require `Idempotency-Key` (8–128 chars); replay/conflict behavior exists. Optimistic locking is documented for future mutable commercial entities and is not used in this immutable first slice. | Backend |
| IRA-09 | HIGH | PRD, workflow docs, migration | Persisted agent workflow states/retry counters are documented but not implemented. No LLM workflow is used in the slice. | Workflow |
| IRA-10 | ACCEPTED | PRD, API, security | No award, approval or payment operation is enabled in the slice; no model writes commercial state. ApprovalService sole-writer behavior remains future implementation work and needs module-level authorization tests before those routes ship. | Backend + Security |
| IRA-11 | ACCEPTED | `backend/`, `frontend/`, Compose | Spring Boot/PostgreSQL and React/TypeScript/Vite foundations, migration, local auth, health endpoint, environment template, README, Dockerfiles and Compose are present. Both images have built. Container startup and live smoke run were not approved, so runtime readiness is not yet demonstrated. | Integration |
| IRA-12 | ACCEPTED | Maven and npm configuration | Java 21 target and exact frontend package versions are configured. Maven dependencies were downloaded into the local cache; Node image uses Debian slim because the host `node_modules` context had caused an Alpine Rollup parse failure. `.dockerignore` now excludes host dependencies/build artifacts. | Integration |
| IRA-13 | ACCEPTED | Financial tests | Twelve executable JUnit cases cover tax creditability, inclusive pricing, included freight block, wastage/increment, MOQ excess, unknown treatment, invalid increment, insufficiency, unsupported unit, MOQ feasibility and deterministic repeat. Actual result: 12 tests pass. | Backend |
| IRA-14 | HIGH | Acceptance criteria / test fixtures | No synthetic multilingual/adversarial quote dataset or extraction accuracy test is implemented. The slice is manual entry only and makes no extraction accuracy claim. | Product + QA |
| IRA-15 | OPEN | Technical review / acceptance criteria | Exact C/H/M/L review contents and the separate acceptance-criteria document were not provided. Their reconciliation is blocked on source availability; do not infer meaning from IDs. | Product |
| IRA-16 | HIGH | `tests/smoke.sh`, Docker Compose | Smoke script is prepared and shell syntax checked, but cannot be called passing until backend/PostgreSQL services start and the workflow runs. Compose startup was rejected by the managed approval gate. | QA |
| IRA-17 | HIGH | `frontend/package-lock.json` | `npm install` reported seven dependency advisories (1 moderate, 4 high, 2 critical). Run a full audit and review compatible fixes before deployment; do not apply blanket forced upgrades. | Frontend + Security |

## Cross-contract checks

- OpenAPI YAML parses; it contains 27 paths and 30 operations. It remains broader than the implemented API and must not be read as evidence that all operations exist.
- Implemented RFQ, supplier, quote and evaluation DTOs correspond to the first-slice columns and use string monetary values. Manual structured quote fields are JSONB in the initial migration; they are not nullable monetary columns defaulted to zero.
- The implemented RFQ resolves its procurement deadline from a direct procurement date or `customerRequiredBy - productionLeadDays`. Only kg is supported; other quote units block evaluation pending an approved conversion.
- Quote completeness now blocks unknown ITC, included freight without a tax allocation, unsupported units, and quantities insufficient for wastage/MOQ/increment. Invalid numeric values fail; they are not replaced with zero.
- Financial output keeps `economicLandedCost` distinct from `cashPayable`. GST remains split for material and freight.
- No automatic award, payment, supplier messaging, LLM calculation, or commercial commitment is implemented.
- Database integrity has been strengthened with composite foreign keys for the slice, but PostgreSQL has not executed the migration in this environment.

## Three-day / four-person feasibility

The achievable three-day deliverable is the manual RFQ → supplier → quote → completeness → deterministic evaluation → display slice, plus one migration and local Compose wiring. The full PRD (including extraction, multilingual adversarial evaluation, approvals, BOM/costing, agent retries, audit and proposal invalidation) is not a realistic three-day implementation for four people. Keep those as later work. Suggested split: backend/financial service; frontend/API integration; PostgreSQL/contracts; integration/QA, with one shared daily contract review.

## Release gate

Do not describe the slice as end-to-end verified until PostgreSQL migration, Spring API startup, authorization checks, idempotency replay and the smoke script have run against the composed services. Before enabling approval/award routes, implement the persisted approval snapshot and invalidation model and test that ApprovalService is the only commercial state writer.
