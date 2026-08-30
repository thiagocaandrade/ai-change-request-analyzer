# Design — ai-rag-memory-tools

## Context

O grafo LangGraph de 13 nós (change 03) roda no sidecar Python com nós determinísticos stub — `classify_request`, `analyze_code`, `retrieve_knowledge`, `retrieve_history`, `assess_risk` e `generate_test_plan` emitem constantes (evidência: `agent/graph/nodes.py`, linhas 58–159). O Spring Boot 4.1.1 com Spring AI 2.0.1 (`pom.xml`) já possui `spring-ai-starter-model-openai` e um `ChatClient` condicionado à presença de `ai.chat.api-key` (`AiConfig.java`) — a aplicação sobe sem chave. O Postgres roda na imagem `pgvector/pgvector:pg16` (`docker-compose.yml`, linha 3) e o domínio (tabelas `change_request`, `change_analysis`, `impact_finding`, `test_recommendation`, `approval`) já é persistido via JPA. O `AgentClient` Java chama `POST /analyze` com timeout configurável (default 10s) e 3 tentativas. O agente Python não tem banco nem LLM — só o grafo. Motivação: ver proposal.md — Why.

## Goals / Non-Goals

**Goals:**
- Plugar IA, tools, RAG e memória reais sem alterar a topologia do grafo nem as regras determinísticas Java (`RiskPolicy`).
- Suíte inteira verde sem chave de API (fallback degradado marcado), como já ocorre hoje.
- Cada evidência recuperada rastreável: fonte, id, score, trace_id.

**Non-Goals:**
- `SecurityAssessment` no domínio e endpoint de aprovação (change 05); prompt `security-analysis-v1` idem.
- Métricas, segundo sinal observável, análise de logs e anomalias (changes 06/09) — aqui apenas timeout/retry básicos de integração.
- Embeddings locais (onnx), banco dedicado ao RAG ou infra extra.

## Decisions

**D1 — LLM, tools, RAG e memória vivem no Java (Spring AI); o Python mantém só orquestração.**
Os nós do grafo chamam a aplicação via HTTP. Alternativa (implementar LLM/tools no Python com langchain) — rejeitada: a stack obrigatória é Spring AI; validação de saída e regras determinísticas permanecem na JVM; o sidecar continua testável sem chaves.

**D2 — Contrato REST interno `/api/agent/**`.**
Endpoints: `classify`, `analyze-code`, `retrieve-knowledge`, `retrieve-history`, `assess-risk`, `generate-test-plan`, com DTOs record Java e respostas tipadas. O sidecar usa httpx com timeout (10s) e retry limitado (2), cabeçalho `X-Trace-Id`. Alternativa (inverter o fluxo: Java chamar nós do grafo) — rejeitada: contraria a arquitetura decidida (AGENTS.md, decisão 1).

**D3 — Prompts versionados com seção de dados delimitada.**
`resources/prompts/<etapa>-v1.txt`, carregados por `PromptRegistry` (id de etapa + versão); placeholders `{change_text}` e `{evidence}`. A evidência recuperada entra numa seção delimitada "DADOS NÃO CONFIÁVEIS" dentro do user message — nunca no system prompt (spec `ai-capabilities`). Alternativa (prompts no código) — rejeitada: regra de prompts versionados.

**D4 — Structured output com validação e fallback determinístico.**
`BeanOutputConverter` para records tipados + validação jakarta; inválido → retry (máx. 2); esgotado → fallback determinístico marcado (`rationale: "analysis_unavailable"`, risco MEDIUM). O LLM sugere risco; `RiskPolicy` (Java) continua aplicando a regra HIGH ⇒ aprovação obrigatória. Alternativa (aceitar texto livre) — rejeitada: regra de structured output + validação.

**D5 — Tools como ToolCallbacks Spring AI, executadas sempre na JVM.**
`search_code`, `get_file` (raiz configurada; `Path.normalize` + verificação de prefixo; rejeição de `..`, absolutos e vazios; sem `ProcessBuilder`/`Runtime.exec` em nenhum ponto), `search_change_history` e `get_related_tests` sobre os repositórios existentes. Timeout por tool, retry máx. 2, logs com trace_id. MCP: servidor Spring AI (`spring-ai-starter-mcp-server-webmvc`; confirmar artefato no BOM 2.0.1 na tarefa 1.1) expondo `search_code`/`get_file` via os mesmos ToolCallbacks. Alternativa (tools no Python) — rejeitada: duplicaria proteções de path/DB fora da stack.

**D6 — RAG com `spring-ai-starter-vector-store-pgvector`.**
Tabela de vetores criada por migration idempotente (não ddl-auto). Ingestão no startup somente se a base estiver vazia; chunking por seção/parágrafo (~400–600 tokens, sem overlap); metadata `{source, document_id, chunk_id}` + score retornado pela busca. Busca top-k (default 4) com threshold de score configurável. Embedding model via `OpenAiEmbeddingModel` condicionado a env (`ai.embedding.api-key` etc.); sem key de embedding, RAG desativa e a análise segue degradada. Alternativa (vetorizar histórico também) — rejeitada: duplica infra; o histórico fica textual (D7).

**D7 — Memória: busca determinística ILIKE.**
`AnalysisMemoryService` consulta `change_request`/`change_analysis`/`impact_finding` por termos, componente, regra de negócio e classificação, retornando id + resumo ("semelhante à CR-XXX"). Alternativa (embeddings do histórico) — rejeitada: RAG já cobre semântica dos docs; ILIKE resolve o Cenário A com menos infra.

**D8 — Timeout Java→agente.**
`agent.timeout-ms` default sobe para 120000 (análise real passa a incluir chamadas a LLM). O requisito do `request-pipeline` ("timeout ... configurado") permanece válido — sem delta de spec.

**D9 — Sidecar: client injetável, topologia intacta.**
Novo `agent/tools/client.py` (httpx, timeout, retry, `X-Trace-Id`); `builder.py` injeta o client nos nós (fábrica/contexto); testes pytest mockam o client. Falha do client → entrada em `errors` + coleta vazia — o grafo nunca interrompe. Alternativa (client global) — rejeitada: não testável.

**D10 — Estratégia de teste.**
Unitários Java: tools/path traversal, `AiAnalysisService` com ChatModel fake, RAG com `VectorStore` mockado (pgvector não roda em H2), memória com H2. pytest com client mockado (6 cenários do grafo). E2E via docker compose: `scripts/smoke_test.py` estendido para o Cenário A com key configurada e fluxo degradado sem key.

## Risks / Trade-offs

- **[R1] Artefatos MCP podem variar no BOM 2.0.1** → tarefa 1.1 confirma dependências antes de codar (regra: não assumir APIs sem verificar).
- **[R2] pgvector indisponível em testes unitários (H2)** → VectorStore mockado; integração real apenas em docker compose/E2E.
- **[R3] Latência do LLM estoura timeouts** → timeouts configuráveis (D8) e fallback determinístico (D4) garantem resposta; smoke mede a duração.
- **[R4] LLM instável ou alucinando** → structured output + validação + retry + fallback; risco final sempre arbitrado no Java.
- **[R5] Custo de tokens** → top-k limitado, chunking controlado, uma chamada por etapa (sem loop livre de agente).
- **[R6] Ingestão no startup atrasa a subida** → só quando a base está vazia; health com `start_period` já cobre (`docker-compose.yml`, linha 52).

## Migration Plan

Sem migração de dados: a tabela de vetores é nova (migration idempotente) e `knowledge/` é aditivo. Ordem de adoção: dependências/config → prompts + camada IA → tools + MCP → RAG → memória → endpoints internos → sidecar → testes/E2E → evidência. Rollback: reverter o commit; a tabela de vetores pode ser descartada sem afetar o domínio.
