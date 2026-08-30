import structlog
from fake_client import happy_client
from fastapi.testclient import TestClient

import app.main as main_module
from app.main import app
from graph.builder import build_graph

client = TestClient(app)

CAPTURE_PROCESSORS = [
    structlog.contextvars.merge_contextvars,
    structlog.processors.add_log_level,
]


def test_logs_carry_trace_id_from_header(monkeypatch):
    monkeypatch.setattr(main_module, "graph", build_graph(happy_client()))
    with structlog.testing.capture_logs(processors=CAPTURE_PROCESSORS) as captured:
        response = client.post(
            "/analyze",
            headers={"X-Trace-Id": "trace-abc"},
            json={"request_id": "req-1", "text": "Alterar desconto VIP"},
        )
    assert response.status_code == 200
    assert len(captured) >= 2
    assert all(entry.get("trace_id") == "trace-abc" for entry in captured)


def test_logs_generate_trace_id_when_header_missing(monkeypatch):
    monkeypatch.setattr(main_module, "graph", build_graph(happy_client()))
    with structlog.testing.capture_logs(processors=CAPTURE_PROCESSORS) as captured:
        response = client.post("/analyze", json={"request_id": "req-2", "text": "Alterar frete"})
    assert response.status_code == 200
    assert len(captured) >= 2
    assert all(entry.get("trace_id") for entry in captured)
