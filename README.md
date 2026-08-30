# AI Change Request Analyzer

Aplicação acadêmica que recebe uma solicitação de alteração em software e produz uma análise estruturada de impacto, risco e testes. O agente **não** altera código automaticamente.

Caminho executável de ponta a ponta: fundação (`foundation`), domínio e API (`domain-and-api`), orquestração LangGraph completa (`langgraph-orchestration`), **IA, tools, RAG e memória reais** (`ai-rag-memory-tools`), **segurança + aprovação humana** (`security-and-human-approval`), **observabilidade + resiliência** (`observability-and-resilience`): logs JSON com campos padronizados, eventos de auditoria persistidos por execução (`GET /api/traces/{traceId}`), métricas Micrometer e política única de resiliência (timeout/retry/backoff/fallback) em LLM, MCP, RAG e tools — e **interface web** (`frontend`): páginas Thymeleaf de formulário, resultado com aprovação humana e trace da execução. As changes 08+ seguem em `docs/roadmap.md`.

## Visão geral dos componentes

| Componente | Tecnologia | Responsabilidade |
|---|---|---|
| `app` | Java 21 · Spring Boot 4.1.1 · JPA · Spring AI | Recebe `POST /api/change-requests`, gera `trace_id`, delega ao agente com timeout/retry, persiste solicitação + análise estruturada. Hospeda a camada IA (prompts versionados + structured output + retry), as 4 tools, o RAG pgvector, a memória, o servidor MCP e os endpoints internos `/api/agent/**`. |
| `agent` | Python 3.12 · FastAPI · LangGraph | Sidecar que executa o grafo LangGraph completo (13 nós) e obtém evidência real da aplicação via HTTP (`/api/agent/**`, timeout/retry, `X-Trace-Id`). |
| `db` | PostgreSQL 16 + pgvector | Persistência do domínio (tabelas `change_request`, `change_analysis`, `impact_finding`, `risk_assessment`, `test_recommendation`, `approval`, `security_assessment`) e índice vetorial `vector_store` da base de conhecimento. |
| CI | GitHub Actions | Lint (Spotless/ruff), testes e build dos dois serviços + job E2E. |

## Diagrama de arquitetura

```mermaid
flowchart LR
    Client[Cliente HTTP] -->|POST /api/change-requests| App[app · Spring Boot]
    App -->|POST /analyze<br/>X-Trace-Id| Agent[agent · FastAPI + LangGraph]
    Agent -->|POST /api/agent/**<br/>X-Trace-Id| App
    App -->|JPA| DB[(db · PostgreSQL + pgvector)]
    App -->|RAG| KB[(knowledge/ · 6 docs)]
    App -->|tools · leitura no repo| Repo[repositorio configurado]
    McpClient[Cliente MCP] -->|JSON-RPC /mcp| App
    App -.->|logs JSON · trace_id| Logs[stdout]
    Agent -.->|logs JSON · trace_id| Logs
```

## Grafo LangGraph (orquestração)

O agente executa um `StateGraph` completo com estado compartilhado tipado (`ChangeRequestState`), 13 nós na ordem do roadmap, paralelização real, branching por risco e condição de parada com retry limitado:

```
validate_request → classify_request → detect_untrusted_content
  → (analyze_code ‖ retrieve_knowledge ‖ retrieve_history)   [paralelo]
  → analyze_impact → assess_risk → approval_router
      ├─ HIGH ──> human_approval → generate_test_plan
      └─ LOW/MEDIUM ──> generate_test_plan
  → validate_final_result
      ├─ válido ──> finalize
      ├─ inválido (retry ≤ 2) ──> generate_test_plan
      └─ esgotado ──> finalize_error (END_WITH_ERROR)
```

- **Estado:** `trace_id`, `change_request`, `classification`, `retrieved_documents`, `code_findings`, `historical_findings`, `impact_findings`, `risk_assessment`, `security_assessment`, `test_plan`, `approval_required`, `approval_status`, `final_result`, `errors`, `iteration_count`.
- **Sem loop infinito:** `validate_final_result` limita a 1 tentativa inicial + 2 correções; esgotado, termina com status `failed` e erros estruturados.
- **Falhas contidas:** qualquer exceção de nó vira entrada em `errors` (nunca quebra o processo nem vaza segredos); a análise segue degradada.
- **Conteúdo recuperado é dado não confiável:** `detect_untrusted_content` obtém a avaliação de segurança da aplicação via HTTP (`POST /api/agent/security-assessment`, timeout/retry) e a espelha no estado e no `final_result`; falha do endpoint → `errors` + avaliação vazia, e o grafo segue. Instruções injetadas nunca alteram risco, classificação ou fluxo.
- **Regra determinística continua no Java:** o agente sinaliza pendência (`status=pending_approval` quando HIGH); `RiskPolicy` decide e persiste a obrigatoriedade de aprovação.
- **Evidência real nas etapas:** os nós `classify_request`, `analyze_code`, `retrieve_knowledge`, `retrieve_history`, `analyze_impact`, `assess_risk` e `generate_test_plan` obtêm resultado da aplicação via `agent/tools/client.py` (httpx, timeout, retry 2, `X-Trace-Id`); falha → `errors` + coleta vazia, sem interromper o grafo. Evidência da execução no Cenário A: `docs/evidence/01-langgraph.png`.

Resposta de `POST /analyze`: `{request_id, status, result}` com `status` em `completed` | `pending_approval` | `failed` e `result` com `processed_text`, `summary`, `classification`, `risk`, `confidence`, `rationale`, `findings`, `test_plan`, `approval` e `errors` (quando houver).

## Camada IA (Spring AI)

- **Prompts versionados:** `src/main/resources/prompts/<etapa>-v1.txt` (`classification`, `impact-analysis`, `risk-analysis`, `test-generation`), carregados por `PromptRegistry` (etapa + versão); nenhum prompt de produção embutido em código.
- **Structured output validado:** `AiAnalysisService` converte a saída do LLM em records tipados (`BeanOutputConverter` + jakarta.validation), com retry limitado (máx. 2) e backoff entre tentativas via `ResilienceExecutor`. Saída inválida é descartada — nunca persistida. Esgotado o limite → fallback determinístico marcado (`degraded=true`; risco `MEDIUM`, rationale `analysis_unavailable`). Cada tentativa é registrada em log estruturado e em evento de auditoria com `model` e `trace_id`; métricas `llm_calls` e `validation_failures`.
- **Conteúdo recuperado é dado:** a evidência entra na seção delimitada `DADOS NÃO CONFIÁVEIS` do user message, nunca no system prompt.
- **Sem chave, análise segue degradada:** o `ChatClient` só existe com `ai.chat.api-key`; timeout configurável (`ai.chat.timeout-ms`); segredos nunca aparecem em logs ou respostas.

## Tools (4, executadas na JVM)

`search_code(query)`, `get_file(path)`, `search_change_history(query)`, `get_related_tests(component)` — implementadas em `tools/` como ToolCallbacks registrados no `ChatClient` (tool calling real via `ToolCallingAdvisor`).

- **Validação de entrada:** query/path/component não vazios, tamanho limitado; erro estruturado para entrada inválida.
- **Acesso restrito:** `RepoAccessPolicy` normaliza o caminho contra a raiz configurada (`tools.repo-root`); `../`, absolutos fora da raiz e vazios são rejeitados. Nenhuma tool executa shell.
- **Resiliência:** cada execução tem timeout (`tools.timeout-ms`), retry máx. 2 com backoff e registro de cada tentativa em log estruturado e evento de auditoria (`ResilientToolCallback` → `ResilienceExecutor`); falha após retries → erro registrado e a análise segue. Métricas `tool_calls`/`tool_errors`.
- **MCP:** servidor MCP da aplicação (Spring AI, streamable HTTP em `/mcp`) expõe `search_code` e `get_file` com os mesmos callbacks. Evidência: `docs/evidence/04-mcp.png`.

## RAG (pgvector)

- **Base de conhecimento:** `knowledge/` com 6 documentos (architecture, business-rules, discount-policy, coding-guidelines, testing-guidelines, security-policy), incluindo a regra VIP 10% do Cenário A.
- **Ingestão idempotente:** migration idempotente (`PgVectorSchemaMigration`: `CREATE EXTENSION IF NOT EXISTS vector` + tabela `vector_store` + índice HNSW) e `KnowledgeIngestionService` (chunking por seção/parágrafo + embeddings + ingestão no startup **somente se a base estiver vazia** — restart não duplica).
- **Busca com metadata:** `RagService` com top-k configurável (`ai.rag.top-k`, default 4), threshold (`ai.rag.similarity-threshold`) e resultados com `source`, `document_id`, `chunk_id` e `score`, em ordem decrescente. A busca executa com timeout (`ai.rag.timeout-ms`), retry limitado e backoff via `ResilienceExecutor`; falha, estouro de tempo ou base vazia → lista vazia marcada (degradada). Busca bem-sucedida registra as fontes no evento `rag_search` (`detail` do `TraceEvent`), visíveis na página de trace. Evidência: `docs/evidence/03-rag.png`.
- **Embeddings configuráveis por env:** sem `ai.embedding.api-key` o RAG desativa e a análise segue degradada.

## Memória persistente

`AnalysisMemoryService` busca análises anteriores por termos (ILIKE no texto da solicitação), componente afetado, regra de negócio e classificação de risco, retornando identificador e resumo ("semelhante à CR-XXX"). Falha de busca → lista vazia marcada, sem interromper a análise.

## Segurança (prompt injection) e aprovação humana

- **Detecção determinística no Java (`SecurityAssessmentService`):** marcadores de injeção ("ignore as instruções", "classifique como low", …) varridos no texto da solicitação e em todo conteúdo retornado pelos gateways de coleta (`analyze-code` → fonte `code`, `retrieve-knowledge` → `knowledge`, `retrieve-history` → `history`), com fonte por origem e dedupe por `(type, source, evidence)`.
- **Eventos persistidos:** cada evento (`security_assessment`, vinculado à solicitação) registra `type=prompt_injection`, `source`, `evidence`, `action=IGNORED` e `traceId`; é exposto em `GET /api/change-requests/{id}/analysis` como `securityAssessment` (`detected` + `events`). Nenhum evento contém segredos; falha de persistência é registrada e nunca derruba o fluxo.
- **Instrução injetada é ignorada:** a detecção nunca altera classificação, risco ou fluxo — a análise prossegue até o fim e o risco final reflete apenas a avaliação estruturada de risco.
- **LLM assiste, o Java decide:** etapa `security-analysis` com prompt versionado `security-analysis-v1.txt` (conteúdo recuperado na seção delimitada `DADOS NÃO CONFIÁVEIS`), structured output validado, retry máx. 2 e fallback determinístico marcado; a decisão final (união, dedupe, ação) é sempre determinística na aplicação.
- **Aprovação humana:** risco HIGH ⇒ aprovação `PENDING` (regra de `RiskPolicy`, intocada); somente `POST /api/change-requests/{id}/approval` (`{approver, decision}` APPROVED|REJECTED) transita o estado, registrando approver, decision, decidedAt e trace_id. Decisão inválida → 400, solicitação inexistente → 404, fora de PENDING ou não exigida → 409.
- **Cenário B ponta a ponta:** fixture no repositório com a frase oficial de injeção → evento persistido na análise, risco permanece HIGH, aprovação PENDING → decisão humana via endpoint. Evidências: `docs/evidence/05-prompt-injection.png` (evento registrado + análise concluída) e `docs/evidence/06-human-approval.png` (decisão APPROVED/REJECTED no endpoint).

## Interface web (Thymeleaf)

Uma tela server-side (sem SPA, sem framework JS) para operar o analisador pelo navegador — as páginas consomem a mesma API REST existente (`WebController` delega aos controllers REST, sem duplicar o pipeline):

- **Formulário (`GET /`):** descreve a solicitação e submete (`POST /change-requests`); texto em branco é rejeitado com mensagem no próprio formulário (sem chamar o agente); texto válido dispara a análise e redireciona (303) para o resultado. Inclui consulta de execução por `trace_id`.
- **Resultado (`GET /requests/{id}`):** status, nível de risco (badge LOW/MEDIUM/HIGH), confiança, justificativa, findings, testes recomendados, eventos de segurança e estado de aprovação. HIGH + PENDING exibe o formulário de decisão (aprovador + APPROVED/REJECTED → `POST /requests/{id}/approval`); LOW/MEDIUM indica "aprovação não exigida"; decisão registrada reflete APPROVED/REJECTED com o aprovador; análise `FAILED` exibe o motivo legível; solicitação inexistente → página 404 amigável (HTML, não o JSON do handler REST).
- **Trace (`GET /traces/{traceId}`):** reconstrói a execução com eventos em ordem cronológica (etapa, evento, duração, status, erro, tool, modelo, momento) e a seção de documentos recuperados (origem + score) — o `RagService` registra as fontes no campo opcional `detail` do `TraceEvent` (JSON compacto `[{source, document_id, score}]` truncado a 1024 caracteres, nunca o conteúdo dos documentos); busca degradada não registra `detail`; trace inexistente → página amigável.
- **Escaping garantido:** todo conteúdo não confiável (solicitação, findings, evidências de segurança, fontes recuperadas) é renderizado com `th:text` (escapamento automático do Thymeleaf); `th:utext` não é usado. Nenhum segredo aparece nas páginas.
- **Estilo:** `static/css/app.css` único (cabeçalho, cartões, badges por nível de risco/status, tabelas de eventos, layout responsivo).
- **Evidência:** `docs/evidence/08-frontend.png` (formulário, resultado HIGH e página de trace); captura reproduzível via `.kilo/scripts/frontend-evidence.ps1` (renderiza as páginas com `FrontendEvidenceDumpTest` e captura com Edge headless).

## Execução via Docker Compose

Pré-requisitos: Docker (com Compose v2) e Docker Desktop em execução.

```bash
cp .env.example .env   # opcional: ajuste valores antes de subir
docker compose up --build
```

Quando os três serviços estiverem saudáveis:

- `app`: http://localhost:8080
- `agent`: http://localhost:8000
- `db`: localhost:5432

Health checks:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8000/health
```

### Fluxo ponta a ponta

```bash
curl -X POST http://localhost:8080/api/change-requests \
  -H "Content-Type: application/json" \
  -d '{"text":"Alterar o desconto de clientes VIP de 10% para 15%."}'
```

A resposta devolve `id`, `status` (`COMPLETED`), `traceId` e o resumo da `analysis` tipada. O mesmo `traceId` é propagado ao agente via cabeçalho `X-Trace-Id` e aparece correlacionado nos logs JSON dos dois serviços.

### Registro de análise estruturada

```bash
curl -X POST http://localhost:8080/api/change-requests/<id>/analysis \
  -H "Content-Type: application/json" \
  -d '{
    "findings": [{"component":"discount-service","description":"Desconto VIP alterado","severity":"HIGH"}],
    "riskAssessment": {"level":"HIGH","confidence":0.95,"rationale":"regra financeira"},
    "testRecommendations": [{"component":"discount-service","description":"Cobrir desconto VIP de 15%","priority":"HIGH"}]
  }'

curl http://localhost:8080/api/change-requests/<id>/analysis
```

**Regra determinística (Java, nunca no LLM):** risco `HIGH` ⇒ aprovação humana obrigatória com estado `PENDING`; confidence fora de `[0,1]` ⇒ rejeição com `invalid_confidence`.

### Decisão humana sobre análise HIGH

```bash
curl -X POST http://localhost:8080/api/change-requests/<id>/approval \
  -H "Content-Type: application/json" \
  -d '{"approver":"revisora","decision":"APPROVED"}'
```

A resposta devolve o estado atualizado (`APPROVED` ou `REJECTED`) com `approver`, `decision`, `decidedAt` e `traceId`. Decisão fora de `PENDING` (ou sem aprovação exigida) retorna 409; payload inválido 400; identificador inexistente 404.

## Variáveis de ambiente

Toda configuração é fornecida por variáveis de ambiente (referência em `.env.example`, **sem valores reais**; o `.env` real nunca é versionado).

| Variável | Padrão | Uso |
|---|---|---|
| `POSTGRES_DB` | `analyzer` | Nome do banco |
| `POSTGRES_USER` | `analyzer` | Usuário do banco |
| `POSTGRES_PASSWORD` | `change-me` | Senha do banco (trocar em produção) |
| `POSTGRES_PORT` | `5432` | Porta publicada do banco |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/analyzer` | JDBC URL do Spring |
| `SPRING_DATASOURCE_USERNAME` | `analyzer` | Usuário JDBC |
| `SPRING_DATASOURCE_PASSWORD` | `change-me` | Senha JDBC |
| `AGENT_URL` | `http://agent:8000` | Base URL do agente |
| `AGENT_PORT` | `8000` | Porta publicada do agente |
| `APP_URL` | `http://app:8080` | Base URL da aplicação para o sidecar (contrato `/api/agent/**`) |
| `APP_PORT` | `8080` | Porta publicada do app |
| `AI_CHAT_MODEL` | *(vazio)* | Modelo de IA (chat) |
| `AI_CHAT_API_KEY` | *(vazio)* | Chave de API do chat (sem chave, a análise segue degradada) |
| `AI_CHAT_BASE_URL` | *(vazio)* | Base URL do provedor de IA |
| `AI_EMBEDDING_MODEL` | *(vazio)* | Modelo de embeddings (RAG) |
| `AI_EMBEDDING_API_KEY` | *(vazio)* | Chave de embeddings (sem chave, o RAG fica desativado) |
| `AI_EMBEDDING_BASE_URL` | *(vazio)* | Base URL do provedor de embeddings |
| `AI_RAG_ENABLED` | `true` | Habilita o RAG |
| `AI_RAG_TOP_K` | `4` | Número máximo de chunks retornados |
| `AI_RAG_SIMILARITY_THRESHOLD` | `0.7` | Score mínimo de similaridade |
| `AI_RAG_KNOWLEDGE_PATH` | `/repo/knowledge` | Diretório dos documentos de conhecimento |
| `AI_RAG_TIMEOUT_MS` | `5000` | Timeout da busca RAG |
| `AI_CHAT_TIMEOUT_MS` | `30000` | Timeout das chamadas ao modelo |
| `RESILIENCE_BACKOFF_MS` | `200` | Base do backoff entre tentativas de integrações |
| `RESILIENCE_MAX_BACKOFF_MS` | `2000` | Teto do backoff entre tentativas |
| `TOOLS_REPO_ROOT` | `/repo` | Raiz do repositório acessível pelas tools (proteção contra path traversal) |

## Endpoints

| Serviço | Método | Rota | Descrição |
|---|---|---|---|
| `app` | POST | `/api/change-requests` | Cria e analisa uma solicitação de alteração |
| `app` | GET | `/api/change-requests/{id}` | Consulta status e resumo da análise de uma solicitação |
| `app` | POST | `/api/change-requests/{id}/analysis` | Registra análise estruturada (achados, risco, recomendações de teste) |
| `app` | GET | `/api/change-requests/{id}/analysis` | Consulta a análise completa tipada, incluindo avaliação de segurança |
| `app` | POST | `/api/change-requests/{id}/approval` | Decisão humana (APPROVED\|REJECTED) para análise com aprovação exigida |
| `app` | GET | `/api/traces/{traceId}` | Reconstrução da execução: eventos de auditoria em ordem cronológica (404 sem eventos) |
| `app` | GET | `/` | Página web: formulário de solicitação (Thymeleaf) |
| `app` | POST | `/change-requests` | Submete o formulário (valida texto em branco) e redireciona (303) para o resultado |
| `app` | GET | `/requests/{id}` | Página web: resultado da análise com decisão de aprovação |
| `app` | POST | `/requests/{id}/approval` | Registra a decisão humana pela página (redireciona para o resultado) |
| `app` | POST | `/traces` | Consulta de trace pela página (redireciona para `/traces/{traceId}`) |
| `app` | GET | `/traces/{traceId}` | Página web: reconstrução da execução com documentos recuperados |
| `app` | GET | `/actuator/health` | Health check |
| `app` | GET | `/actuator/metrics` | Métricas Micrometer (`analysis_duration`, `llm_calls`, `tool_calls`, `tool_errors`, `high_risk_changes`, `prompt_injection_count`, `validation_failures`) |
| `app` | POST | `/api/agent/classify` | Classificação da solicitação (IA/fallback marcado) |
| `app` | POST | `/api/agent/analyze-code` | Evidência de código e testes via tools (com varredura de injeção) |
| `app` | POST | `/api/agent/retrieve-knowledge` | Busca RAG com fontes e scores (com varredura de injeção) |
| `app` | POST | `/api/agent/retrieve-history` | Memória: análises anteriores (com varredura de injeção) |
| `app` | POST | `/api/agent/security-assessment` | Avaliação de segurança tipada (`detected`, `events`) do texto da solicitação |
| `app` | POST | `/api/agent/analyze-impact` | Achados de impacto (IA sobre evidências) |
| `app` | POST | `/api/agent/assess-risk` | Sugestão de risco (IA; regra final no Java) |
| `app` | POST | `/api/agent/generate-test-plan` | Recomendações de testes (IA/fallback marcado) |
| `app` | POST | `/mcp` | Servidor MCP (JSON-RPC streamable HTTP): `search_code`, `get_file` |
| `agent` | POST | `/analyze` | Executa o grafo de análise (corpo `{request_id, text}`) |
| `agent` | GET | `/health` | Health check |

## Observabilidade

- **Logs JSON com campos padronizados** (`logstash-logback-encoder`, sem mudanças no logback): toda linha carrega `trace_id` e `request_id` do MDC (gerados no `TraceIdFilter`, que também loga `node=http`, `event=request_started/request_finished`, `status` e `duration_ms`); componentes instrumentados emitem `node`, `event`, `duration_ms`, `status`, `error`, `risk`, `tool` e `model` — `ChangeRequestController` e `AnalysisService` (pipeline, com `risk`), `AgentGatewayController` (started/completed por endpoint do grafo), `AiAnalysisService` (`model`), `ResilientToolCallback` (`tool`), `RagService`, `AgentClient` e `ResilienceExecutor` (cada tentativa).
- **Segundo sinal — auditoria persistida:** cada evento é gravado na tabela `trace_event` (trace_id, request_id, node, event, duration_ms, status, error, risk, tool, model, detail, createdAt) via `TraceService`; `GET /api/traces/{traceId}` reconstrói a execução em ordem cronológica (404 para trace inexistente). Falha de persistência de telemetria é registrada e nunca derruba a análise; nenhum evento contém segredos.
- **Métricas (terceiro sinal complementar):** `AnalysisMetrics` (Micrometer via Actuator) registra `analysis_duration`, `llm_calls`, `tool_calls`, `tool_errors`, `high_risk_changes`, `prompt_injection_count` e `validation_failures`, expostas em `/actuator/metrics`.
- `agent`: structlog em JSON usando o `trace_id` do cabeçalho (gera um próprio se ausente).
- Todos os sinais correlacionam-se pelo mesmo `trace_id` — fluxo, decisões, erros e latência de uma execução são investigáveis de ponta a ponta. Evidência: `docs/evidence/07-observability.png`.

## Resiliência

- **Política única (`ResilienceExecutor`)** para LLM, MCP, RAG, tools e cliente do agente: timeout configurável por integração, retry limitado (1 tentativa + 2 retries), backoff crescente limitado (`resilience.backoff-ms`, `resilience.max-backoff-ms`), cada tentativa registrada em log estruturado e em `TraceEvent` com número da tentativa e erro, e fallback explícito marcado como degradado quando o limite se esgota.
- **Falha crítica nunca escondida:** sem fallback, o executor propaga `ResilienceExhaustedException` com a causa; `AgentClient` converte em `AgentUnavailableException` → solicitação termina em estado `FAILED` com motivo estruturado (nunca sucesso simulado).
- `AgentClient` e o servidor MCP herdam o comportamento dos mesmos callbacks resilientes das tools.

## Testes e CI

- Java: `./mvnw test` (happy path, segurança/path traversal, structured output com ChatModel fake, detecção determinística de injeção, endpoint de aprovação 200/400/404/409, RAG com VectorStore mockado, memória com H2, MCP, controller `/api/agent`, reconstrução de trace com `TraceTest`/`TraceEndpointTest`, páginas web com `WebUiTest`/`TraceViewTest`/`WebE2ETest` — formulário válido/vazio, resultado HIGH com aprovação refletida, falha do agente, 404 amigável, escaping de HTML, eventos ordenados com documentos recuperados, Cenários A/B pelas páginas, cenários integrados de resiliência com `ResilienceTest`, métricas com `AnalysisMetricsTest`/`MetricsInstrumentationTest`, executor com `ResilienceExecutorTest`) e `./mvnw spotless:check`. Suíte inteira verde **sem chave de API**.
- Python (em `agent/`): `pytest` e `ruff check .` — cobre o grafo nos cenários do roadmap (happy path, high risk, prompt injection com avaliação obtida da aplicação, endpoint de segurança indisponível, tool failure, validation failure, max iteration), aplicação indisponível, paralelismo e propagação de trace_id, com client HTTP mockado.
- E2E: `docker compose up --build` + `python scripts/smoke_test.py` — Cenário A (desconto VIP 10%→15%) e Cenário B adversário (fixture com a frase oficial de injeção → evento de segurança persistido, risco HIGH permanece PENDING, decisão humana via endpoint), com chave configurada ou fluxo degradado marcado (`analysis_unavailable`) sem chave; `trace_id` correlacionado nos logs dos dois serviços.
- Demonstrações: `scripts/rag_demo.py` (RAG com fontes/scores), `scripts/mcp_tools_demo.py` (MCP tools/list + proteção de path), `scripts/fake_embeddings_server.py` (embeddings determinísticos locais só para demonstração), `scripts/generate_evidence.py` (gera as evidências 05/06 como placeholders até os screenshots reais da demonstração).
- CI: `.github/workflows/ci.yml` (jobs `spring`, `agent` e `e2e`).
