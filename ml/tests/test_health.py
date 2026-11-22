def test_health_endpoint(client):
    res = client.get("/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "UP"
    assert data["service"] == "munim-ml-service"
    assert data["version"] == "0.1.0"
    assert "timestamp" in data

def test_ready_endpoint(client):
    res = client.get("/ready")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "READY"
    assert data["serviceReady"] is True
    # Crucial requirement: Does not report model ready when no model is validated
    assert data["modelReady"] is False
    assert data["activeModelStatus"] == "NO_VALIDATED_MODEL"

def test_status_endpoint_with_auth(client, auth_headers):
    res = client.get("/api/v1/ml/status", headers=auth_headers)
    assert res.status_code == 200
    data = res.json()
    assert data["serviceVersion"] == "0.1.0"
    assert data["featureSchemaVersion"] == "1.0.0"
    assert data["modelAvailable"] is False
    assert data["activeModelVersion"] is None
    assert data["trainingStatus"] == "IDLE"
    assert data["predictionsEnabled"] is False
    assert data["status"] == "NO_VALIDATED_MODEL"
    assert "Predictions disabled until authentic pilot outcome evidence" in data["statusMessage"]
    assert data["eligibleHistoricalOutcomesCount"] == 0
