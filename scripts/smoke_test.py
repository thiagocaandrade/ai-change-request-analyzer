#!/usr/bin/env python3
"""Smoke test E2E do fluxo completo (Cenario A: desconto VIP 10% -> 15%).

Sobe a stack via docker compose e valida:
- POST /api/change-requests retorna 201 com status COMPLETED;
- a analise do agente (grafo LangGraph + evidencia real da aplicacao) e
  persistida com risco;
- com chave de IA (AI_CHAT_API_KEY): classificacao via LLM e RAG com fontes
  e score (quando AI_EMBEDDING_API_KEY presente);
- sem chave: fluxo degradado marcado (analysis_unavailable), mantendo
  tools funcionais (codigo) e memoria (historico);
- o trace_id da resposta aparece correlacionado nos logs dos servicos app e agent.
"""

import json
import os
import subprocess
import time
import urllib.error
import urllib.request

APP_URL = "http://localhost:8080"

CHANGE_TEXT = "Alterar o desconto de clientes VIP de 10% para 15%"


def post(path, payload):
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        APP_URL + path,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"POST {path} falhou com HTTP {error.code}: {detail}") from error


def get(path):
    request = urllib.request.Request(APP_URL + path)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.headers.get("X-Trace-Id"), json.loads(
                response.read().decode("utf-8")
            )
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"GET {path} falhou com HTTP {error.code}: {detail}") from error


def post_request():
    status, payload = post("/api/change-requests", {"text": CHANGE_TEXT})
    return status, payload


def check_logs(trace_id):
    for service in ("app", "agent"):
        result = subprocess.run(
            ["docker", "compose", "logs", "--no-color", service],
            capture_output=True,
            text=True,
            check=True,
        )
        logs = result.stdout + result.stderr
        if trace_id not in logs:
            raise SystemExit(
                f"trace_id {trace_id} nao encontrado nos logs do servico '{service}'"
            )
        print(f"trace_id {trace_id} correlacionado nos logs de '{service}'")


def wait_until_ready(max_attempts=30, delay_s=2):
    for attempt in range(1, max_attempts + 1):
        try:
            post_request()
            return
        except (SystemExit, urllib.error.URLError, TimeoutError) as error:
            print(f"tentativa {attempt}/{max_attempts}: {error}")
        time.sleep(delay_s)
    raise SystemExit("aplicacao nao ficou pronta a tempo")


def check_agent_contract(ai_mode):
    has_embedding = bool(os.getenv("AI_EMBEDDING_API_KEY"))

    status, classification = post("/api/agent/classify", {"changeText": CHANGE_TEXT})
    if status != 200:
        raise SystemExit(f"classify retornou HTTP {status}")
    if ai_mode:
        if classification.get("category") != "business_rule":
            raise SystemExit(
                f"modo AI: esperada classificacao business_rule, obtida {classification}"
            )
    else:
        if classification.get("degraded") is not True:
            raise SystemExit(f"modo degradado: esperado degraded=true, obtido {classification}")
        if classification.get("notes") != "analysis_unavailable":
            raise SystemExit(f"modo degradado: esperado analysis_unavailable, obtido {classification}")
    print(f"classify ok: {classification}")

    status, code_evidence = post("/api/agent/analyze-code", {"changeText": CHANGE_TEXT})
    if status != 200:
        raise SystemExit(f"analyze-code retornou HTTP {status}")
    if not code_evidence.get("findings"):
        raise SystemExit(f"tools deveriam retornar evidencias de codigo: {code_evidence}")
    print(f"analyze-code ok: {len(code_evidence.get('findings', []))} achados")

    status, knowledge = post("/api/agent/retrieve-knowledge", {"changeText": CHANGE_TEXT})
    if status != 200:
        raise SystemExit(f"retrieve-knowledge retornou HTTP {status}")
    if has_embedding:
        if not knowledge.get("documents"):
            raise SystemExit(f"RAG deveria retornar documentos: {knowledge}")
        for document in knowledge["documents"]:
            if not document.get("source") or document.get("score") is None:
                raise SystemExit(f"documento RAG sem fonte/score: {document}")
        print(f"RAG ok: {len(knowledge['documents'])} documentos com fonte e score")
    else:
        if knowledge.get("degraded") is not True:
            raise SystemExit(f"sem embedding, RAG deveria estar degradado: {knowledge}")
        print("RAG degradado marcado (sem AI_EMBEDDING_API_KEY)")

    status, history = post("/api/agent/retrieve-history", {"changeText": "desconto VIP"})
    if status != 200:
        raise SystemExit(f"retrieve-history retornou HTTP {status}")
    if not history.get("findings"):
        raise SystemExit(f"memoria deveria retornar historico: {history}")
    print(f"retrieve-history ok: {len(history['findings'])} registros")

    status, plan = post(
        "/api/agent/generate-test-plan",
        {
            "changeText": CHANGE_TEXT,
            "risk": {"level": "MEDIUM", "confidence": 0.5},
            "classification": {},
            "impactFindings": [],
        },
    )
    if status != 200:
        raise SystemExit(f"generate-test-plan retornou HTTP {status}")
    if not plan.get("recommendations"):
        raise SystemExit(f"plano de testes vazio: {plan}")
    print(f"generate-test-plan ok: {len(plan['recommendations'])} recomendacoes")


def main():
    ai_mode = bool(os.getenv("AI_CHAT_API_KEY"))
    print(f"smoke mode={('ai' if ai_mode else 'degraded')}")

    wait_until_ready()
    status, payload = post_request()

    if status != 201:
        raise SystemExit(f"esperado HTTP 201, obtido {status}: {payload}")

    if payload.get("status") != "COMPLETED":
        raise SystemExit(
            f"esperado status COMPLETED, obtido {payload.get('status')}: {payload}"
        )

    trace_id = payload.get("traceId")
    if not trace_id:
        raise SystemExit("trace_id ausente na resposta")
    request_id = payload.get("id")
    if not request_id:
        raise SystemExit("id ausente na resposta")

    _, _, analysis = get(f"/api/change-requests/{request_id}/analysis")
    risk_level = analysis.get("riskLevel")
    if risk_level not in ("LOW", "MEDIUM", "HIGH"):
        raise SystemExit(f"analise persistida sem risco valido: {analysis}")
    print(f"analise persistida com risco {risk_level}")
    if not ai_mode:
        if analysis.get("rationale") != "analysis_unavailable":
            raise SystemExit(
                f"modo degradado: esperado rationale analysis_unavailable, obtido {analysis.get('rationale')}"
            )
        print("fluxo degradado marcado (analysis_unavailable)")

    check_agent_contract(ai_mode)
    check_logs(trace_id)
    print(f"SMOKE OK: request COMPLETED com trace_id {trace_id} (mode={('ai' if ai_mode else 'degraded')})")


if __name__ == "__main__":
    main()
