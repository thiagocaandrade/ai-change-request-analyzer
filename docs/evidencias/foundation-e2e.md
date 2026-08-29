# Evidência — Execução local ponta a ponta (change `foundation`)

- **Data da execução:** 2026-08-29
- **Ambiente:** Docker 27.1.1 · Docker Compose v2.29.1 (Docker Desktop, Linux containers)
- **Change:** `foundation` (task 6.2)
- **Objetivo:** registrar evidência da execução local completa — `docker compose up`, análise de ponta a ponta e logs correlacionados por `trace_id`.

## 1. Subida da stack (`docker compose up`)

Comando executado:

```bash
docker compose up --build -d
```

Resultado após a subida — os três serviços ficaram saudáveis (`healthy`):

| Nome | Imagem | Status | Portas |
|---|---|---|---|
| `analyzer-db-1` | `pgvector/pgvector:pg16` | Up (healthy) | 0.0.0.0:5432->5432/tcp |
| `analyzer-agent-1` | `analyzer-agent` | Up (healthy) | 0.0.0.0:8000->8000/tcp |
| `analyzer-app-1` | `analyzer-app` | Up (healthy) | 0.0.0.0:8080->8080/tcp |

### Health checks

- `GET http://localhost:8080/actuator/health` → **200** `{"groups":["liveness","readiness"],"status":"UP"}`
- `GET http://localhost:8000/health` → **200** `{"status":"ok"}`

## 2. Análise ponta a ponta

Requisição de análise (cenário de sucesso):

```bash
curl -X POST http://localhost:8080/requests \
  -H "Content-Type: application/json" \
  -d '{"text":"Alterar o desconto de clientes VIP de 10% para 15%."}'
```

Resposta obtida:

- **HTTP:** `201`
- **Cabeçalho `X-Trace-Id`:** `43ff3726-bb03-4241-bad6-8a13e4ee366e`

```json
{
  "id": "4978aca3-7a61-4b0c-9fbd-cdd7a7e9a4a1",
  "text": "Alterar o desconto de clientes VIP de 10% para 15%.",
  "status": "COMPLETED",
  "traceId": "43ff3726-bb03-4241-bad6-8a13e4ee366e",
  "result": {
    "request_id": "4978aca3-7a61-4b0c-9fbd-cdd7a7e9a4a1",
    "processed_text": "Alterar o desconto de clientes VIP de 10% para 15%.",
    "summary": "Analise de fundacao executada (stub deterministico)"
  }
}
```

Persistência confirmada via `GET http://localhost:8080/requests/4978aca3-7a61-4b0c-9fbd-cdd7a7e9a4a1` → retorna o mesmo registro com `status: "COMPLETED"` e o mesmo `traceId`.

## 3. Logs correlacionados por `trace_id`

O mesmo `trace_id` `43ff3726-bb03-4241-bad6-8a13e4ee366e` aparece em **todas** as linhas de log da requisição nos dois serviços.

### `app` (Spring Boot · logstash-logback-encoder)

```json
{"@timestamp":"2026-08-29T20:42:06.23686502Z","@version":"1","message":"request_started method=POST path=/requests","logger_name":"com.ai.change.request.analyzer.config.TraceIdFilter","thread_name":"http-nio-8080-exec-9","level":"INFO","level_value":20000,"trace_id":"43ff3726-bb03-4241-bad6-8a13e4ee366e","service":"analyzer"}
{"@timestamp":"2026-08-29T20:42:06.522448397Z","@version":"1","message":"request_persisted id=4978aca3-7a61-4b0c-9fbd-cdd7a7e9a4a1 status=COMPLETED","logger_name":"com.ai.change.request.analyzer.web.ChangeRequestController","thread_name":"http-nio-8080-exec-9","level":"INFO","level_value":20000,"trace_id":"43ff3726-bb03-4241-bad6-8a13e4ee366e","service":"analyzer"}
{"@timestamp":"2026-08-29T20:42:06.530871705Z","@version":"1","message":"request_finished status=201","logger_name":"com.ai.change.request.analyzer.config.TraceIdFilter","thread_name":"http-nio-8080-exec-9","level":"INFO","level_value":20000,"trace_id":"43ff3726-bb03-4241-bad6-8a13e4ee366e","service":"analyzer"}
```

### `agent` (FastAPI · structlog)

```json
{"method": "POST", "path": "/analyze", "event": "request_started", "trace_id": "43ff3726-bb03-4241-bad6-8a13e4ee366e", "level": "info", "timestamp": "2026-08-29T20:42:06.441438Z"}
{"status_code": 200, "event": "request_finished", "trace_id": "43ff3726-bb03-4241-bad6-8a13e4ee366e", "level": "info", "timestamp": "2026-08-29T20:42:06.449069Z"}
```

## Conclusão

A execução local completa foi verificada: a stack sobe com `docker compose up`, a análise ponta a ponta retorna `201` com `status: COMPLETED` e o mesmo `trace_id` correlaciona os logs JSON dos serviços `app` e `agent` (os dois sinais de observabilidade). Isso satisfaz o cenário da spec `platform-foundation` ("Ambiente sobe e responde") e os cenários de trace das specs `request-pipeline` e `agent-runtime`.
