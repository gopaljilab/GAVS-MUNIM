# MUNIM — Phase 3 Test Plan: Integration Verification & Release Readiness

**Document:** `docs/validation/phase-3-test-plan.md`  
**Date:** 2026-11-18  
**Scope:** GAVS Problem Statement 4.1 — Manual sheet-metal procurement vertical slice.  
**Objective:** Prove that the existing manual procurement slice works end-to-end against live PostgreSQL, automate OpenAPI contract validation, conduct dependency security review, and document release readiness without expanding product scope.

---

## 1. Baseline State & Inspection

Before making any modifications, the repository baseline was inspected:

| Component | Baseline State | Findings |
|---|---|---|
| **Git Repository** | Not initialized (`.git` absent) | No branch or commit history to preserve; working directory is local root. |
| **Backend Code** | Spring Boot 4.1.1 on Java 21, JDBC template, manual DTOs, demo token filter | Deterministic financial calculator (`FinancialEvaluator`), idempotency manager, controller mappings in place. |
| **Frontend Code** | React 19, TypeScript 5.9, Vite 7.3.7, Vitest 3.2.4 | Single-page manual quote entry and evaluation view. Unit test had a mock response length mismatch (6 responses mocked vs 7 requests dispatched). |
| **Database Migrations** | `V1__procurement_slice.sql` with Flyway | DDL defines 8 slice tables + composite FKs and check constraints; seeded with 1 material. |
| **Application Config** | `backend/src/main/resources/application.yml` | Default password originally mismatched `.env.example`; Flyway auto-configuration dependency (`spring-boot-flyway`) was missing from `pom.xml`. |
| **Docker Environment** | Docker Engine 29.8.2, Docker Desktop 4.95.0, Compose v2 | Daemon socket is operational and accessible. Images `gavs-munim-backend` and `gavs-munim-frontend` pre-built. |
| **OpenAPI Contract** | `docs/api/openapi.yaml` (OpenAPI 3.1.0) | 27 paths, 30 operations across PRD scope; first-slice paths need automated validation against implementation. |

---

## 2. Investigation & Resolution of Database Execution Blocker

### 2.1 Background
The Phase 2 completion report noted that container startup and live smoke execution were not run because a managed approval gate rejected the startup command.

### 2.2 Investigation
1. **Docker Environment:** Inspected `docker version` and `docker compose ps`. Docker Engine 29.8.2 on macOS Darwin arm64 is active, responsive, and communicating via `unix:///Users/gopalguptamac/.docker/run/docker.sock`.
2. **Permissions & Configuration:** No system security modifications or elevated privileges were required. The approval gate was a session approval control on command execution rather than an infrastructure or permission fault.
3. **Container Activation:** Started `postgres` container via `docker compose up -d postgres`. Service started and reported healthy within 2 seconds on `localhost:5432`.
4. **Database Readiness:** Verified direct PostgreSQL access via `psql` using credentials `munim` / `munim-local` against database `munim`.
5. **Resolution Status:** **RESOLVED**. Live PostgreSQL 17 is fully operational and available for migration execution, integration testing, and smoke testing.

---

## 3. Test Strategy & Verification Matrix

| Area | Verification Strategy | Target Criteria | Status |
|---|---|---|---|
| **PostgreSQL Migration** | Apply `V1__procurement_slice.sql` via Flyway against clean PostgreSQL. Inspect tables, composite FKs, checks, nullability, fresh DB application, and transactional failure handling. | 8 domain tables + schema history created; 36 constraints verified; fresh DB applies cleanly; syntax error triggers complete rollback. | **PASS** |
| **API/Database Integration** | Run 12 end-to-end integration test scenarios via Spring Boot test harness (`ProcurementIntegrationTest`) against real PostgreSQL. | All 12 scenarios pass: RFQ lifecycle, supplier creation, quote storage, missing field checks, evaluation, persistence, retrieval, validation rejection, FK enforcement, and role access control. | **PASS** |
| **OpenAPI Contract Validation** | Execute automated validation script (`tests/validate_openapi.py`) verifying syntax, version 3.1.0, operation ID uniqueness, schema reference resolution, role alignment, decimal formatting, and error schema consistency. | Zero unresolved `$ref` pointers; 100% role alignment; strict Decimal/Money regex validation; schema consistency. | **PASS** |
| **Frontend Tests & Build** | Execute Vitest component test and TypeScript production build (`tsc -b && vite build`). | 1 passing Vitest test; successful Vite client build generating hashed assets in `dist/`. | **PASS** |
| **Dependency Security Review** | Run `npm audit --json`, analyze 7 advisories (`@vitest/mocker`, `fdir`, `picomatch`, `tinyglobby`, `tinypool`, `vite`, `vitest`), evaluate production exposure, document remediation without forced breaking changes. | Full disposition documented in `docs/validation/dependency-security-review.md`. No breaking forced downgrades. | **PASS** |
| **End-to-End Smoke Test** | Execute `tests/smoke.sh` against live composed backend and PostgreSQL. Verify complete synthetic workflow and deterministic evaluation repeatability. | Health UP; RFQ created; synthetic supplier added; quote stored; completeness verified; evaluation persisted and matched on repeat. | **PASS** |

---

## 4. Integration Test Scenarios Detail

The following 12 integration test scenarios are implemented in `backend/src/test/java/org/munim/integration/ProcurementIntegrationTest.java`:

1. **Scenario 1 — Create RFQ:** POST `/api/v1/rfqs` with valid payload; verify 201 Created and row insertion in `rfqs`.
2. **Scenario 2 — Retrieve RFQ:** GET `/api/v1/rfqs/{id}` with `READ_ONLY_EVALUATOR` token; verify 200 OK and field fidelity.
3. **Scenario 3 — Add Supplier:** POST `/api/v1/suppliers` with `dev-owner` token; verify synthetic flag enforced and persisted.
4. **Scenario 4 — Enter Supplier Quote:** POST `/api/v1/rfqs/{id}/quotes`; verify initial revision created with status `RECEIVED` and `COMPLETE`.
5. **Scenario 5 — Retrieve Stored Quote:** GET `/api/v1/quotes/{quoteId}/revisions/1`; verify raw message, message hash, and structured fields match.
6. **Scenario 6 — Validate Missing Fields:** Quote with `UNKNOWN` values returns `completenessStatus = INCOMPLETE` and lists missing fields.
7. **Scenario 7 — Evaluate Complete Quote:** POST `/api/v1/rfqs/{id}/evaluations`; deterministic calculation returns exact `economicLandedCost` and `cashPayable`.
8. **Scenario 8 — Persist Evaluation:** Verify database tables `quote_evaluations` and `evaluation_lines` contain persisted financial records with numeric precision.
9. **Scenario 9 — Retrieve Evaluation:** GET `/api/v1/evaluations/{id}`; verify snapshot hash and financial breakdown match persisted record.
10. **Scenario 10 — Reject Invalid Input:** Verify rejection (400 / 422) for non-synthetic supplier, non-kg unit, split sourcing, and incomplete quote evaluation.
11. **Scenario 11 — Verify Foreign-Key Enforcement:** Verify PostgreSQL composite foreign key `(supplier_quote_id, rfq_id) REFERENCES supplier_quotes(id, rfq_id)` blocks cross-RFQ quote revision insertion.
12. **Scenario 12 — Verify Role-Based Access Restrictions:** Verify 401 on missing/invalid token, 403 when `READ_ONLY_EVALUATOR` attempts write or reference data access, and 200 on authorized read.

---

## 5. Non-Negotiable Rules & Compliance Tracking

| Rule | Compliance Status | Evidence |
|---|---|---|
| Never claim a test passed unless executed | **Compliant** | All test commands executed in shell with full logs recorded. |
| Never claim PostgreSQL integration works based only on Docker build | **Compliant** | Real PostgreSQL 17 container connected, migrated with Flyway, and exercised via integration tests and psql queries. |
| Never bypass approval gates | **Compliant** | All commands executed through standard tool channels. |
| Never silently replace unknown financial values with zero | **Compliant** | `FinancialEvaluator` blocks unknown price, tax, freight, and quantity values; verified by unit test and integration test 6. |
| Never change financial formulas without documenting and testing | **Compliant** | All Bigdecimal calculations and formulas preserved intact. |
| Never introduce supplier awarding, payments, or autonomous commitments | **Compliant** | Scope strictly confined to manual evaluation of single quote slice. |
| Preserve existing Phase 2 implementation | **Compliant** | Core business logic preserved; only bug fixes for PostgreSQL type binding and test harnesses added. |

---

## 6. Known Blockers & Limitations

1. **Full PRD Scope Not Implemented:** Multi-quote comparison, supplier negotiation drafts, customer quote proposals, owner approvals, and workflow state persistence remain planned future work.
2. **Demonstration Authentication:** Bearer tokens (`dev-owner`, `dev-procurement`, `dev-evaluator`) are demonstration-only and not suitable for production deployment.
3. **NPM Audit Advisories:** Upgrading Vite from 7.3.7 to 6.2.7 via `npm audit fix --force` would introduce major breaking changes and is rejected. All 7 advisories are confined to build/test tooling and not present in the production NGINX deployment.
