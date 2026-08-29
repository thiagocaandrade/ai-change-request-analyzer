from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_returns_200():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_analyze_happy_path():
    response = client.post(
        "/analyze", json={"request_id": "req-1", "text": "Alterar desconto VIP de 10% para 15%"}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["request_id"] == "req-1"
    assert body["status"] == "completed"
    assert body["result"]["processed_text"] == "Alterar desconto VIP de 10% para 15%"


def test_analyze_rejects_empty_text():
    response = client.post("/analyze", json={"request_id": "req-2", "text": ""})
    assert response.status_code == 400
    body = response.json()
    assert body["error"] == "invalid_request"


def test_analyze_rejects_missing_fields():
    response = client.post("/analyze", json={"text": "sem request_id"})
    assert response.status_code == 400
