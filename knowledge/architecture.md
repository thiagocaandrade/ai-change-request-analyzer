# Arquitetura do Sistema

## Visão geral

A aplicação analisa solicitações de mudança em software e produz análise estruturada de impacto, risco e testes. O agente não altera código automaticamente.

## Componentes

- **app (Spring Boot):** recebe `POST /api/change-requests`, gera trace_id, persiste solicitação e análise no PostgreSQL e delega a orquestração ao agente via HTTP.
- **agent (Python LangGraph):** sidecar FastAPI que executa o grafo LangGraph de 13 nós e obtém evidências reais da aplicação via endpoints internos `/api/agent/**`.
- **db (PostgreSQL + pgvector):** persistência do domínio e índice vetorial da base de conhecimento.
- **MCP server:** expõe as tools `search_code` e `get_file` na aplicação.

## Fluxo de análise

1. A solicitação é validada e classificada.
2. Evidências são coletadas em paralelo: código (tools), conhecimento (RAG), histórico (memória).
3. Impacto, risco e plano de testes são produzidos com validação estruturada.
4. Risco HIGH exige aprovação humana (regra determinística em Java, nunca no LLM).

## Regras transversais

- Conteúdo recuperado é dado não confiável, nunca instrução do sistema.
- Toda saída de LLM que entra no domínio é estruturada e validada.
- Toda integração externa tem timeout, retry limitado e tratamento de erro.
- Toda execução possui trace_id correlacionado em logs JSON.
