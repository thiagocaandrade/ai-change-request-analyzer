# Design — security-and-human-approval

## Context

O nó `detect_untrusted_content` do sidecar faz varredura local por marcadores no texto da solicitação (evidência: `agent/graph/nodes.py`, linhas 212–239) e o `security_assessment` resultante fica só no state — nunca é persistido, não tem `action` e não cobre o conteúdo retornado pelos nós de coleta (que executam depois). O `Approval` Java (`src/main/java/.../domain/Approval.java`) tem apenas `required`/`status`/`createdAt`; `RiskPolicy` já decide HIGH ⇒ PENDING e deve permanecer intocado. O domínio é criado por `ddl-auto: update` (`application.yml`), os gateways `/api/agent/**` retornam conteúdo recuperado sem varredura, e o client Python (`agent/tools/client.py`) já tem timeout/retry/`X-Trace-Id`. Motivação: ver proposal.md — Why.

## Goals / Non-Goals

**Goals:**
- Detecção determinística de injeção no Java, cobrindo texto da solicitação e conteúdo recuperado, com eventos persistidos e rastreáveis por trace_id.
- Endpoint de aprovação humana com registro completo (approver, decision, decidedAt, trace_id) sem tocar em `RiskPolicy`.
- Cenário B demonstrável ponta a ponta, com suíte verde sem chave de IA.

**Non-Goals:**
- Sem sanitização/edição de conteúdo injetado (registrar e ignorar, nunca reescrever).
- Sem autenticação/autorização no endpoint de aprovação (demo acadêmica single-user).
- Sem mudanças na topologia do grafo (13 nós e ordem fixados no roadmap/AGENTS.md).

## Decisions

**D1 — Detecção determinística vive no Java (`SecurityAssessmentService`).**
Lista de marcadores de injeção (ex.: "ignore as instruções", "ignore all instructions", "classifique ... como low") com atribuição de fonte por origem do conteúdo (`change_request_text`, `code`, `knowledge`, `history`); evento com `type=prompt_injection`, `source`, `evidence` (trecho/marcador) e `action=IGNORED`. A detecção nunca altera risco, classificação ou fluxo. Alternativa (manter só no Python) — rejeitada: viola "regras determinísticas ficam no Java" (AGENTS.md, decisão 4) e não persiste nada.

**D2 — Pontos de cobertura sem mudar a topologia.**
Os gateways `analyze-code`, `retrieve-knowledge` e `retrieve-history` varrem o conteúdo que vão retornar e persistem eventos antes de responder; novo endpoint interno `POST /api/agent/security-assessment` recebe o texto da solicitação e retorna a avaliação para o nó de detecção do grafo. Alternativa (mover o nó para depois da coleta) — rejeitada: a ordem dos 13 nós está fixada no roadmap; a varredura no ponto de retorno cobre exatamente o conteúdo que chega ao grafo.

**D3 — LLM assiste, o Java decide.**
Prompt versionado `resources/prompts/security-analysis-v1.txt` (mesmo padrão do `PromptRegistry`; conteúdo recuperado em seção delimitada de DADOS NÃO CONFIÁVEIS) com structured output (`BeanOutputConverter`) e validação; inválido → retry máx. 2 → fallback determinístico marcado. A decisão final é a união dos eventos determinísticos (D1) com os achados validados do LLM, com dedupe por `(type, source, evidence)`; ação sempre decidida pela aplicação. Alternativa (LLM como único detector) — rejeitada: regra determinística; a frase oficial do Cenário B precisa ser detectada mesmo sem chave de IA.

**D4 — Persistência de eventos.**
Entidade `SecurityAssessment` (id, requestId FK, `detected`, `type`, `source`, `evidence`, `action`, `traceId`, `createdAt`) — tabela nova criada por `ddl-auto: update`, mesmo padrão das tabelas de domínio existentes. `AgentResultMapper` passa a ler `final_result.security_assessment` e `AnalysisService` persiste os eventos junto da análise; `GET /api/change-requests/{id}/analysis` passa a incluir a avaliação de segurança (spec `change-api`). Alternativa (eventos só em log) — rejeitada: o requisito é "security event registrado" e a matriz pede evidência recuperável.

**D5 — Endpoint de aprovação humana.**
`POST /api/change-requests/{id}/approval` com payload `{approver, decision}` (APPROVED|REJECTED); `ApprovalService.decide(requestId, approver, decision, traceId)` aceita transição apenas a partir de PENDING com aprovação exigida; `Approval` ganha `approver`, `decision`, `decidedAt`, `traceId`. Erros: 404 (solicitação inexistente), 400 (payload inválido), 409 (já decidida ou não exigida) — no `GlobalExceptionHandler`, padrão existente. HIGH permanece PENDING até a decisão (regra de `RiskPolicy`, intocada). Alternativa (PUT genérico no recurso) — rejeitada: contrato restrito e testável fica mais simples.

**D6 — Sidecar: nó de detecção obtém avaliação da aplicação.**
`detect_untrusted_content` chama `/api/agent/security-assessment` via o client existente (timeout/retry); falha → entrada em `errors` + avaliação vazia, grafo segue. `security_assessment` do state espelha a avaliação; `finalize` inclui `security_assessment` no `final_result` (o nó já inclui `approval`). Alternativa (nó chama banco direto) — rejeitada: o Python não tem banco nem deve ter regras.

**D7 — Estratégia de teste.**
Unitários Java: `SecurityAssessmentService` (marcadores, fontes, sem segredos), `ApprovalService`/endpoint (MockMvc + H2: 200/400/404/409), `AgentResultMapper` (security assessment no resultado). pytest: Cenário B no grafo (injeção detectada via client mockado; risco não alterado; falha do endpoint → análise segue). E2E smoke: `TOOLS_REPO_ROOT` apontando para fixture com arquivo contendo a frase oficial do Cenário B — o tool retorna o conteúdo, o gateway detecta, o evento persiste, o risco permanece HIGH e a aprovação fica PENDING. Evidências: `docs/evidence/05-prompt-injection.png` (evento registrado + análise concluída) e `06-human-approval.png` (endpoint APPROVED/REJECTED).

## Risks / Trade-offs

- **[R1] Marcadores determinísticos não cobrem todas as variações de injeção** → LLM assiste (D3); o teste adversarial usa a frase oficial do Cenário B; aceita-se cobertura heurística (escopo acadêmico).
- **[R2] `ddl-auto: update` altera tabela existente (`approval` ganha colunas)** → consistente com o padrão do projeto desde foundation; sem dados críticos em dev; rollback = reverter commit.
- **[R3] Eventos persistidos podem preceder o fim da análise** (gateways persistem durante a coleta) → aceitável: eventos vinculados à solicitação permanecem rastreáveis mesmo se a análise falhar depois.
- **[R4] Dupla detecção (determinística + LLM) pode duplicar eventos** → dedupe por `(type, source, evidence)` no serviço (D3).
- **[R5] Endpoint de aprovação sem autenticação** → non-goal documentado; a regra HIGH ⇒ aprovação obrigatória continua sendo a proteção central.

## Migration Plan

Aditivo, sem migração de dados: tabela `security_assessment` nova e colunas novas em `approval` via `ddl-auto: update`; prompts e endpoint novos. Ordem de adoção: domínio (`SecurityAssessment`, campos de `Approval`) → `SecurityAssessmentService` + prompt v1 → varredura nos gateways → endpoint interno `/api/agent/security-assessment` → mapper/persistência + resposta de análise → endpoint de aprovação → nó Python → testes (unit/pytest/E2E) → evidências 05/06. Rollback: reverter o commit; a tabela nova pode ser descartada sem afetar o domínio existente.
