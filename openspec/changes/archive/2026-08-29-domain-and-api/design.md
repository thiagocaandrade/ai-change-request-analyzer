## Context

Foundation entregou `ChangeRequest` mínimo (id, text, status, traceId, `result` como JSON string opaco), `ChangeRequestStatus` (PENDING/COMPLETED/FAILED), repositório JPA, controller em `/requests` e `GlobalExceptionHandler`. Stack já presente: Java 21, Spring Boot, Spring Data JPA, PostgreSQL (dev com `ddl-auto`), Bean Validation (`@Valid` no controller), logs JSON com trace_id. Motivação: ver `proposal.md - Why`.

## Goals / Non-Goals

**Goals:**
- Modelo de domínio tipado e persistente (análise, achados, risco, testes, aprovação) sem dependência de LLM.
- Regras determinísticas centralizadas em serviço Java com teste unitário isolado.
- API REST canônica em `/api/change-requests` com DTOs validados e erros estruturados.

**Non-Goals:**
- Não tocar no grafo LangGraph nem no agente Python (change 03).
- Não implementar decisão/endpoint de aprovação humana (change 05) — a entidade e o estado PENDING nascem aqui.
- Sem migração de banco ferramental (Flyway/Liquibase) — dev-only com `ddl-auto`.

## Decisions

**D1 — Relacionamentos 1:1/1:N com FK do lado do "filho".**
`ChangeRequest 1:1 ChangeAnalysis` (FK `change_request_id` em `change_analysis`), `ChangeAnalysis 1:N ImpactFinding`, `ChangeAnalysis 1:1 RiskAssessment`, `ChangeAnalysis 1:N TestRecommendation`, `ChangeRequest 1:1 Approval`. IDs UUID gerados em `@PrePersist` (padrão já usado pela foundation). Enums persistidos como STRING.
*Alternativa rejeitada:* FK no `ChangeRequest` (poluiria a entidade da foundation com campos de fases futuras) e JSONB para achados (perde tipagem, que é o objetivo da change).

**D2 — Regras determinísticas em `RiskPolicy` (serviço Java puro, sem Spring/LLM).**
`assess(riskLevel, confidence)` retorna decisão tipada: risco HIGH ⇒ `approvalRequired = true` + `ApprovalStatus.PENDING` (sempre); confidence fora de [0,1] ⇒ `InvalidConfidenceException` (400). O `AnalysisService` aplica a regra na única porta de entrada de registro de análise; nada de lógica em setters de entidade nem no LLM.
*Alternativa rejeitada:* constraint de banco — esconderia a regra e dificultaria a evidência acadêmica (teste unitário legível é requisito do roadmap).

**D3 — Substituir `result` (JSON opaco) por relação tipada com `ChangeAnalysis`.**
Coluna `result` removida de `change_request`. O `POST /api/change-requests` continua delegando ao agente stub; um `AgentResultMapper` converte defensivamente a resposta em `ChangeAnalysis` (campos ausentes viram análise vazia, nunca falha o fluxo). O registro explícito e validado de análise fica em `POST /api/change-requests/{id}/analysis`, usado pelas fases seguintes.
*Alternativa rejeitada:* manter `result` e adicionar tabelas em paralelo — manteria duas fontes de verdade.

**D4 — API canônica em `/api/change-requests`.**
Rotas da foundation migram de `/requests` para `/api/change-requests` (base usada pelo roadmap nas changes 05 e 09). DTOs como records Java (padrão já usado em `web/`), validação com Bean Validation (`@NotBlank`, `@Valid`, range de confidence no DTO + regra no serviço). Erros centralizados no `GlobalExceptionHandler` existente, adicionando handler para `MethodArgumentNotValidException` (400) e `InvalidConfidenceException` (400).
*Alternativa rejeitada:* manter `/requests` e criar `/api/change-requests` em paralelo — dois prefixos confundiriam a demonstração e o n8n.

**D5 — Testes por camada, sem duplicação.**
`RiskPolicyTest` (puro), `@DataJpaTest` para mapeamentos, `@WebMvcTest`/`MockMvc` para API (happy path, 400, 404). Testes existentes da foundation ajustados à nova rota nesta mesma change — CI verde é pré-condição (AGENTS.md).

## Risks / Trade-offs

- [Quebra de testes da foundation pela mudança de rota e remoção de `result`] → Mitigação: ajustar os testes no mesmo change e rodar `mvn test` completo antes de encerrar.
- [`ddl-auto: update` pode deixar coluna `result` órfã em volumes existentes] → Mitigação: ambiente dev descartável; documentar recriação do volume no README se necessário.
- [Mapper do agente stub ficar frágil quando a change 03 mudar o payload] → Mitigação: mapper isolado em classe única, fácil de substituir; o registro estruturado não depende dele.
- [Regra de risco duplicada no futuro (change 05 reusará `RiskPolicy`)] → Mitigação: `RiskPolicy` é o único ponto de verdade; change 05 consome, não recria.

## Migration Plan

1. Implementar entidades/serviços/regras (sem remover rotas antigas ainda).
2. Migrar rotas e remover `result`; ajustar testes da foundation.
3. Rodar `mvn test` completo; verificar smoke via Docker Compose (dev).
4. Rollback: reverter commit; dados dev são recriados pelo `ddl-auto` — sem migração destrutiva em produção (não há produção).
