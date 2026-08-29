## Why

A foundation entregou um grafo LangGraph mínimo e linear com dois nós stub (`parse_stub` → `compile_stub`, evidência: `agent/graph/builder.py`), suficiente para provar state/nodes/edges, mas sem paralelização, branching ou condição de parada — três demonstrações exigidas pela rubrica acadêmica (docs/roadmap.md, linhas 22–28). Sem o grafo completo, as changes 04 e 05 (IA/RAG/tools e segurança/aprovação) não têm nós onde plugar LLM, coleta de evidências e checkpoint humano. É a change 03 do roadmap (FASE 6), na ordem obrigatória.

## What Changes

- Substituir o grafo linear por um `StateGraph` completo com 13 nós na ordem do roadmap: `validate_request`, `classify_request`, `detect_untrusted_content`, `analyze_code`, `retrieve_knowledge`, `retrieve_history`, `analyze_impact`, `assess_risk`, `approval_router`, `human_approval`, `generate_test_plan`, `validate_final_result`, `finalize`.
- Estado compartilhado completo (`ChangeRequestState`) com os campos do AGENTS.md: traceId, changeRequest, classification, retrievedDocuments, codeFindings, historicalFindings, impactFindings, riskAssessment, securityAssessment, testPlan, approvalRequired, approvalStatus, finalResult, errors, iterationCount.
- Paralelização real: `analyze_code` ‖ `retrieve_knowledge` ‖ `retrieve_history` (fan-out/fan-in).
- Branching real: `approval_router` → HIGH: `human_approval`; LOW/MEDIUM: `generate_test_plan`.
- Condição de parada: `validate_final_result` → válido: `finalize`; inválido: retry com `iterationCount` limitado (máx. 2) → término com erro estruturado.
- Nós determinísticos stub nesta change (LLM entra na change 04); falhas capturadas no state (`errors`), sem exceções não tratadas e sem segredos em log.
- Detecção determinística de conteúdo injetado com registro de security event — mecanismo base do Cenário B.
- `POST /analyze` enriquece o resultado (risk, confidence, rationale, approval, errors) mantendo o contrato `{request_id, status, result}` já consumido pelo Java.
- Testes do grafo: happy path, high risk, prompt injection, tool failure, validation failure, max iteration.

## Capabilities

### New Capabilities

- `agent-orchestration`: grafo LangGraph completo — estado compartilhado, 13 nós, paralelização, branching, condição de parada, contenção de falhas e tratamento de conteúdo não confiável.

### Modified Capabilities

- `agent-runtime`: o requisito "Análise mínima via LangGraph" passa a "Análise completa via LangGraph" — o mesmo endpoint `POST /analyze` executa o grafo completo e devolve resultado estruturado enriquecido.

## Non-goals

- Sem chamadas a LLM, Spring AI, RAG, MCP ou tools reais (change 04); os nós de coleta/risco são stubs determinísticos.
- Sem endpoint de decisão humana e sem `SecurityAssessment` no domínio Java (change 05).
- Sem checkpointer/persistência no agente e sem mudança nas regras determinísticas Java — `RiskPolicy` continua o árbitro final da regra HIGH→aprovação.
- Sem mudanças na API Java além da compatibilidade já existente do contrato.

## Impact

- Modificados: `agent/graph/` (state, nodes, builder), `agent/app/main.py` (mapeamento do resultado final), `agent/tests/` (novos testes de grafo e ajuste dos existentes).
- Java: nenhuma mudança obrigatória — `AgentResultMapper` é defensivo e continua consumindo o mesmo contrato; `RiskPolicy` segue aplicando a regra determinística na persistência.
- CI: mesma suíte (`pytest` + `mvn test`), agora cobrindo os seis cenários do grafo.
