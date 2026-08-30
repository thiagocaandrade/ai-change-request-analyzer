## Why

Os 13 nós do grafo ainda são stubs determinísticos ("LLM na change 04" — evidência: `agent/graph/nodes.py`, linhas 58–159), então a aplicação não produz análise real de impacto, risco e testes nem cumpre o objetivo central. Sem IA, tools, RAG e memória, não há conteúdo recuperado real para o Cenário B e a change 05 (segurança) não tem o que testar. É a change 04 do roadmap (FASE 7–11), na ordem obrigatória.

## What Changes

- Camada IA no Java (Spring AI): 4 prompts versionados em `resources/prompts/*-v1.txt` (classification, impact-analysis, risk-analysis, test-generation) com structured output convertido em objetos tipados, validação e retry limitado; saída inválida nunca é persistida.
- 4 tools no Java: `search_code`, `get_file`, `search_change_history`, `get_related_tests` — validação de entrada, sem shell, sem path traversal, sem acesso fora do repositório configurado; `search_code`/`get_file` também expostas via MCP (servidor MCP Spring AI).
- RAG pgvector: `knowledge/` com 6 docs (architecture, business-rules, discount-policy, coding-guidelines, testing-guidelines, security-policy); ingestão → chunking → embeddings → pgvector → busca por similaridade com metadata (source, document id, chunk id, score), limite de documentos e fontes no resultado. Conteúdo recuperado é sempre dado, nunca instrução.
- Memória persistente: busca de análises anteriores por termos semelhantes, componente, regra de negócio e classificação nas tabelas existentes.
- Sidecar Python: nós do grafo passam a obter evidência real da aplicação via HTTP (timeout/retry), mantendo a topologia de 13 nós; falha de integração degrada a análise sem interromper o grafo.

## Capabilities

### New Capabilities

- `ai-capabilities`: chamadas a LLM via Spring AI com prompts versionados, structured output validado e retry limitado, sem segredos em log.
- `analysis-tools`: as 4 tools com validação de entrada, proteções de segurança e exposição via MCP.
- `rag-knowledge`: ingestão e busca semântica dos 6 documentos de `knowledge/` via pgvector, com metadata e limite.
- `analysis-memory`: busca de análises anteriores por semelhança, componente, regra e classificação.

### Modified Capabilities

- `agent-runtime`: o requisito "Análise completa via LangGraph" passa a exigir resultado produzido com evidência real (LLM, tools, RAG, histórico) via aplicação — não mais stubs determinísticos.
- `agent-orchestration`: "Coleta paralela de evidências" passa a obter evidências da aplicação via HTTP com timeout/retry e degradação em falha.

## Non-goals

- Sem `security-analysis-v1.txt`, `SecurityAssessment` no domínio e endpoint de aprovação (change 05); a detecção determinística de injeção existente permanece como está.
- Sem métricas/segundo sinal observável, análise de logs com IA e anomalias (changes 06/09); aqui há apenas timeout/retry básicos de integração.
- Sem frontend, n8n, code review com IA e refinamento v1→v2 de prompts (changes 07–10).
- Sem alterar regras determinísticas: `RiskPolicy` (Java) segue decidindo obrigatoriedade de aprovação; o LLM apenas sugere risco.

## Impact

- Maven: `spring-ai-starter-vector-store-pgvector` e starter MCP server (verificar artefatos no BOM 2.0.1 antes de codar).
- Java: novos pacotes `ai/`, `tools/`, `rag/`, `mcp/` + controller interno `/api/agent/**`; `application.yml`/docker-compose (envs de embedding, timeout do agente).
- Python: `agent/graph/nodes.py` (nós reais), novo `agent/tools/client.py` (httpx com timeout/retry/trace_id); `builder.py` sem mudança de topologia.
- Dados: tabela de vetores nova via migration idempotente; `knowledge/` criado com 6 docs.
- Testes: unitários (tools/path traversal, LLM com modelo fake, RAG com VectorStore mock), integração e E2E (`scripts/smoke_test.py` estendido).
