## 1. Logs JSON estruturados com correlação por trace_id

- [x] 1.1 Criar entidade `TraceEvent` (traceId, requestId, node, event, durationMs, status, error, risk, tool, model, createdAt) e `TraceEventRepository` no pacote `observability`; verificar `mvn test` verde com teste de persistência H2 salvando e recuperando eventos por traceId em ordem cronológica
- [x] 1.2 Criar `TraceService` (record com campos padronizados, busca por traceId ordenada, falha de persistência não derruba o fluxo) e endpoint `GET /api/traces/{traceId}` (200 com eventos / 404 sem eventos); verificar com MockMvc + H2
- [x] 1.3 Padronizar campos nos logs (node, event, duration_ms, status, error, risk, tool, model, request_id) nos pontos principais — `TraceIdFilter` (request_id), `ChangeRequestController`, `AgentGatewayController` (duration_ms por endpoint), `AiAnalysisService` (model), `ResilientToolCallback` (tool), `RagService`; verificar `TraceIdLoggingTest` e log JSON contendo os campos

## 2. Métricas de execução (duration, llm_calls, tool_calls, erros, high_risk, injection)

- [x] 2.1 Criar `AnalysisMetrics` (Micrometer) com `analysis_duration` (timer) e os counters `llm_calls`, `tool_calls`, `tool_errors`, `high_risk_changes`, `prompt_injection_count`, `validation_failures`; verificar teste unitário com `SimpleMeterRegistry`
- [x] 2.2 Instrumentar os pontos de gravação: `AnalysisService.registerAnalysis` (analysis_duration, high_risk_changes), `AiAnalysisService` (llm_calls, validation_failures), `ResilientToolCallback` (tool_calls, tool_errors) e persistência de eventos de segurança (prompt_injection_count); verificar teste de integração: análise concluída incrementa as métricas esperadas
- [x] 2.3 Expor `metrics` em `management.endpoints.web.exposure.include` no `application.yml`; verificar endpoint `/actuator/metrics` listando as sete métricas após uma análise

## 3. Resiliência: timeout, retry, backoff e fallback em LLM, MCP, RAG e tools

- [x] 3.1 Criar `ResilienceExecutor` no pacote `resilience` (timeout configurável, máx. 3 tentativas, backoff crescente limitado, registro de cada tentativa em log estruturado e TraceEvent, fallback explícito); verificar teste unitário: sucesso imediato, timeout→retry→sucesso, retry exaurido→fallback e propagação de falha quando exigido
- [x] 3.2 Refatorar `ResilientToolCallback` para delegar ao `ResilienceExecutor` mantendo o contrato `ToolCallback` (servidor MCP herda o comportamento); verificar `ResilientToolCallbackTest` atualizado (timeout, retry, exaurido)
- [x] 3.3 Adicionar backoff e registro de tentativas no `AiAnalysisService` e timeout/retry/backoff no `RagService.search` usando o executor; verificar `AiAnalysisServiceTest` e `RagServiceTest` atualizados (timeout, retry, fallback marcado)
- [x] 3.4 Registrar cada tentativa do `AgentClient` como TraceEvent e padronizar logs; verificar `AgentClientTest` (retry, exaurido, propagação com causa registrada)

## 4. Testes de resiliência e observabilidade + evidência

- [x] 4.1 Criar `TraceTest`: reconstrução completa de uma análise por trace_id (eventos do pipeline + gateways + IA + tools em ordem), 404 para trace inexistente e nenhum segredo nos eventos; verificar `mvn test` verde
- [x] 4.2 Criar `ResilienceTest`: cenários integrados de timeout, retry, retry exaurido com fallback e propagação de falha crítica (agente indisponível → estado failed); verificar `mvn test` verde
- [x] 4.3 Registrar evidência `docs/evidence/07-observability.png` (trace reconstruído + métricas) e atualizar README (seções observabilidade e resiliência) e a matriz de requisitos; verificar presença do arquivo e consistência do README
