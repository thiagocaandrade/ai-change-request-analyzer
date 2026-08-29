## Why

O projeto precisa satisfazer 15 critérios de avaliação até 31/08/26 e hoje só existe um scaffold Spring Boot vazio. Antes de qualquer capacidade de IA, é preciso um esqueleto executável de ponta a ponta — requisição entra, o agente roda um grafo LangGraph mínimo e uma resposta estruturada volta — com observabilidade e CI verdes. Sem isso, cada change seguinte carrega risco de integração acumulado; com isso, os critérios 6, 7 e 11 ganham base demonstrável desde o primeiro dia.

## What Changes

- `docker-compose.yml` com PostgreSQL+pgvector, serviço Spring Boot e serviço agente Python.
- `agent/`: serviço FastAPI + LangGraph com grafo mínimo linear de state tipado (nós determinísticos de stub), endpoints `POST /analyze` e `GET /health`, logs estruturados com trace_id.
- Spring Boot: controller `POST /requests`, geração de trace_id, cliente REST com timeout/retry para o agente, persistência JPA de requisição/status, logs JSON (logback encoder), driver PostgreSQL.
- `.env.example` sem valores reais; conexões e modelo configurados por variável de ambiente.
- `.github/workflows/ci.yml`: lint, testes e build dos dois serviços.

Decisões e evidências: LangGraph não possui SDK Java oficial (docs oficiais cobrem Python/JS), portanto o agente é um sidecar Python acessado por REST — contrato fino, sem fila, sem LangGraph Platform. PostgreSQL+pgvector em Docker segue a stack obrigatória do contexto do projeto (imagem `pgvector/pgvector:pg16`, documentada). Spring Boot 4.1.1 é mantido porque já está no `pom.xml` existente; a compatibilidade com Spring AI será verificada na change que o introduzir. Trace_id gerado no Spring e propagado por header é a base de correlação dos dois sinais de observabilidade exigidos.

## Capabilities

### New Capabilities
- `agent-runtime`: serviço agente Python (FastAPI + LangGraph) com grafo mínimo, state tipado, endpoints de análise e health, logs estruturados com trace_id.
- `request-pipeline`: pipeline Spring Boot que recebe solicitação, gera trace_id, delega ao agente com timeout/retry, persiste e expõe status e resposta estruturada.
- `platform-foundation`: ambiente de execução local reprodutível — docker compose (Postgres+pgvector + serviços), configuração exclusivamente via variáveis de ambiente e `.env.example`.
- `ci-pipeline`: workflow GitHub Actions com lint, testes e build dos serviços Java e Python.

### Modified Capabilities

Nenhuma — o repo não possui specs existentes.

## Non-goals

- Sem chamadas a LLM, RAG, MCP tools, checkpointer persistente ou memória.
- Sem aprovação humana, cálculo de risco, análise de impacto ou plano de testes.
- Sem n8n, sem UI completa (Thymeleaf permanece mínimo), sem sample-project.
- Sem múltiplos agentes, filas, cache, métricas Prometheus ou tracing distribuído.

## Impact

- Novos: `agent/` (Python 3.12, FastAPI, LangGraph, structlog), `docker-compose.yml`, `.env.example`, `.github/workflows/ci.yml`.
- Modificados: `pom.xml` (driver PostgreSQL, logstash encoder), `application.yml`, `src/main/java` (controller, cliente REST, entidade JPA, filter de trace_id).
- Nenhuma API externa é assumida nesta change.
