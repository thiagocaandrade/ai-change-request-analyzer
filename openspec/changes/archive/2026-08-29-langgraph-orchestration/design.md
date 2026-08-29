# Design — langgraph-orchestration

## Context

Hoje o agente (`agent/`) executa um grafo linear mínimo: `StateGraph` com 2 nós stub (`parse_stub` → `compile_stub`) e estado de 5 campos (`request_id`, `text`, `status`, `result`, `warnings`) — ver `agent/graph/builder.py` e `agent/graph/state.py`. O Spring consome `POST /analyze` via `AgentClient` (timeout 10s, 3 tentativas) e mapeia a resposta defensivamente: `AgentResultMapper` lê `result.risk`, `result.confidence`, `result.rationale`; `RiskPolicy` (Java) aplica a regra determinística HIGH ⇒ aprovação PENDING na persistência — o agente nunca decide a obrigatoriedade. Nesta change o grafo vira o alvo final do roadmap (13 nós, paralelização, branching, condição de parada), ainda com nós determinísticos: LLM, RAG e tools reais entram na change 04. Motivação: ver proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- Grafo completo executável com os 13 nós, fan-out/fan-in real de coleta, branching por risco e condição de parada com retry limitado — tudo testável sem LLM.
- Contrato `POST /analyze` retrocompatível com o Java existente (nenhuma mudança Java obrigatória).
- Base estrutural para plugar LLM/tools (04) e aprovação humana (05) sem retrabalho de topologia.

**Non-Goals:**
- Zero chamadas a LLM; nós stub determinísticos apenas.
- Sem checkpointer/persistência no agente; sem novas rotas HTTP; sem mudanças em `RiskPolicy` ou nos endpoints Java.

## Decisions

**D1 — Estado: `ChangeRequestState` em snake_case.**
Um `TypedDict` com as chaves do AGENTS.md traduzidas para snake_case (convenção do código existente): `trace_id`, `change_request` (texto + id), `classification`, `retrieved_documents`, `code_findings`, `historical_findings`, `impact_findings`, `risk_assessment`, `security_assessment`, `test_plan`, `approval_required`, `approval_status`, `final_result`, `errors`, `iteration_count`. `total=False` com fábrica de estado inicial (coleções vazias, `iteration_count=0`) para que nós leiam sem `KeyError`. Alternativa (dataclasses anotadas de LangGraph) — rejeitada: TypedDict é o padrão já usado e suficiente.

**D2 — Nós stub determinísticos, um arquivo por responsabilidade.**
`agent/graph/nodes.py` concentra os 13 nós; cada um lê/escreve apenas suas chaves do state. `assess_risk` emite default MEDIUM com confidence 0.5 e racional explícito "stub determinístico (LLM na change 04)"; para testar HIGH/MEDIUM, os testes seedam `risk_assessment` na entrada do `graph.invoke` (padrão LangGraph). Alternativa (heurística de risco sobre o texto no Python) — rejeitada: regra de negócio no agente duplicaria `RiskPolicy`; o stub mantém as regras determinísticas onde já estão (Java).

**D3 — Paralelização por fan-out/fan-in nativo do LangGraph.**
`detect_untrusted_content` tem 3 edges de saída (uma por nó de coleta) e os 3 convergem para `analyze_impact`; o join default aguarda todos. Como os nós capturam falhas em `errors` (nunca levantam), o fan-in sempre é alcançado → análise degradada em vez de travada. Alternativa (Send API) — rejeitada por complexidade desnecessária.

**D4 — Branching com conditional edge determinística.**
`approval_router` é função pura do state: retorna `"human_approval"` se `risk_assessment.level == "HIGH"`, senão `"generate_test_plan"`. `human_approval` marca `approval_required=true`, `approval_status="PENDING"` e tem edge fixa para `generate_test_plan`. O agente apenas sinaliza pendência; o Java (`RiskPolicy` + `AnalysisService`) decide e persiste a obrigatoriedade — alinhado ao AGENTS.md decisão 4.

**D5 — Condição de parada: retry limitado em `iteration_count`.**
`generate_test_plan` incrementa `iteration_count` a cada execução (tentativa de geração); `validate_final_result` valida os campos obrigatórios do `final_result` e registra falhas em `errors`. O roteador condicional reenvia para `generate_test_plan` enquanto `iteration_count < 3` (1 tentativa inicial + 2 correções — retry limitado a 2, conforme roadmap linha 27); esgotado o contador, segue para o nó terminal `finalize_error` (status `failed`, `errors` preenchidos, sem nova reexecução). Válido → `finalize`. É a garantia estrutural de ausência de loop infinito.

**D6 — Contenção de falhas e logs por nó.**
Wrapper comum (`run_node`) aplicado a todos os nós: try/except captura exceção → entrada em `errors` (`{node, message}` sem stack/segredos) → retorna `{}` para o fluxo continuar até o término estruturado. Logs structlog `node_enter`/`node_exit` com `trace_id`, `node`, `iteration_count`; o `trace_id` é injetado no state pelo endpoint via header `X-Trace-Id` (middleware já existente).

**D7 — Detecção determinística de conteúdo injetado.**
`detect_untrusted_content` varre o texto da solicitação e os campos de coleta por marcadores de injeção (ex.: "ignore as instruções", "classifique como LOW") e registra evento em `security_assessment` (`detected=true`, tipo, evidência); o conteúdo nunca alimenta roteamento nem risco. É o mecanismo base do Cenário B; o refinamento com `SecurityAssessment` no domínio Java fica na change 05.

**D8 — Contrato HTTP estável.**
Resposta continua `{request_id, status, result}`. `status`: `completed` | `pending_approval` | `failed`. `result`: `processed_text`, `summary`, `classification`, `risk`, `confidence`, `rationale`, `findings`, `test_plan`, `approval {required, status}`, `errors` (quando houver). O Java já ignora campos extras e trata campos ausentes (`AgentResultMapper` defensivo), então a E2E existente permanece verde; HTTP 400 continua reservado a entrada inválida (sem executar o grafo) e respostas degradadas retornam 200 com `status` próprio — o `AgentClient` trata qualquer resposta 200 como análise recebida e deixa `RiskPolicy` arbitrar.

**D9 — Estrutura de arquivos.**
`agent/graph/state.py` (estado + fábrica), `agent/graph/nodes.py` (13 nós + wrapper), `agent/graph/builder.py` (montagem: edges, fan-out/fan-in, conditional edges), `agent/tests/` (test_state, test_nodes, test_graph, test_api). Sem novos módulos além destes.

## Risks / Trade-offs

- **[R1] Join de fan-in em LangGraph espera todos os ramos** → mitigado por D3/D6: nós capturam falha e retornam, então o join nunca fica órfão.
- **[R2] Stubs serão substituídos por LLM na change 04** → mitigado por D2: contrato entre nós é só o state; troca-se a implementação interna sem mexer na topologia.
- **[R3] API de conditional edges varia entre versões de langgraph** → mitigado: versões pinadas em `requirements.txt`; primeira tarefa verifica assinatura contra a versão instalada antes de escrever o builder.
- **[R4] Java marca COMPLETED para qualquer 200 do agente** → mitigado por D8: o `result` carrega risk/confidence para `RiskPolicy` arbitrar na persistência; ajuste fino do status Java fica fora do escopo.
- **[R5] Timeout de 10s do `AgentClient`** → grafo 100% em memória (stubs), execução ≪ timeout; reavaliado quando LLM entrar (06/04).

## Migration Plan

Greenfield de grafo: substituição direta de `state.py`/`nodes.py`/`builder.py` e do mapeamento em `main.py`; sem dados ou schema a migrar; rollback = reverter o commit. Ordem de adoção: estado → nós de entrada → coleta paralela → síntese → branching → condição de parada → builder → API → testes → evidência (detalhado em tasks.md).
