## 1. Dependências e configuração

- [x] 1.1 Confirmar no BOM Spring AI 2.0.1 os artefatos `spring-ai-starter-vector-store-pgvector` e o starter MCP server webmvc, adicionar ao `pom.xml` e verificar resolução com `mvn dependency:resolve` (registrar a evidência da decisão no PR)
- [x] 1.2 Criar `knowledge/` com os 6 documentos (architecture, business-rules, discount-policy, coding-guidelines, testing-guidelines, security-policy), incluindo a regra VIP 10% do Cenário A em discount-policy; verificar presença e conteúdo dos 6 arquivos
- [x] 1.3 Adicionar envs de embedding (`.env.example` sem valores reais), expor no docker-compose e subir `agent.timeout-ms` default para 120000 em `application.yml`; verificar `mvn test` verde

## 2. Camada IA — prompts e structured output

- [x] 2.1 Criar os 4 prompts versionados em `resources/prompts/` (classification-v1, impact-analysis-v1, risk-analysis-v1, test-generation-v1) com placeholders e seção delimitada de DADOS NÃO CONFIÁVEIS; verificar com teste unitário que o PromptRegistry carrega por etapa e versão
- [x] 2.2 Implementar `AiAnalysisService` (ChatClient + BeanOutputConverter para records tipados, validação e retry máx. 2); verificar com ChatModel fake: saída válida aceita, inválida 1x recupera, inválida persistente → fallback determinístico marcado
- [x] 2.3 Garantir fallback sem chave (risco MEDIUM, rationale "analysis_unavailable") e que nenhuma saída inválida é persistida; verificar com `mvn test` rodando sem `AI_CHAT_API_KEY`

## 3. Tools

- [x] 3.1 Implementar `SearchCodeTool` e `GetFileTool` com raiz configurada, normalização de path e rejeição de traversal; verificar testes unitários: busca ok, `../` rejeitado, absoluto fora da raiz rejeitado, arquivo inexistente → erro estruturado
- [x] 3.2 Implementar `SearchChangeHistoryTool` e `GetRelatedTestsTool` sobre os repositórios existentes; verificar com H2: termo retorna análises anteriores com id, componente retorna testes relacionados, sem resultado → lista vazia
- [x] 3.3 Registrar as 4 tools no ChatClient com timeout, retry máx. 2 e logs com trace_id; verificar teste de tool failure: falha após retries → erro registrado e análise segue
- [x] 3.4 Configurar servidor MCP expondo `search_code` e `get_file` via os mesmos ToolCallbacks; verificar teste de listagem das tools MCP e de rejeição de path traversal via MCP

## 4. RAG pgvector

- [x] 4.1 Criar migration idempotente da tabela de vetores (extensão pgvector) e `KnowledgeIngestionService` (chunking por seção + embeddings + ingestão no startup só se vazio); verificar teste unitário de chunking e ingestão manual via docker compose sem duplicação no restart
- [x] 4.2 Implementar `RagService` (top-k default 4, threshold, metadata source/document_id/chunk_id/score, ordenação decrescente); verificar com VectorStore mockado: top-k e threshold respeitados, falha → lista vazia marcada

## 5. Memória

- [x] 5.1 Implementar `AnalysisMemoryService` com buscas ILIKE por termos, componente, regra de negócio e classificação nos repositórios existentes; verificar com H2: busca retorna CR anterior com id e resumo, sem resultado retorna vazio, falha retorna vazio marcado

## 6. Endpoints internos do agente

- [x] 6.1 Criar `AgentGatewayController` (`/api/agent/**`: classify, analyze-code, retrieve-knowledge, retrieve-history, assess-risk, generate-test-plan) com DTOs tipados e trace_id nos logs; verificar com MockMvc usando serviços mockados
- [x] 6.2 Garantir que respostas nunca contêm segredos e que conteúdo recuperado retorna com fonte e score; verificar com teste de segurança no controller

## 7. Sidecar Python

- [x] 7.1 Criar `agent/tools/client.py` (httpx, timeout, retry 2, header `X-Trace-Id`) com injeção via builder nos nós; verificar com `pytest agent/tests/test_nodes.py` usando client mockado
- [x] 7.2 Substituir os stubs dos nós (classify_request, analyze_code, retrieve_knowledge, retrieve_history, analyze_impact, assess_risk, generate_test_plan) por chamadas ao client com falha → `errors` + coleta vazia; verificar com `pytest` os 6 cenários do grafo, incluindo falha de tool e aplicação indisponível

## 8. E2E e evidência

- [x] 8.1 Estender `scripts/smoke_test.py`: com key, Cenário A completo (desconto VIP → classificação, código, RAG com fontes, histórico, risco, plano de testes); sem key, fluxo degradado marcado; verificar `docker compose up` + smoke + `mvn test` + `pytest`
- [x] 8.2 Registrar evidências `docs/evidence/03-rag.png` (busca com fontes/score) e `docs/evidence/04-mcp.png` (tools listadas no MCP) e atualizar README (tools, RAG, memória, prompts versionados); verificar presença dos arquivos e consistência do README
