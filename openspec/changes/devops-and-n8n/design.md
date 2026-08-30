# Design: devops-and-n8n

## Context

Motivação e escopo: ver `proposal.md`. Estado atual relevante:

- `.github/workflows/ci.yml` tem 3 jobs (spring: spotless/test/package; agent: ruff/pytest; e2e: docker compose smoke) — sem separação unit/integration, sem stage de imagem Docker e sem artefatos de log.
- O backend já provê os padrões a reusar: `AiAnalysisService.generate(stage, changeText, evidence, outputType, fallback)` com structured output, validação, retry/backoff via `ResilienceExecutor`, trace event e métricas (`ai/AiAnalysisService.java`); `AnalysisStage` enum + `PromptRegistry` (`resources/prompts/<stage>-vN.txt`); `TraceService`/`AnalysisMetrics` (`observability/`); padrão de persistência H2 de registros de QA (`qa/QaReviewRecord`, `qa/QaRecordService`).
- O webhook do n8n consumirá `POST /api/change-requests/analyze` já existente (`web/ChangeRequestController.java`).

## Goals / Non-Goals

**Goals:**

- Estágios explícitos de CI (compile, unit, integration, E2E, quality, imagem Docker) com logs de build/teste publicados como artefatos.
- Análise de logs por LLM reusando o pipeline de IA existente, sem nunca alterar o pipeline.
- Detecção determinística de anomalia e tendência de falha sem LLM.
- Workflow n8n exportável que apenas integra e roteia.

**Non-Goals:**

- Sem CD (deploy) multi-ambiente, sem canary, sem autoscale.
- Sem novo agente ou novo nó LangGraph — nada desta change passa pelo grafo de análise.
- Sem UI nova; os endpoints de devops são REST (sem tela Thymeleaf).

## Decisions

### D1 — Expandir `ci.yml` existente (não criar workflow novo)

O pipeline passa a ter estágios nomeados dentro do job `spring` (compile → unit → integration → quality → build Docker image) e mantém jobs `agent` e `e2e` separados. Artefatos `build.log`/`test.log` gerados com redirecionamento das saídas do Maven e publicados via `actions/upload-artifact` (upload mesmo em falha: `if: always()`).

- Alternativa: workflows separados por estágio (mais arquivos, mais pontos de falha) — rejeitada por simplicidade.
- Integração vs unit: usar Maven Failsafe (`mvn verify` roda `*IT`/`*IT.java` separado do surefire). Testes E2E existentes continuam no job `e2e` (compose + smoke).

### D2 — Análise de logs como novo `AnalysisStage.LOG_ANALYSIS` no pipeline de IA existente

Novo `devops/` com `LogAnalysisService`, DTO `LogAnalysisResult` (summary, failedStep, probableCause, evidence, recommendedAction, confidence, degraded) em `ai/dto/AiResults`, prompt versionado `resources/prompts/log-analysis-v1.txt` (seção explícita "DADOS NÃO CONFIÁVEIS" para o conteúdo do log) e validação/retry reusando `AiAnalysisService.generate()`. Registro persistido `LogAnalysisRecord` (H2) com promptVersion, resultJson, degraded e traceId — mesmo padrão de `QaReviewRecord`. Endpoint `POST /api/devops/log-analysis` recebe o log como texto (delimitado como dado) e retorna diagnóstico; nenhuma escrita em arquivos de pipeline.

- Alternativa: prompt inline sem versão — rejeitada (regra: prompts versionados).
- O conteúdo do log nunca é concatenado ao system prompt; vai apenas no campo `evidence`/user como dado.

### D3 — Anomalia/tendência 100% determinística em `devops/AnomalyService`

Sem LLM. Duas capacidades:

- **Anomalia por desvio**: baseline = média móvel simples das N observações anteriores (N configurável, default 5); desvio relativo = |obs − baseline| / baseline; severidade por limiares fixos (ex.: ≥50% HIGH, ≥20% MEDIUM, senão LOW/normal). Limiares em `application.yml` (`devops.anomaly.*`).
- **Tendência de falha**: taxa de falha em janela móvel de 5 execuções; crescente (cada janela ≥ anterior) → tendência registrada.

Endpoint `POST /api/devops/runs` registra uma execução de pipeline (durationMs, success) e retorna relatório com anomalia e tendência; entidades H2 `PipelineRun`, `AnomalyEvent` (traceId, metric, baseline, observed, deviation, severity). Cálculo reprodutível por construção (mesma entrada → mesma saída).

- Alternativa: consumir Micrometer diretamente — rejeitada; manter entrada explícita via endpoint torna a demonstração reproduzível e testável.

### D4 — n8n como artefato estático de integração

`n8n/workflow.json` com nós: Webhook → HTTP Request (`POST /api/change-requests/analyze`) → IF (risk == HIGH) → notificação; documentação em `n8n/README.md` (trigger, endpoint, payload, response, condição, saída, evidência). Nenhuma regra de negócio no workflow. Payload de exemplo dos Cenários A e B documentado. Nenhum servidor n8n é subido no compose (validação por teste estrutural do JSON + import manual documentado).

- Alternativa: subir n8n via docker compose para teste — rejeitada (infraestrutura desnecessária para o objetivo acadêmico).

### D5 — Observabilidade e secrets

Toda operação de devops registra trace event via `TraceService` (node `log_analysis`, `anomaly_check`, `failure_trend`) e reusa métricas existentes (`llm_calls`, `validation_failures`). Logs enviados para a IA podem conter URLs/tokens: o prompt instrui o modelo a não reproduzir segredos e o `LogAnalysisService` aplica redação simples de padrões `token|secret|password|api_key` antes do envio, mantendo o restante do log para diagnóstico.

## Risks / Trade-offs

- [CI mais lento com stages separados] → rodar unit/integration no mesmo job (cache Maven) e E2E apenas após estes passarem (`needs`).
- [Upload de logs pode vazar segredo] → redação de padrões sensíveis antes do upload e antes do envio ao modelo; nunca logar env vars.
- [JSON do n8n pode divergir do schema do n8n] → manter nós mínimos e estáveis (Webhook, HTTP Request, IF, NoOp/notificação) e teste estrutural que valida JSON e tipos de nós.
- [Fallback de IA degradado pode mascarar diagnóstico] → registro marcado `degraded=true` e campo `recommended_action` determinístico ("revisão humana") como no padrão existente.

## Migration Plan

1. Merge da change na master; `ci.yml` novo vale a partir do próximo push/PR.
2. Endpoints novos são aditivos (`/api/devops/*`); nenhuma migração de dados — entidades novas em H2 (schema auto).
3. Rollback: reverter o commit do workflow; endpoints não removidos não afetam o fluxo de análise existente.
