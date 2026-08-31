"""Testes do cliente HTTP do contrato /api/agent/** (timeouts por etapa)."""

import httpx

from tools.client import AgentClient


def test_generate_test_plan_uses_dedicated_qa_timeout():
    captured = {}

    def handler(request):
        captured["read_timeout"] = request.extensions["timeout"]["read"]
        captured["path"] = request.url.path
        return httpx.Response(200, json={"recommendations": [], "qa": None, "degraded": False})

    client = AgentClient(
        base_url="http://app:8080",
        timeout=10.0,
        qa_timeout=120.0,
        transport=httpx.MockTransport(handler),
    )
    client.generate_test_plan({"changeText": "x"}, "trace-1")

    assert captured["path"] == "/api/agent/generate-test-plan"
    assert captured["read_timeout"] == 120.0


def test_other_endpoints_keep_default_timeout():
    captured = {}

    def handler(request):
        captured["read_timeout"] = request.extensions["timeout"]["read"]
        return httpx.Response(200, json={"category": "general", "degraded": False})

    client = AgentClient(
        base_url="http://app:8080",
        timeout=10.0,
        qa_timeout=120.0,
        transport=httpx.MockTransport(handler),
    )
    client.classify("x", "trace-2")

    assert captured["read_timeout"] == 10.0
