# Design — foundation

## Context

O repositório contém apenas o scaffold Spring Boot 4.1.1 (Java 21, Maven, JPA, Thymeleaf, WebMVC) com uma única classe `AnalyzerApplication` e nenhum driver de banco, configuração de ambiente, Docker ou CI. Motivação: ver `proposal.md - Why`. Restrições que moldam o design: stack obrigatória do contexto (PostgreSQL+pgvector, LangGraph, Spring AI, Docker Compose, GitHub Actions), princípio de simplicidade, e o fato de LangGraph não possuir SDK Java oficial (docs cobrem Python/JS).

## Goals / Non-Goals

**Goals:**
- Caminho executável de ponta a ponta: `POST /requests` (Spring) → `POST /analyze` (agente LangGraph) → resposta estruturada persistida, tudo subindo com `docker compose up`.
- trace_id e logs JSON correlacionados nos dois serviços desde o primeiro dia.
- CI verde (lint, testes, build) antes de qualquer feature.

**Non-Goals:**
- O agente permanece sem estado: nenhum checkpointer ou acesso ao Postgres nesta change (persistência é responsabilidade do Spring).
- Zero chamadas a LLM; Spring AI entra apenas como esqueleto de configuração (dependência + env vars), sem uso.
- Sem RAG, MCP, aprovação humana, n8n, sample-project ou UI além de páginas mínimas de health/status.

## Decisions

**D1 — Arquitetura de 2 serviços + banco (Spring, sidecar Python, Postgres), orquestrados por docker compose.**
Alternativas: processo único (inviável — sem SDK Java de LangGraph); LangGraph Platform (infra extra, rejeitada por simplicidade). Contrato REST fino, síncrono — o grafo desta change é linear e rápido, então não há fila nem polling.

**D2 — Grafo mínimo em `agent/` (FastAPI + LangGraph).**
`StateGraph` linear com estado tipado `AnalysisState {request_id, text, status, result}` e dois nós determinísticos: `parse_stub` (validação/normalização) e `compile_stub` (monta a saída estruturada), com edges explícitas `START → parse_stub → compile_stub → END`. Sem nós de LLM. Motivo: prova a mecânica de state/nodes/edges agora, extensível pelas próximas changes sem retrabalho. Validação de entrada via Pydantic; erro 400 sem executar o grafo.

**D3 — Contrato REST Spring↔agente.**
`POST /analyze` recebe `{request_id, text}` e devolve `{request_id, status, result}`. Spring usa `RestClient` com timeout de 10s e retry limitado (3 tentativas, backoff) implementado no `AgentClient` — sem dependência de spring-retry nesta change. Cabeçalho `X-Trace-Id` propagado nas duas direções; o agente gera um próprio se ausente. Validação dupla: Bean Validation no Spring, Pydantic no agente.

**D4 — Observabilidade: trace_id + logs JSON, sem OpenTelemetry.**
Spring: UUID gerado por request no filter, colocado no MDC e persistido; logback com `logstash-logback-encoder`. Agente: structlog em JSON. Alternativa considerada (OpenTelemetry SDK / tracing distribuído) — rejeitada: logs estruturados + correlação por trace_id atendem a rubrica com custo muito menor.

**D5 — Persistência no Spring (JPA), agente stateless.**
Entidade `ChangeRequest` (`id` UUID, `text`, `status` PENDING/COMPLETED/FAILED, `traceId`, `result` JSON, timestamps). `ddl-auto: update` em dev; testes de integração com H2 (pgvector ainda não é usado — será trocado por Testcontainers na change de RAG). Flyway fica para quando o modelo amadurecer.

**D6 — Esqueleto Spring AI.**
Adicionar BOM Spring AI 1.x GA + starter de modelo OpenAI-compatível; configuração exclusivamente por env (`AI_CHAT_MODEL`, `AI_CHAT_API_KEY`, `AI_CHAT_BASE_URL`) com default vazio e nenhum bean que exija chave no startup. Compatibilidade com Boot 4.1.1 é verificada na primeira tarefa; se a versão GA do Spring AI não suportar, pina-se Spring Boot 3.5.x (fallback documentado no risco R1).

**D7 — Configuração de ambiente centralizada.**
`.env.example` único na raiz (sem valores reais, `.gitignore` cobre `.env`); docker compose injeta as variáveis; Spring lê `SPRING_DATASOURCE_*` e `AGENT_URL`.

**D8 — Docker Compose com 3 serviços e healthchecks.**
`db` (imagem `pgvector/pgvector:pg16`, volume, healthcheck `pg_isready`), `app` (Dockerfile multi-stage: Maven build → JRE 21), `agent` (`python:3.12-slim` + `requirements.txt` com versões pinadas). `depends_on` condicionado aos healthchecks.

**D9 — CI em um workflow.**
Jobs: `spring` (setup-java 21 Temurin → `mvn test` → `mvn package`), `agent` (setup-python 3.12 → `pip install -r requirements.txt` → `ruff check` → `pytest`). Lint Java: Spotless com google-java-format; lint Python: ruff. Dispara em push para `develop`/`main` e em PRs.

**D10 — Estrutura de pacotes.**
Spring (`com.ai.change.request.analyzer`): `web` (controllers + `GlobalExceptionHandler`), `api` (`AgentClient` + DTOs), `domain` (entidade, repositório, enum de status), `config` (filter de trace_id, logging, RestClient). Agente: `agent/app` (FastAPI), `agent/graph` (state, nodes, builder), `agent/logging`.

## Risks / Trade-offs

- **[R1] Compatibilidade Spring Boot 4.1.1 × Spring AI GA não verificada** → spike na primeira tarefa; fallback decidido: pin Boot 3.5.x.
- **[R2] Docker no Windows pode variar** → CI valida o mesmo caminho em runner Linux; compose exige apenas pull da imagem pgvector.
- **[R3] H2 vs Postgres em testes de integração** → escopo de teste pequeno nesta change; migração para Testcontainers quando pgvector entrar.
- **[R4] Retry manual no AgentClient** → 3 tentativas em poucas linhas são suficientes hoje; reavaliar quando houver mais integrações.
- **[R5] Chamada síncrona ao agente** → grafo linear é rápido; fluxo assíncrono com polling entra junto com o interrupt de aprovação (change futura).
- **[R6] `ddl-auto: update`** → aceitável no escopo acadêmico; Flyway quando o schema estabilizar.

## Migration Plan

Projeto greenfield, sem dados ou usuários existentes — rollback equivale a reverter o merge. Sequência de adoção: esqueleto dos serviços → contrato REST → compose → CI → documentação (detalhada em tasks.md).
