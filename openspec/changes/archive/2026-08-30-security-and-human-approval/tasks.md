## 1. Domínio: SecurityAssessment e Approval estendido

- [x] 1.1 Criar entidade `SecurityAssessment` (id, FK change_request, detected, type, source, evidence, action, traceId, createdAt) e repositório; adicionar a `Approval` os campos approver, decision, decidedAt e traceId; verificar `mvn test` verde e schema atualizado no compose (`ddl-auto: update` cria `security_assessment` e as colunas novas em `approval`)
- [x] 1.2 Criar enum `ApprovalDecision` (APPROVED, REJECTED) e teste unitário de persistência com H2: salvar solicitação com análise, evento de segurança e decisão de aprovação e recuperar todos por identificador; verificar teste verde

## 2. Detecção determinística + prompt de segurança

- [x] 2.1 Implementar `SecurityAssessmentService` com marcadores determinísticos e fonte por origem (`change_request_text`, `code`, `knowledge`, `history`), evento com `type=prompt_injection`, evidence e `action=IGNORED`, dedupe por (type, source, evidence); verificar teste unitário: injeção da frase do Cenário B em cada origem detecta, conteúdo limpo não gera evento e nenhum evento contém segredo
- [x] 2.2 Criar prompt versionado `resources/prompts/security-analysis-v1.txt` (seção delimitada de DADOS NÃO CONFIÁVEIS) e registrar no `PromptRegistry`; verificar teste unitário de carregamento por etapa/versão e de fallback quando o prompt não existe
- [x] 2.3 Implementar etapa de segurança no `AiAnalysisService` (structured output + validação + retry máx. 2 + fallback determinístico marcado) com decisão final no Java; verificar com ChatModel fake: saída válida aceita, inválida persistente → fallback marcado, e sugestão do LLM nunca altera risco

## 3. Cobertura nos gateways e endpoint interno

- [x] 3.1 Varrer o conteúdo retornado por `analyze-code`, `retrieve-knowledge` e `retrieve-history` no `AgentGatewayController` via `SecurityAssessmentService`, persistindo eventos vinculados à solicitação antes de responder; verificar com MockMvc + H2: conteúdo com a frase do Cenário B gera evento persistido e o payload de resposta permanece íntegro
- [x] 3.2 Criar endpoint interno `POST /api/agent/security-assessment` que recebe o texto da solicitação e retorna a avaliação tipada (detected, events) com trace_id nos logs; verificar com MockMvc: texto limpo → detected=false; texto injetado → detected=true com eventos; falha de persistência não derruba o endpoint

## 4. Persistência no fluxo do agente e resposta de análise

- [x] 4.1 Estender `AgentResultMapper` para mapear `final_result.security_assessment` para eventos e `AnalysisService` para persistir os eventos junto da análise; verificar teste unitário do mapper com resultado contendo security assessment e sem ele
- [x] 4.2 Incluir avaliação de segurança em `GET /api/change-requests/{id}/analysis` (detected + eventos com tipo, fonte, evidência e ação); verificar com MockMvc: análise com eventos retorna avaliação completa, análise sem eventos retorna detected=false e lista vazia

## 5. Endpoint de aprovação humana

- [x] 5.1 Implementar `ApprovalService.decide(requestId, approver, decision, traceId)` com transição apenas a partir de PENDING e exigência de aprovação; verificar teste unitário: APPROVED e REJECTED registram approver/decision/decidedAt/traceId, segunda decisão e não exigida lançam conflito
- [x] 5.2 Criar `POST /api/change-requests/{id}/approval` com payload `{approver, decision}` e respostas 200/400/404/409 via `GlobalExceptionHandler`; verificar com MockMvc + H2: happy path (PENDING→APPROVED), rejeição, payload inválido 400, id inexistente 404, já decidida 409
- [x] 5.3 Garantir que análise HIGH concluída deixa aprovação PENDING e que apenas o endpoint transita para APPROVED/REJECTED (regra `RiskPolicy` intocada); verificar teste de integração: análise HIGH → PENDING; decisão via endpoint → estado final

## 6. Sidecar Python

- [x] 6.1 Alterar `detect_untrusted_content` para chamar `/api/agent/security-assessment` via o client existente (timeout/retry), espelhando a avaliação no `security_assessment` do state e incluindo-o no `final_result`; falha do client → `errors` + avaliação vazia; verificar `pytest agent/tests/` com client mockado (Cenário B e falha do endpoint)

## 7. E2E e evidências

- [x] 7.1 Estender `scripts/smoke_test.py` com o Cenário B: fixture em `TOOLS_REPO_ROOT` contendo a frase oficial de injeção → evento registrado na análise, risco permanece HIGH, aprovação PENDING; aprovar via endpoint e conferir APPROVED com approver/decision registrados; verificar `docker compose up` + smoke verde (sem chave de IA)
- [x] 7.2 Registrar evidências `docs/evidence/05-prompt-injection.png` (evento de segurança + análise concluída) e `docs/evidence/06-human-approval.png` (decisão APPROVED/REJECTED no endpoint) e atualizar README (segurança e aprovação humana); verificar presença dos arquivos e consistência do README
