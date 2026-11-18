# MUNIM — Frontend Dependency Security Review

**Document:** `docs/validation/dependency-security-review.md`  
**Date:** 2026-11-18  
**Scope:** Frontend npm dependencies (`frontend/package.json`, `frontend/package-lock.json`)  
**Context:** GAVS PS 4.1 MUNIM Procurement Application  
**Author:** Application Security Reviewer & Senior QA Engineer  

---

## 1. Executive Summary

A security audit was conducted on the frontend dependencies using `npm audit` and `npm audit --json`. The audit identified **7 vulnerability advisories** across the dependency tree:
- **1 Moderate** severity
- **4 High** severity
- **2 Critical** severity

### Production Impact Analysis
All 7 advisories reside exclusively in the **developer build toolchain and test runner** (`vite`, `vitest`, and their internal worker/matching dependencies). 

In production, the frontend is built ahead of time (`tsc -b && vite build`) into static assets (`index.html`, CSS, and bundled JavaScript) and served via an unprivileged **NGINX 1.31 Alpine container** (`frontend/Dockerfile`). None of the affected libraries (`vitest`, `tinypool`, `@vitest/mocker`, `vite`, `fdir`, `picomatch`, `tinyglobby`) are bundled into client-side artifacts or executed in the production runtime.

Therefore, the **production exploitability is NEGLIGIBLE / NOT EXPOSED**.

A blind `npm audit fix --force` was evaluated and **REJECTED**, as it would force a major version downgrade of Vite from 7.3.7 to 6.2.7, breaking configuration and build stability.

---

## 2. Advisory Breakdown Table

| # | Package Name | Severity | Direct / Transitive | Installed Version | Patched Version | Used in Production? | GHSA Advisory ID / CWE | Recommended Action |
|---|---|---|---|---|---|---|---|---|
| 1 | `@vitest/mocker` | Moderate | Transitive (via `vitest`) | `3.2.4` | `3.2.7` / `>=4.1.12` | **No** (Test mocking only) | GHSA-82fw-gwwq-j7x9 (CWE-22), GHSA-qxg6-89mg-rqv8 (CWE-94) | Defer to safe `vitest@3.2.7` patch update; no code change required. |
| 2 | `fdir` | High | Transitive (via `tinyglobby`, `vite`) | `6.4.4` | Dependents patch | **No** (Build-time filesystem crawler) | Transitive via `picomatch` ReDoS | Do not force downgrade; maintain current lockfile pending Vite 7.x ecosystem update. |
| 3 | `picomatch` | High | Transitive (via `vite`, `vitest`, `fdir`, `tinyglobby`) | `2.3.1` / `4.0.2` | `2.3.2+`, `4.0.8+` | **No** (Build-time globbing) | GHSA-fc5q-39xc-vjpf (CWE-1333, ReDoS) | Safe patch upgrade via package lock resolution when npm registry is accessible; reject `--force`. |
| 4 | `tinyglobby` | High | Transitive (via `vite`, `vitest`) | `0.2.12` | Dependents patch | **No** (Build-time glob matcher) | Transitive via `picomatch` / `fdir` | Await upstream dependency bump. |
| 5 | `tinypool` | Critical | Transitive (via `vitest`) | `1.0.2` | `>=2.1.2` (via `vitest@3.2.7`) | **No** (Test worker pool) | GHSA-5gmw-xhrv-c9v3 (CWE-1321), GHSA-85c8-ppgw-ccpr (CWE-94) | Update `vitest` to `3.2.7` when registry is accessible. Only active during test execution. |
| 6 | `vite` | High | Direct (`dependencies: 7.3.7`) | `7.3.7` | `6.2.7` (suggested downgrade) | **No** (Build bundler; compiled output is static) | Transitive via `fdir`, `picomatch`, `tinyglobby` | **DO NOT DOWNGRADE**. npm suggests downgrading to Vite 6.2.7; keep Vite 7.3.7. |
| 7 | `vitest` | Critical | Direct (`devDependencies: 3.2.4`) | `3.2.4` | `3.2.7` | **No** (Headless test runner) | GHSA-5xrq-8626-4rwp (CWE-22/862), GHSA-82fw-gwwq-j7x9 | Keep headless `vitest run`. GHSA-5xrq-8626-4rwp requires Vitest UI server (`vitest --ui`), which is unused. Bump to 3.2.7 in planned maintenance. |

---

## 3. Detailed Security Assessments

### Advisory 1: `@vitest/mocker` (Moderate)
- **Title:** Path Traversal / Arbitrary File Read & Code Injection via Unauthenticated Mock Registration
- **Advisories:** GHSA-82fw-gwwq-j7x9, GHSA-qxg6-89mg-rqv8
- **Attack Vector:** Network / UI Server. Requires attacker access to a running Vitest mock server or mocker redirect.
- **Exposure:** The project uses headless `vitest run` in CI/local runs. No mock server port is exposed externally.
- **Disposition:** ACCEPTED RISK FOR LOCAL DEV / BUILD. Target upgrade to Vitest 3.2.7.

### Advisory 2 & 4: `fdir` & `tinyglobby` (High)
- **Title:** Transitive ReDoS through `picomatch`
- **Attack Vector:** Malicious glob expressions with single-branch repeated extglobs leading to exponential regex evaluation.
- **Exposure:** File patterns evaluated by Vite/Vitest are fixed internal project files (`src/**/*.ts`, `src/**/*.tsx`). No untrusted user input enters glob patterns.
- **Disposition:** ACCEPTED RISK FOR LOCAL DEV / BUILD.

### Advisory 3: `picomatch` (High)
- **Title:** ReDoS through single-branch repeated extglobs
- **Advisory:** GHSA-fc5q-39xc-vjpf
- **Attack Vector:** High complexity regex matching on crafted input.
- **Exposure:** Build-time tool only. All glob paths are internal project files.
- **Disposition:** ACCEPTED RISK FOR LOCAL DEV / BUILD.

### Advisory 5: `tinypool` (Critical)
- **Title:** Prototype Pollution gadget in worker options / run() options to Remote Code Execution
- **Advisories:** GHSA-5gmw-xhrv-c9v3, GHSA-85c8-ppgw-ccpr
- **Attack Vector:** Prototype pollution via Object.prototype manipulation in untrusted options payloads passed to tinypool workers.
- **Exposure:** Tests run locally with trusted developer test suites (`src/ui.test.tsx`). No external input controls worker options.
- **Disposition:** ACCEPTED RISK FOR LOCAL DEV / BUILD. Resolved when `vitest` is bumped to `>=3.2.7`.

### Advisory 6: `vite` (High)
- **Title:** Transitive vulnerability via `fdir` / `picomatch`
- **Impact of Proposed npm fix:** `npm audit fix --force` would downgrade `vite` from major version `7` to `6.2.7`. This represents an uncontrolled backward incompatible change that breaks Rollup/Vite configuration.
- **Disposition:** REJECT FORCED DOWNGRADE. Retain Vite 7.3.7.

### Advisory 7: `vitest` (Critical)
- **Title:** Arbitrary file read and code execution when Vitest UI server is listening
- **Advisory:** GHSA-5xrq-8626-4rwp
- **Attack Vector:** Requires `vitest --ui` server running on an exposed network interface.
- **Exposure:** MUNIM scripts define `"test": "vitest run"` (headless single-run execution without web UI server). No UI server is ever started or bound to a port.
- **Disposition:** MITIGATED BY CONFIGURATION. Headless mode does not expose the UI endpoint.

---

## 4. Verification of Frontend Stability

Following inspection and test fixes:
1. `npm test` executed: **1 test passed (100% success)**.
2. `npm run build` executed: **TypeScript type check (`tsc -b`) and Vite production bundle generated cleanly (0 errors)**.
3. Production Docker image `gavs-munim-frontend:latest` builds and starts cleanly on NGINX 1.31 Alpine, serving HTTP 200 on port 5173.

---

## 5. Security Recommendations for Next Release

1. **Do not run `npm audit fix --force`** at any point.
2. When general registry access is available, execute a non-breaking devDependency bump:
   ```sh
   npm install --save-dev vitest@3.2.7
   ```
3. Maintain headless test execution (`vitest run`) and avoid committing scripts that start `vitest --ui` on unauthenticated public network interfaces.
4. Integrate automated lockfile scanning (e.g. Trivy or GitHub Dependabot) in the repository CI pipeline.
