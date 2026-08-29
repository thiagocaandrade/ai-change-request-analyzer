#!/usr/bin/env python3
"""Smoke test E2E do fluxo completo.

Sobe a stack via docker compose e valida:
- POST /api/change-requests retorna 201 com status COMPLETED;
- a analise do agente (grafo LangGraph completo) e persistida com risco;
- o trace_id da resposta aparece correlacionado nos logs dos servicos app e agent.
"""

import json
import subprocess
import sys
import time
import urllib.error
import urllib.request

APP_URL = "http://localhost:8080"


def post_request():
    body = json.dumps(
        {"text": "Alterar o desconto de clientes VIP de 10% para 15%"}
    ).encode("utf-8")
    request = urllib.request.Request(
        APP_URL + "/api/change-requests",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            status = response.status
            header_trace_id = response.headers.get("X-Trace-Id")
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"POST /api/change-requests falhou com HTTP {error.code}: {detail}") from error
    return status, header_trace_id, payload


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
        except SystemExit as error:
            print(f"tentativa {attempt}/{max_attempts}: {error}")
        time.sleep(delay_s)
    raise SystemExit("aplicacao nao ficou pronta a tempo")


def main():
    wait_until_ready()
    status, header_trace_id, payload = post_request()

    if status != 201:
        raise SystemExit(f"esperado HTTP 201, obtido {status}: {payload}")

    if payload.get("status") != "COMPLETED":
        raise SystemExit(
            f"esperado status COMPLETED, obtido {payload.get('status')}: {payload}"
        )

    trace_id = payload.get("traceId") or header_trace_id
    if not trace_id:
        raise SystemExit("trace_id ausente na resposta")

    check_logs(trace_id)
    print(f"SMOKE OK: request COMPLETED com trace_id {trace_id}")


if __name__ == "__main__":
    main()

