## Context

Estado atual (verificado no código):

- Logs JSON com `trace_id` já existem: `TraceIdFilter` popula MDC e `logback-spring.xml` usa LogstashEncoder (os campos de MDC já saem no JSON). Falta padronizar os campos de negócio (`node`, `event`, `duration_ms`, `status`, `error`, `risk`, `tool`, `model`, `request_id`).
- Resiliência pontual já existe: `ResilientToolCallback` (timeout + retry fixo), `AgentClient` (timeout + retry + backoff), `AiAnalysisService` (timeout + retry sem backoff), `RagService` (só fallback, sem timeout/retry). Não há registro uniforme de tentativas.
- `spring-boot-starter-actuator` já está no `pom.xml`; apenas `health` está exposto em `management.endpoints.web.exposure.include`.
- O grafo LangGraph roda no sidecar Python e consome os endpoints `POST /api/agent/**` — cada chamada corresponde a uma etapa da análise e é o ponto natural de instrumentação Java-side.
- Motivação e requisitos: ver `proposal.md` (Why) e `specs/` (observability, resilience + deltas).

## Goals / Non-Goals

**Goals:**

- Dois sinais correlacionados por `trace_id` (logs JSON padronizados + eventos de auditoria persistidos), sem infraestrutura nova.
- Uma política de resiliência única e determinística reutilizada por LLM, MCP, RAG, tools e client do agente.
- Reconstrução de execução consultável via API (base da página de trace da change 07).

**Non-Goals:**

- Sem tracing distribuído (OpenTelemetry/Jaeger), sem spans, sem amostragem.
- Sem alterar o sidecar Python ou o grafo LangGraph.
- Sem retenção/expurgo de eventos de trace (fora de escopo acadêmico).
- Sem alterar comportamento de negócio (RiskPolicy, detecção de injeção, approval).

## Decisions

### D1 — Evento de auditoria persistido (`TraceEvent`) como segundo sinal

Registrar cada evento de execução numa tabela `trace_event` (JPA, `ddl-auto: update`) com campos `trace_id`, `request_id`, `node`, `event`, `duration_ms`, `status`, `error`, `risk`, `tool`, `model`, `created_at`, e expor `GET /api/traces/{traceId}` (200 ordenado cronológico / 404).

- Alternativa rejeitada: Micrometer Tracing + propagação de contexto — exige dependências novas e infra de coleta; a tabela de auditoria é simples, demonstrável e vira fonte da página de trace (change 07).
- O contrato (§15) pede "trace/span, node duration, LLM latency, tool latency, approval event, final status" — todos cobertos por eventos com `duration_ms` por etapa.

### D2 — Micrometer via Actuator existente, sem dependência nova

Um componente único `AnalysisMetrics` registra timers/counters (`analysis_duration`, `llm_calls`, `tool_calls`, `tool_errors`, `high_risk_changes`, `prompt_injection_count`, `validation_failures`); expor o endpoint `metrics` em `application.yml`. Micrometer é transitivo do Spring Boot; nada novo no `pom.xml`.

### D3 — `ResilienceExecutor` próprio em vez de Resilience4j

Wrapper determinístico pequeno (~1 classe): `execute(node, op, timeoutMs, fallback)` com timeout via executor, máx. 3 tentativas (1 + 2 retries), backoff crescente (`baseMs * attempt`, limitado), registro de cada tentativa em log estruturado e como `TraceEvent`, e fallback explícito marcado.

- Alternativa rejeitada: Resilience4j — dependência externa para uma política trivial; viola "implementação pequena e demonstrável" e dificulta testes determinísticos.
- Refatorações: `ResilientToolCallback` passa a delegar ao executor (mantém o contrato `ToolCallback`, então o servidor MCP — que expõe os mesmos callbacks — herda o comportamento); `AiAnalysisService.generate` ganha backoff + registro de tentativas; `RagService.search` passa a executar a busca com timeout/retry/backoff; `AgentClient` mantém o backoff existente e passa a registrar cada tentativa como evento.

### D4 — Instrumentação nos pontos de entrada (mapeamento nós do grafo)

Cada endpoint `POST /api/agent/**` do `AgentGatewayController` corresponde 1:1 a uma etapa consumida pelos nós do grafo — `classify`, `analyze-code`, `retrieve-knowledge`, `retrieve-history`, `security-assessment`, `analyze-impact`, `assess-risk`, `generate-test-plan`. Registrar eventos `started`/`completed`/`failed` com `node` = endpoint, `duration_ms`, `status` e `error` nesses pontos, no pipeline público (`ChangeRequestController` + `AnalysisService`) e nas integrações internas (IA: `model` + `llm_calls`; tools: `tool`; RAG; memória). Assim a reconstrução por `trace_id` cobre o fluxo completo sem modificar o sidecar.

### D5 — Logs: manter LogstashEncoder; padronizar campos nos statements

Sem mudar o `logback-spring.xml`. Um helper pequeno (`TraceLogger`/métodos auxiliares no `TraceService`) emite os campos padronizados como pares chave=valor nas mensagens JSON, aproveitando o MDC já existente. Os campos `request_id` e `risk` são adicionados via MDC quando disponíveis no escopo da requisição/análise.

### D6 — Falha de telemetria não derruba o fluxo

`TraceService.record` é defensivo: se a persistência do evento falhar, loga erro e segue (a análise não pode quebrar por causa da observabilidade). Métricas idem.

## Risks / Trade-offs

- [Volume de eventos no Postgres cresce com cada análise] → Eventos são poucos por execução (≈ 8 etapas + tools + IA); aceitável para o escopo; expurgo fica como decisão futura documentada.
- [Testes H2] → `TraceEvent` usa tipos JPA portáveis (string, long, timestamp), sem tipos exclusivos do Postgres; testes de repositório seguem o padrão H2 existente.
- [Backoff real atrasaria testes] → tempo de backoff configurável via propriedade (`resilience.backoff-ms`); testes usam valor mínimo.
- [Registro de tentativas duplica logs] → volume aceitável e é exigência do contrato (retries bounded, cada tentativa visível); mensagens curtas e estruturadas.
- [Endpoint `/api/traces` expõe dados internos] → endpoint de leitura apenas, sem segredos em nenhum campo (regra existente de não expor secrets), consistente com os demais endpoints internos `/api/agent/**`.

## Migration Plan

1. Aplicar a change na branch `feature/observability-and-resilience`; tabela `trace_event` criada automaticamente por `ddl-auto: update` no próximo boot (sem script manual).
2. Rollback: reverter a change — a tabela fica inerte; sem alteração destrutiva de dados existentes.
3. Pré-condição de entrega: `mvn test` verde e smoke (`docker compose up` + `scripts/smoke_test.py`) sem regressão nos Cenários A/B.
