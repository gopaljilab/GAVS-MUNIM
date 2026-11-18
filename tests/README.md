# MUNIM smoke testing

1. Start with docker compose up --build.
2. Wait for http://localhost:8080/actuator/health to return {"status":"UP"}.
3. Open http://localhost:5173, use the default synthetic supplier name, and click Run procurement evaluation.
4. Confirm an RFQ, supplier quote with raw source text, completeness result, persisted evaluation, cost breakdown and SHA-256 input snapshot appear.
5. Reload and repeat; each run uses new idempotency keys and creates new synthetic records.

The project does not yet contain a browser automation test or PostgreSQL JUnit harness; the manual Compose smoke walkthrough is the current persistence check.
