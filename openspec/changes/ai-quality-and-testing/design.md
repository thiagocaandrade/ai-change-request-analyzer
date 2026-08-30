# Design: ai-quality-and-testing

## Context

O sistema já possui a infraestrutura necessária para esta change (ver `openspec/specs/ai-capabilities`, `agent-runtime`, `rag-knowledge`):

- `AiAnalysisService` executa etapas cognitivas com prompts versionados, structured output (`BeanOutputConverter`), validação Bean Validation, retry limitado com backoff (`ResilienceExecutor`), fallback determinístico marcado (`degraded=true`), trace events e métricas.
- O grafo LangGraph (sidecar Python) orquestra; as etapas de IA rodam em Java, expostas por `AgentGatewayController` (`/classify`, `/analyze-impact`, `/assess-risk`, `/generate-test-plan`, `/analyze-security`). O nó `generate_test_plan` chama o endpoint Java e monta o `final_result`.
- `RagService.search(query)` recupera conhecimento (incluindo `coding-guidelines` e `business-rules`) como dado não confiável; `AnalysisService.registerAnalysis` persiste a análise (findings, risco, recomendações, aprovação).
- Já existem `TestRecommendation` (component, description, priority) e o prompt `test-generation-v1.txt`.

Motivação: ver `proposal.md - Why`. Requisitos: ver specs das capabilities `ai-code-review`, `ai-test-generation`, `risk-based-testing`, `analysis-memory`, `web-ui`.

## Goals / Non-Goals

**Goals:**

- Code review com IA da alteração (descrição + findings de impacto) apoiada em guidelines/regras de negócio recuperadas, com findings tipados e registro de prompt/resultado/risco.
- Priorização determinística de testes via matriz Impact × Likelihood, cobrindo as 4 categorias obrigatórias, com pelo menos um teste priorizado e justificado por análise.
- Refinamento limitado (máx. 2 iterações) e registrado de recomendações inválidas.
- Exibição do QA na página de resultado, persistência dos registros QA e evidência E2E (`09-ai-code-review.png`).

**Non-Goals:**

- Não criar nó novo no grafo LangGraph nem alterar o contrato de orquestração (13 nós); QA roda dentro do estágio `generate-test-plan` existente.
- Não alterar prompts v1→v2 (change 10); não executar/gravar código de teste no repositório.
- Não adicionar endpoints públicos novos além dos DTOs existentes enriquecidos.

## Decisions

### 1. QA integrado ao estágio `generate-test-plan` (sem novo nó no grafo)

O endpoint Java `POST /generate-test-plan` passa a orquestrar: (a) recuperar guidelines/regras de negócio via `RagService`; (b) executar code review (novo estágio `CODE_REVIEW`); (c) aplicar a matriz de risco determinística; (d) gerar/refinar recomendações com os findings de QA como evidência; (e) responder com bloco `qa` (findings + recomendações priorizadas + registro).

- *Alternativa considerada:* novo nó `qa_review` no grafo Python (14 nós). Rejeitada: violaria o alvo fixo de 13 nós do roadmap e duplicaria orquestração; o ganho de paralelização não compensa (review depende do impacto, que já é sequencial).

### 2. Reuso integral da infraestrutura de IA existente

Novo `AnalysisStage.CODE_REVIEW` + prompt versionado `resources/prompts/code-review-v1.txt` + DTO tipado `AiResults.CodeReviewResult` (findings: component, description, severity, source) processado pelo mesmo `AiAnalysisService.generate()`: structured output, validação, retry máx. 2 com backoff, fallback determinístico `degraded`, trace events e métricas.

- *Alternativa considerada:* novo serviço paralelo de LLM. Rejeitada: duplicaria timeout/retry/fallback e métricas já garantidos por `AiAnalysisService`.

### 3. Matriz de risco 100% determinística no Java

`qa/RiskMatrixService`: dimensões Impact (LOW/MEDIUM/HIGH) × Likelihood (LOW/MEDIUM/HIGH) → prioridade via regra fixa (ex.: HIGH×HIGH → HIGH; HIGH×MEDIUM → HIGH; MEDIUM×MEDIUM → MEDIUM; demais → LOW). O modelo sugere impacto/probabilidade por categoria no code review; a aplicação normaliza sugestões fora de faixa para o padrão e calcula a prioridade — a IA nunca decide a prioridade final.

Categorias obrigatórias avaliadas em toda análise: prompt injection, acesso não autorizado às tools, classificação incorreta de HIGH/LOW, regressão de regra de negócio financeira.

### 4. Refinamento limitado e registrado de testes

Após a geração, recomendações inválidas (ex.: descrição vazia) disparam até 2 iterações de refinamento reutilizando o estágio `TEST_GENERATION` com feedback ("refine apenas o item inválido"); cada iteração vira trace event e é registrada no `QaReviewRecord`. Esgotado o limite, a recomendação permanece marcada como não refinada.

- *Alternativa considerada:* prompt dedicado `test-refinement-v1`. Rejeitada: o retry com feedback no mesmo prompt satisfaz o requisito com menos superfície; um prompt dedicado fica para a change 10 (refinamento v1→v2 com evidência).

### 5. Persistência: registros QA + justificativa nas recomendações

- Novas entidades: `QaReviewRecord` (id, analysis/request, stage, promptVersion, resultJson, degraded, traceId, createdAt) e `QaFinding` (id, reviewRecord, component, description, severity, source).
- `TestRecommendation` ganha `priorityJustification` (text) e `riskCategory` (varchar).
- O `QaReviewRecord` é persistido no próprio gateway (como os security events), usando o `requestId` do payload; findings e recomendações fluem pelo payload normal do `final_result` → `CreateAnalysisRequest` → `AnalysisService`.

### 6. Fluxo de dados do QA até a persistência e a web

Python: `generate_test_plan` node repassa o bloco `qa` da resposta do Java para `final_result.qa` (mudança mínima, sem lógica nova). Java: `AgentResponse`/`CreateAnalysisRequest` ganham `qa` (findings + recomendações com justification/riskCategory). Web: `result.html` ganha seção QA renderizada com `th:text` (escaping padrão, sem `th:utext`).

## Risks / Trade-offs

- **[Risco] A saída do code review é grande (findings + matriz)** → Mitigação: schema JSON enxuto (máx. 8 findings) e validação Bean Validation; truncação do `resultJson` persistido.
- **[Risco] Conteúdo recuperado (guidelines/regras) tenta instruir o modelo** → Mitigação: mesmo padrão `DADOS NÃO CONFIÁVEIS` dos prompts existentes; decisões finais determinísticas no Java (matriz e prioridade).
- **[Risco] Mudança de DTOs quebra o contrato com o sidecar Python** → Mitigação: campos novos opcionais (null tolerado); testes de contrato Python + Java; `mvn test` e `pytest` verdes antes de merge.
- **[Risco] Latência extra (RAG + 1 chamada LLM + refinamento)** → Mitigação: reuse do timeout existente; refinamento só quando há item inválido; QA degrada para fallback sem bloquear a análise.
- **[Trade-off] QA dentro de um estágio existente em vez de nó paralelo** → menos paralelismo, mais simplicidade e preservação do grafo alvo.

## Migration Plan

1. Implementar em `feature/ai-quality-and-testing` com testes (H2); nenhuma migração de dados necessária — colunas novas são anuláveis e entidades novas são tabelas novas (ddl-auto em dev; schema versionado no compose para Postgres).
2. Sidecar Python atualizado em conjunto (bloco `qa` opcional no `final_result`); rollback = desfazer o commit do grupo correspondente, pois as mudanças Python/Java são co-deployadas.
3. `mvn test` + `pytest` verdes antes do merge; E2E dos Cenários A/B reexecutados.

## Open Questions

Nenhuma que altere specs, abordagem ou tasks — decisões deferidas (ex.: valores exatos da tabela de prioridade) são ajustáveis dentro das tasks sem mudar contratos.
