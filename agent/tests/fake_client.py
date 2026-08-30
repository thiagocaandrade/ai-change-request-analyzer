"""Client fake para os testes dos nos e do grafo.

Cada metodo devolve o dict configurado; uma instancia de Exception
configurada e levantada no lugar da resposta (simula aplicacao indisponivel).
"""

from tools.client import AgentUnavailableError


class FakeAgentClient:
    def __init__(self, **responses):
        self.responses = responses
        self.calls = []

    def _call(self, name, *args):
        self.calls.append((name, args))
        response = self.responses.get(name, {})
        if isinstance(response, Exception):
            raise response
        return response

    def classify(self, text, trace_id=None):
        return self._call("classify", text, trace_id)

    def analyze_code(self, text, trace_id=None, request_id=None):
        return self._call("analyze_code", text, trace_id, request_id)

    def retrieve_knowledge(self, text, trace_id=None, request_id=None):
        return self._call("retrieve_knowledge", text, trace_id, request_id)

    def retrieve_history(self, text, trace_id=None, request_id=None):
        return self._call("retrieve_history", text, trace_id, request_id)

    def security_assessment(self, payload, trace_id=None):
        return self._call("security_assessment", payload, trace_id)

    def analyze_impact(self, payload, trace_id=None):
        return self._call("analyze_impact", payload, trace_id)

    def assess_risk(self, payload, trace_id=None):
        return self._call("assess_risk", payload, trace_id)

    def generate_test_plan(self, payload, trace_id=None):
        return self._call("generate_test_plan", payload, trace_id)


def happy_client():
    return FakeAgentClient(
        classify={"category": "business_rule", "notes": "regra de desconto", "degraded": False},
        security_assessment={"detected": False, "events": [], "degraded": False},
        analyze_code={
            "findings": [
                {
                    "area": "code",
                    "description": "evidencia real de codigo: desconto VIP",
                    "severity": "INFO",
                    "file": "knowledge/discount-policy.md",
                    "line": 5,
                }
            ],
            "degraded": False,
        },
        retrieve_knowledge={
            "documents": [
                {
                    "source": "discount-policy.md",
                    "documentId": "discount-policy",
                    "chunkId": "discount-policy-0",
                    "score": 0.93,
                    "content": "Clientes VIP recebem desconto de 10%",
                }
            ],
            "degraded": False,
        },
        retrieve_history={
            "findings": [{"requestId": "req-antiga", "summary": "semelhante a CR anterior"}],
            "degraded": False,
        },
        analyze_impact={
            "findings": [
                {
                    "component": "discount-service",
                    "description": "regra de desconto afetada",
                    "severity": "HIGH",
                }
            ],
            "degraded": False,
        },
        assess_risk={
            "level": "MEDIUM",
            "confidence": 0.5,
            "rationale": "risco medio com evidencia real",
            "degraded": False,
        },
        generate_test_plan={
            "recommendations": [
                {
                    "component": "unit",
                    "description": "cobrir regra de desconto VIP",
                    "priority": "HIGH",
                }
            ],
            "degraded": False,
        },
    )


def unavailable_client():
    error = AgentUnavailableError("aplicacao indisponivel apos 3 tentativas")
    return FakeAgentClient(
        classify=error,
        security_assessment=error,
        analyze_code=error,
        retrieve_knowledge=error,
        retrieve_history=error,
        analyze_impact=error,
        assess_risk=error,
        generate_test_plan=error,
    )
