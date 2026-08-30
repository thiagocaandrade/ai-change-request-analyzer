## Why

A entrega exige dois sinais de observabilidade correlacionados (contrato §15) e resiliência limitada em toda integração externa (contrato §16). Hoje há logs JSON com `trace_id` (`TraceIdFilter` + LogstashEncoder), timeout/retry nas tools (`ResilientToolCallback`) e no client do agente (`AgentClient`) — mas os campos são inconsistentes (sem `node`, `event`, `duration_ms`, `risk`, `tool`, `model`), não existem métricas, uma execução não é reconstruível por `trace_id`, o RAG não tem timeout/retry e o retry do LLM não tem backoff. É a change 06 do roadmap (FASE 14–15).

## What Changes

- Campos de log JSON padronizados em toda a aplicação: `trace_id`, `request_id`, `node`, `event`, `duration_ms`, `status`, `error`, `risk`, `tool`, `model`.
- Novo pacote `observability`: registro de auditoria persistido por execução (`TraceEvent`) + endpoint `GET /api/traces/{traceId}` — segundo sinal observável e reconstrução completa de uma execução.
- Métricas Micrometer: `analysis_duration`, `llm_calls`, `tool_calls`, `tool_errors`, `high_risk_changes`, `prompt_injection_count`, `validation_failures`, expostas via Actuator.
- Política única de resiliência (timeout configurável, retry máx. 2, backoff entre tentativas, fallback explícito, registro de cada tentativa) aplicada a LLM, MCP, RAG e tools. RAG ganha timeout/retry; LLM ganha backoff.
- Testes de timeout, retry, retry exaurido, fallback e propagação de falha; evidência `docs/evidence/07-observability.png`.

## Capabilities

### New Capabilities

- `observability`: logs JSON com campos padronizados, métricas de execução e reconstrução de uma execução por `trace_id` via registro de auditoria persistido.
- `resilience`: política única de resiliência (timeout, retry limitado com backoff, fallback explícito e registro de cada tentativa) para todas as integrações externas.

### Modified Capabilities

- `analysis-tools`: o requisito de timeout/retry das tools passa a exigir backoff entre tentativas e registro de cada tentativa.
- `ai-capabilities`: o requisito de timeout em chamadas ao modelo passa a exigir backoff e registro de cada tentativa.
- `rag-knowledge`: a busca semântica passa a ter timeout e retry limitado com backoff antes do fallback degradado.

## Impact

- Código: `config/TraceIdFilter`, `tools/ResilientToolCallback`, `ai/AiAnalysisService`, `rag/RagService`, `api/AgentClient`, `service/AnalysisService`, `web/ChangeRequestController`, `web/AgentGatewayController`; novo pacote `observability` e novo pacote `resilience`.
- Configuração: `application.yml` (exposição do endpoint `metrics`; propriedades de resiliência), `logback-spring.xml` inalterado.
- Dados: nova tabela `trace_event` (JPA, `ddl-auto: update`).
- Testes: novos `TraceTest` e `ResilienceTest`; atualização de testes existentes afetados.
- Sem novas dependências: actuator já está no `pom.xml`; Micrometer é transitivo do Spring Boot.

## Non-Goals

- Sem OpenTelemetry/Jaeger ou tracing distribuído; sem Resilience4j.
- Sem alteração no sidecar Python nem no grafo LangGraph.
- Sem alterar regras determinísticas (RiskPolicy, detecção de injeção) — apenas instrumentá-las.
- Sem persistir conteúdo sensível em eventos de trace; segredos continuam proibidos em logs e respostas.
