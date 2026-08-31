"""Cliente HTTP do contrato interno /api/agent/** da aplicacao.

Timeout, retry limitado (2) e cabecalho X-Trace-Id em toda chamada.
Falha apos os retries vira AgentUnavailableError: os nos registram a falha
em errors com coleta vazia e o grafo segue degradado.
"""

import os
import time

import httpx
import structlog

DEFAULT_TIMEOUT_S = 10.0
DEFAULT_QA_TIMEOUT_S = 120.0
MAX_RETRIES = 2

logger = structlog.get_logger()


class AgentUnavailableError(RuntimeError):
    """Falha de comunicacao com a aplicacao apos os retries."""


class AgentClient:
    """Faz chamadas tipadas aos endpoints /api/agent/** da aplicacao Spring."""

    def __init__(
        self,
        base_url: str | None = None,
        timeout: float = DEFAULT_TIMEOUT_S,
        qa_timeout: float | None = None,
        max_retries: int = MAX_RETRIES,
        transport=None,
    ):
        self._base_url = (
            base_url or os.getenv("APP_URL", "http://localhost:8080")
        ).rstrip("/")
        self._timeout = timeout
        self._qa_timeout = (
            qa_timeout
            if qa_timeout is not None
            else float(os.getenv("AGENT_QA_TIMEOUT_S", str(DEFAULT_QA_TIMEOUT_S)))
        )
        self._max_retries = max_retries
        self._transport = transport

    def _post(
        self, path: str, payload: dict, trace_id: str | None, timeout: float | None = None
    ) -> dict:
        headers = {"X-Trace-Id": trace_id or ""}
        client = httpx.Client(timeout=timeout or self._timeout, transport=self._transport)
        last_error: Exception | None = None
        try:
            for attempt in range(self._max_retries + 1):
                try:
                    response = client.post(
                        f"{self._base_url}{path}", json=payload, headers=headers
                    )
                    response.raise_for_status()
                    return response.json()
                except httpx.HTTPError as exc:
                    last_error = exc
                    logger.warning(
                        "agent_app_call_failed",
                        path=path,
                        attempt=attempt + 1,
                        max_retries=self._max_retries,
                        error=exc.__class__.__name__,
                        trace_id=trace_id,
                    )
                    if attempt < self._max_retries:
                        time.sleep(0.5 * (attempt + 1))
        finally:
            client.close()
        raise AgentUnavailableError(
            f"aplicacao indisponivel em {path} apos {self._max_retries + 1} tentativas"
            f": {last_error.__class__.__name__ if last_error else 'erro'}"
        )

    def classify(self, text: str, trace_id: str | None) -> dict:
        return self._post("/api/agent/classify", {"changeText": text}, trace_id)

    def analyze_code(self, text: str, trace_id: str | None, request_id: str | None = None) -> dict:
        payload: dict = {"changeText": text}
        if request_id:
            payload["requestId"] = request_id
        return self._post("/api/agent/analyze-code", payload, trace_id)

    def retrieve_knowledge(
        self, text: str, trace_id: str | None, request_id: str | None = None
    ) -> dict:
        payload: dict = {"changeText": text}
        if request_id:
            payload["requestId"] = request_id
        return self._post("/api/agent/retrieve-knowledge", payload, trace_id)

    def retrieve_history(
        self, text: str, trace_id: str | None, request_id: str | None = None
    ) -> dict:
        payload: dict = {"changeText": text}
        if request_id:
            payload["requestId"] = request_id
        return self._post("/api/agent/retrieve-history", payload, trace_id)

    def security_assessment(self, payload: dict, trace_id: str | None) -> dict:
        return self._post("/api/agent/security-assessment", payload, trace_id)

    def analyze_impact(self, payload: dict, trace_id: str | None) -> dict:
        return self._post("/api/agent/analyze-impact", payload, trace_id)

    def assess_risk(self, payload: dict, trace_id: str | None) -> dict:
        return self._post("/api/agent/assess-risk", payload, trace_id)

    def generate_test_plan(self, payload: dict, trace_id: str | None) -> dict:
        # Etapa QA: RAG + code review + geracao + refinamento (varias chamadas LLM
        # sequenciais) - usa timeout proprio, maior que o das demais etapas.
        return self._post("/api/agent/generate-test-plan", payload, trace_id, self._qa_timeout)
