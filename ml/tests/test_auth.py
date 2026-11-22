def test_status_missing_auth_header(client):
    res = client.get("/api/v1/ml/status")
    assert res.status_code == 401
    assert "Missing required internal service authentication key" in res.json()["detail"]

def test_status_invalid_auth_key(client):
    res = client.get("/api/v1/ml/status", headers={"X-Internal-ML-Key": "wrong-secret-token"})
    assert res.status_code == 403
    assert "Forbidden" in res.json()["detail"]

def test_status_valid_auth_key(client, auth_headers):
    res = client.get("/api/v1/ml/status", headers=auth_headers)
    assert res.status_code == 200

def test_health_routes_bypass_auth(client):
    # Health and ready probes are accessible without internal key for container orchestrator
    assert client.get("/health").status_code == 200
    assert client.get("/ready").status_code == 200
