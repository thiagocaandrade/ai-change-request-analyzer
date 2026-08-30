from fake_client import FakeAgentClient, happy_client
from fastapi.testclient import TestClient

import app.main as main_module
from app.main import app
from graph.builder import build_graph

client = TestClient(app)


def patch_graph(monkeypatch, graph_client):
    monkeypatch.setattr(main_module, "graph", build_graph(graph_client))


def test_health_returns_200():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_analyze_happy_path(monkeypatch):
    patch_graph(monkeypatch, happy_client())
    response = client.post(
        "/analyze", json={"request_id": "req-1", "text": "Alterar desconto VIP de 10% para 15%"}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["request_id"] == "req-1"
    assert body["status"] == "completed"
    result = body["result"]
    assert result["processed_text"] == "Alterar desconto VIP de 10% para 15%"
    assert result["risk"] == "MEDIUM"
    assert result["confidence"] == 0.5
    assert result["rationale"]
    assert result["classification"]["category"] == "business_rule"
    assert result["test_plan"]
    assert result["approval"] == {"required": False, "status": None}
    assert "errors" not in result


def test_analyze_high_risk_pending_approval(monkeypatch):
    high = FakeAgentClient(
        **{
            **happy_client().responses,
            "assess_risk": {
                "level": "HIGH",
                "confidence": 0.9,
                "rationale": "regra financeira",
                "degraded": False,
            },
        }
    )
    patch_graph(monkeypatch, high)
    response = client.post(
        "/analyze", json={"request_id": "req-1", "text": "Alterar desconto VIP"}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "pending_approval"
    assert body["result"]["risk"] == "HIGH"
    assert body["result"]["approval"] == {"required": True, "status": "PENDING"}


def test_analyze_failed_after_retries(monkeypatch):
    invalid = FakeAgentClient(
        **{
            **happy_client().responses,
            "assess_risk": {
                "level": "MEDIUM",
                "confidence": 1.5,
                "rationale": "invalida",
                "degraded": False,
            },
        }
    )
    patch_graph(monkeypatch, invalid)
    response = client.post(
        "/analyze", json={"request_id": "req-2", "text": "Alterar frete"}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "failed"
    assert body["result"]["errors"]


def test_analyze_rejects_empty_text():
    response = client.post("/analyze", json={"request_id": "req-2", "text": ""})
    assert response.status_code == 400
    body = response.json()
    assert body["error"] == "invalid_request"


def test_analyze_rejects_missing_fields():
    response = client.post("/analyze", json={"text": "sem request_id"})
    assert response.status_code == 400
