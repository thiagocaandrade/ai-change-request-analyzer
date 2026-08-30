## Why

A detecção de injeção só existe como varredura local do grafo (evidência: `agent/graph/nodes.py`, linhas 212–239) — os eventos de segurança não são persistidos, não têm `action` nem cobrem o conteúdo retornado pelos nós de coleta. E o `Approval` (Java) não registra approver/decision/trace_id nem existe endpoint para decisão humana. Sem isso, o Cenário B (injeção em conteúdo recuperado) não é demonstrável ponta a ponta e o risco HIGH nunca pode ser aprovado/rejeitado por humano — os requisitos "prompt injection" e "aprovação humana" do PDF. É a change 05 do roadmap (FASE 12–13), na ordem obrigatória.

## What Changes

- `SecurityAssessment` no domínio Java: `detected`, `type`, `source`, `evidence`, `action` — eventos de segurança persistidos por solicitação e expostos na resposta de análise.
- Detecção determinística de injeção no Java (`SecurityAssessmentService`): varre o texto da solicitação e o conteúdo recuperado (código, docs, histórico) antes de retorná-los; evento registrado com trace_id e `action` (ex.: `IGNORED`); a detecção NUNCA altera risco ou classificação.
- Análise de segurança assistida por LLM: prompt versionado `security-analysis-v1.txt` com structured output validado (retry máx. 2, fallback determinístico marcado); a decisão final permanece determinística no Java.
- Nó `detect_untrusted_content` passa a obter a avaliação do Java via novo endpoint interno `/api/agent/security-assessment`; os eventos persistidos entram no resultado final.
- Endpoint humano `POST /api/change-requests/{id}/approval` com decisão PENDING/APPROVED/REJECTED; registra approver, decision, timestamp e trace_id; HIGH permanece PENDING até decisão humana (regra existente em `RiskPolicy`, intocada).
- Evidências `docs/evidence/05-prompt-injection.png` e `06-human-approval.png`; smoke E2E estendido com o Cenário B adversário.

## Capabilities

### New Capabilities

- `security-and-approval`: avaliação de segurança (detecção, eventos, ação) e ciclo de aprovação humana (endpoint, transições, registros), com regras determinísticas no Java.

### Modified Capabilities

- `domain-model`: o modelo persistente passa a incluir avaliação de segurança; `Approval` ganha approver, decision, decidedAt e trace_id.
- `change-api`: novo requisito de endpoint de aprovação humana; a resposta de análise passa a incluir a avaliação de segurança.
- `ai-capabilities`: nova etapa de análise de segurança com prompt versionado `security-analysis-v1.txt` e structured output validado.
- `agent-orchestration`: "Conteúdo recuperado não confiável" passa a cobrir também o conteúdo retornado pelos nós de coleta, com eventos persistidos na aplicação e avaliação obtida do Java.

## Non-goals

- Sem métricas, segundo sinal observável, análise de logs com IA e anomalias (changes 06/09).
- Sem frontend, n8n e code review com IA (changes 07–09).
- Sem alterar a regra HIGH ⇒ aprovação obrigatória — `RiskPolicy` permanece como está.
- Sem autenticação/controle de acesso no endpoint de aprovação (demo acadêmica single-user).
- Sem sanitização/edição do conteúdo injetado — a injeção é registrada e ignorada, nunca reescrita.

## Impact

- Java: novo pacote `security/` (entidade, repositório, serviço), endpoint interno `/api/agent/security-assessment`, endpoint `/api/change-requests/{id}/approval`; `AgentResultMapper`/`AnalysisService` passam a persistir eventos; `Approval` ganha campos novos.
- Python: nó `detect_untrusted_content` chama o Java via client existente (timeout/retry); `security_assessment` do state espelha a avaliação da aplicação.
- Prompts: novo `resources/prompts/security-analysis-v1.txt`.
- Testes: unitários Java (detecção, endpoint de aprovação, mapper), pytest (Cenário B no grafo), E2E smoke (Cenário B), evidências 05/06.
