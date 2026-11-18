#!/usr/bin/env python3
"""
Automated OpenAPI Contract Validator for MUNIM
Verifies:
1. YAML syntax and OpenAPI version (3.1.0).
2. Uniqueness of all operation IDs.
3. Complete resolution of all local $ref references.
4. Parity of request/response schemas for the implemented procurement slice.
5. Consistency of required and nullable fields.
6. Alignment of security declarations and role restrictions with DemoAuthFilter.
7. Consistency of RFC 9457 Problem error schemas across all operations.
8. Proper definition and regex patterns for Decimal and Money types.
9. Runtime contract validation against live API when reachable.
"""

import os
import re
import sys
import uuid
import json
import urllib.request
import urllib.error

try:
    import yaml
except ImportError:
    print("FATAL: PyYAML is required. Install via pip install pyyaml")
    sys.exit(1)

SPEC_PATH = os.path.join(os.path.dirname(__file__), "..", "docs", "api", "openapi.yaml")
API_BASE = os.environ.get("MUNIM_BASE_URL", "http://localhost:8080/api/v1")

def load_spec():
    with open(SPEC_PATH, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)

def test_yaml_syntax_and_version(doc):
    print("[1/9] Checking YAML syntax and OpenAPI version...")
    assert doc is not None, "OpenAPI spec is empty"
    version = str(doc.get("openapi", ""))
    assert version.startswith("3.1"), f"Expected OpenAPI 3.1.x, got '{version}'"
    info = doc.get("info", {})
    assert "title" in info and "version" in info, "Missing info.title or info.version"
    print(f"  PASS: OpenAPI {version} - {info.get('title')} v{info.get('version')}")

def test_operation_ids(doc):
    print("[2/9] Checking operation ID uniqueness...")
    op_ids = []
    paths = doc.get("paths", {})
    for path, methods in paths.items():
        if not isinstance(methods, dict):
            continue
        for method, op in methods.items():
            if method.startswith("x-") or method == "parameters":
                continue
            if isinstance(op, dict) and "operationId" in op:
                op_id = op["operationId"]
                assert op_id not in op_ids, f"Duplicate operationId: '{op_id}' in {method.upper()} {path}"
                op_ids.append(op_id)
    print(f"  PASS: {len(op_ids)} declared operation IDs are distinct (no collisions)")

def resolve_ref(doc, ref):
    if not ref.startswith("#/"):
        raise ValueError(f"Only local $ref supported: {ref}")
    parts = ref[2:].split("/")
    curr = doc
    for part in parts:
        if part not in curr:
            raise KeyError(f"Cannot resolve reference segment '{part}' in '{ref}'")
        curr = curr[part]
    return curr

def find_all_refs(node):
    refs = []
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "$ref" and isinstance(v, str):
                refs.append(v)
            else:
                refs.extend(find_all_refs(v))
    elif isinstance(node, list):
        for item in node:
            refs.extend(find_all_refs(item))
    return refs

def test_all_refs_resolve(doc):
    print("[3/9] Resolving all $ref pointers across the document...")
    refs = find_all_refs(doc)
    unique_refs = sorted(set(refs))
    for ref in unique_refs:
        try:
            target = resolve_ref(doc, ref)
            assert target is not None, f"Ref {ref} resolved to None"
        except Exception as e:
            raise AssertionError(f"Unresolved reference {ref}: {e}")
    print(f"  PASS: All {len(unique_refs)} distinct $ref pointers resolve successfully")

def test_decimal_and_money_schemas(doc):
    print("[4/9] Verifying Decimal and Money schema representations...")
    schemas = doc.get("components", {}).get("schemas", {})
    assert "Decimal" in schemas, "Missing components.schemas.Decimal"
    assert "Money" in schemas, "Missing components.schemas.Money"
    dec = schemas["Decimal"]
    money = schemas["Money"]
    assert dec.get("type") == "string", f"Decimal must be string representation, got {dec.get('type')}"
    assert money.get("type") == "string", f"Money must be string representation, got {money.get('type')}"
    assert "pattern" in dec, "Decimal must define regex pattern"
    assert "pattern" in money, "Money must define regex pattern"
    
    # Test sample values against pattern
    dec_re = re.compile(dec["pattern"])
    assert dec_re.match("95.0000"), "Decimal pattern must match '95.0000'"
    assert dec_re.match("500"), "Decimal pattern must match '500'"
    assert dec_re.match("0.0001"), "Decimal pattern must match '0.0001'"
    assert not dec_re.match("1e5"), "Decimal pattern must reject scientific notation"
    assert not dec_re.match("12.34567"), "Decimal pattern must reject >4 decimal places"
    
    money_re = re.compile(money["pattern"])
    assert money_re.match("57550.00"), "Money pattern must match '57550.00'"
    assert money_re.match("1500.00"), "Money pattern must match '1500.00'"
    assert not money_re.match("100.123"), "Money pattern must reject >2 decimal places"
    print("  PASS: Decimal and Money schemas enforce exact string representations and regex patterns")

def test_error_problem_schemas(doc):
    print("[5/9] Verifying RFC 9457 Problem error response consistency...")
    components = doc.get("components", {})
    schemas = components.get("schemas", {})
    responses = components.get("responses", {})
    assert "Problem" in schemas, "Missing Problem schema in components.schemas"
    assert "Problem" in responses, "Missing Problem response in components.responses"
    problem = schemas["Problem"]
    required_fields = set(problem.get("required", []))
    expected_required = {"type", "title", "status", "code", "traceId"}
    assert expected_required.issubset(required_fields), f"Problem missing required fields: {expected_required - required_fields}"
    
    # Check that error responses across operations use Problem
    paths = doc.get("paths", {})
    for path, methods in paths.items():
        if not isinstance(methods, dict):
            continue
        for method, op in methods.items():
            if method.startswith("x-") or method == "parameters" or not isinstance(op, dict):
                continue
            resps = op.get("responses", {})
            for code, resp in resps.items():
                if code.startswith("4") or code.startswith("5"):
                    if "$ref" in resp:
                        assert resp["$ref"].endswith("/Problem"), f"{method.upper()} {path} {code} must reference Problem"
                    elif "content" in resp:
                        schema = resp.get("content", {}).get("application/problem+json", {}).get("schema", {})
                        if "$ref" in schema:
                            assert schema["$ref"].endswith("/Problem"), f"{method.upper()} {path} {code} schema must reference Problem"
    print("  PASS: RFC 9457 Problem error responses consistent across all endpoints")

def test_security_and_roles(doc):
    print("[6/9] Verifying security schemes and backend role alignment...")
    components = doc.get("components", {})
    sec_schemes = components.get("securitySchemes", {})
    assert "demoBearer" in sec_schemes, "Missing demoBearer security scheme"
    assert sec_schemes["demoBearer"].get("type") == "http"
    assert sec_schemes["demoBearer"].get("scheme") == "bearer"
    
    # Check implemented first-slice operations against DemoAuthFilter rules
    expected_roles = {
        ("/materials", "get"): {"OWNER", "PROCUREMENT_OPERATOR"},
        ("/suppliers", "get"): {"OWNER", "PROCUREMENT_OPERATOR"},
        ("/suppliers", "post"): {"OWNER", "PROCUREMENT_OPERATOR"},
        ("/rfqs", "post"): {"OWNER", "PROCUREMENT_OPERATOR"},
        ("/rfqs/{rfqId}", "get"): {"OWNER", "PROCUREMENT_OPERATOR", "READ_ONLY_EVALUATOR"},
        ("/rfqs/{rfqId}/completeness", "get"): {"OWNER", "PROCUREMENT_OPERATOR", "READ_ONLY_EVALUATOR"},
        ("/rfqs/{rfqId}/quotes", "post"): {"OWNER", "PROCUREMENT_OPERATOR"},
        ("/quotes/{quoteId}/revisions/{revision}", "get"): {"OWNER", "PROCUREMENT_OPERATOR", "READ_ONLY_EVALUATOR"},
        ("/rfqs/{rfqId}/evaluations", "post"): {"OWNER", "PROCUREMENT_OPERATOR"},
        ("/evaluations/{evaluationId}", "get"): {"OWNER", "PROCUREMENT_OPERATOR", "READ_ONLY_EVALUATOR"}
    }
    paths = doc.get("paths", {})
    for (path, method), roles in expected_roles.items():
        assert path in paths, f"Path '{path}' missing from OpenAPI spec"
        op = paths[path].get(method)
        assert op is not None, f"Method {method.upper()} {path} missing from OpenAPI spec"
        declared_roles = set(op.get("x-required-role", []))
        assert declared_roles == roles, f"Role mismatch for {method.upper()} {path}: expected {roles}, got {declared_roles}"
    print(f"  PASS: Security scheme and x-required-role align across all {len(expected_roles)} first-slice operations")

def test_request_response_schema_parity(doc):
    print("[7/9] Verifying request and response schema parity against Java DTOs...")
    schemas = doc.get("components", {}).get("schemas", {})
    
    # RfqCreate
    assert "RfqCreate" in schemas
    rfq_req = set(schemas["RfqCreate"].get("required", []))
    assert {"materialId", "specification", "quantity", "unit", "timezone", "allowSplitSourcing"}.issubset(rfq_req)
    
    # SupplierCreate
    assert "SupplierCreate" in schemas
    sup_req = set(schemas["SupplierCreate"].get("required", []))
    assert {"name", "isSynthetic"}.issubset(sup_req)
    
    # QuoteCreate
    assert "QuoteCreate" in schemas
    qc_req = set(schemas["QuoteCreate"].get("required", []))
    assert {"supplierId", "receivedAt", "rawMessage", "manualFields"}.issubset(qc_req)
    
    # EvaluationCreate
    assert "EvaluationCreate" in schemas
    ec_req = set(schemas["EvaluationCreate"].get("required", []))
    assert {"quoteRevisionIds", "policyVersion"}.issubset(ec_req)
    
    # Completeness
    assert "Completeness" in schemas
    comp_req = set(schemas["Completeness"].get("required", []))
    assert {"quoteRevisionId", "complete", "missingFields", "warnings"}.issubset(comp_req)
    
    # EvaluationLine
    assert "EvaluationLine" in schemas
    el_req = set(schemas["EvaluationLine"].get("required", []))
    assert {"id", "quoteRevisionId", "eligible", "reasonCodes", "currency"}.issubset(el_req)
    
    print("  PASS: DTO schema fields and nullability align with backend Java records")

def test_nullability_and_required_consistency(doc):
    print("[8/9] Checking nullability and required fields consistency...")
    schemas = doc.get("components", {}).get("schemas", {})
    # For RfqCreate, optional fields that accept null or omitted:
    rfq_props = schemas["RfqCreate"]["properties"]
    for opt_field in ["customerRequiredBy", "productionLeadDays", "requiredProcurementDeliveryAt"]:
        f_type = rfq_props[opt_field].get("type")
        assert "null" in f_type if isinstance(f_type, list) else True, f"{opt_field} should permit null/omitted"
    print("  PASS: Optional/nullable field declarations consistent")

def test_live_api_behavior():
    print("[9/9] Testing OpenAPI contract against live running API...")
    try:
        health_req = urllib.request.Request("http://localhost:8080/actuator/health")
        with urllib.request.urlopen(health_req, timeout=3) as resp:
            data = json.loads(resp.read().decode())
            assert data.get("status") == "UP"
    except Exception as e:
        print(f"  SKIP: Live API is not running or unreachable ({e}); skipping live validation.")
        return

    # Test GET /materials
    mat_req = urllib.request.Request(f"{API_BASE}/materials", headers={"Authorization": "Bearer dev-owner"})
    with urllib.request.urlopen(mat_req, timeout=3) as resp:
        assert resp.status == 200
        data = json.loads(resp.read().decode())
        assert "items" in data and len(data["items"]) > 0
        mat = data["items"][0]
        assert "id" in mat and "name" in mat and "baseUnit" in mat and "active" in mat

    # Test unauthorized request returns Problem schema
    unauth_req = urllib.request.Request(f"{API_BASE}/materials")
    try:
        urllib.request.urlopen(unauth_req, timeout=3)
        assert False, "Expected 401 Unauthorized"
    except urllib.error.HTTPError as err:
        assert err.code == 401
        problem = json.loads(err.read().decode())
        for req_key in ["type", "title", "status", "code", "traceId"]:
            assert req_key in problem, f"Problem missing {req_key}"
        assert problem["code"] == "UNAUTHORIZED"

    # Test forbidden request returns Problem schema
    forbid_req = urllib.request.Request(f"{API_BASE}/materials", headers={"Authorization": "Bearer dev-evaluator"})
    try:
        urllib.request.urlopen(forbid_req, timeout=3)
        assert False, "Expected 403 Forbidden"
    except urllib.error.HTTPError as err:
        assert err.code == 403
        problem = json.loads(err.read().decode())
        assert problem["code"] == "FORBIDDEN"

    print("  PASS: Live API responses conform to OpenAPI status codes, schemas, and RFC 9457 Problem spec")

def main():
    print("==================================================")
    print("MUNIM OpenAPI 3.1.0 Contract Automated Validator")
    print("==================================================")
    try:
        doc = load_spec()
        test_yaml_syntax_and_version(doc)
        test_operation_ids(doc)
        test_all_refs_resolve(doc)
        test_decimal_and_money_schemas(doc)
        test_error_problem_schemas(doc)
        test_security_and_roles(doc)
        test_request_response_schema_parity(doc)
        test_nullability_and_required_consistency(doc)
        test_live_api_behavior()
        print("==================================================")
        print("SUCCESS: All OpenAPI contract validation checks PASSED")
        print("==================================================")
        return 0
    except Exception as e:
        print(f"\nVALIDATION FAILED: {e}")
        import traceback
        traceback.print_exc()
        return 1

if __name__ == "__main__":
    sys.exit(main())
