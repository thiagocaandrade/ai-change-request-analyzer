## Why

O projeto já produz análises de risco, logs estruturados e métricas, mas não demonstra os requisitos acadêmicos restantes de DevOps e automação: CI/CD completo, análise de logs com IA, detecção de anomalia/tendência de falha e integração n8n. A change 09 do roadmap (FASE 19–22) cobre exatamente esse deliverable; é a penúltima antes do hardening final.

## What Changes

- Ampliar o workflow de CI para o ciclo completo: compile → unit → integration → E2E → quality checks → build de imagem Docker, falhando quando testes críticos falham.
- Gerar artefatos de log de build/teste (build.log/test.log) analisáveis por IA.
- Novo serviço de análise de logs com IA: `summary`, `failed_step`, `probable_cause`, `evidence`, `recommended_action`, `confidence` — a IA nunca altera o pipeline automaticamente.
- Novo serviço determinístico de detecção de anomalia (baseline + desvio + severidade) e tendência de falha (failure rate em 5 execuções).
- Workflow n8n exportável (`n8n/workflow.json`): webhook → `POST /api/change-requests/analyze` → IF risk == HIGH → notificação. Lógica de negócio permanece no Spring Boot.
- Evidências em `docs/evidence/` (11-github-actions.png, 12-anomaly.png, 13-n8n.png) e atualização da matriz do README.

## Capabilities

### New Capabilities

- `ai-log-analysis`: análise de logs de build/teste por LLM com structured output validado (summary, failed_step, probable_cause, evidence, recommended_action, confidence).
- `anomaly-detection`: estatística simples e determinística sobre métricas históricas — desvio vs baseline, severidade e tendência de falha.
- `n8n-integration`: workflow n8n exportável que dispara análise e notifica apenas quando o risco é HIGH.

### Modified Capabilities

- `ci-pipeline`: o pipeline passa a executar estágios adicionais (unit, integration, E2E, quality, Docker image) e a publicar artefatos de log analisáveis por IA.

## Impact

- `.github/workflows/` — expansão do pipeline existente.
- Novos pacotes `devops/` (análise de logs, anomalia) no backend Spring Boot, reusando `AiAnalysisService`, `observability/` e `resilience/`.
- `n8n/workflow.json` + documentação do workflow.
- `docs/evidence/`, `README.md`, `docs/roadmap.md` (status da change 09).
- Sem mudanças em endpoints de análise existentes; o webhook n8n consome `POST /api/change-requests/analyze` já existente.

## Non-goals

- Sem autodeploy/automerge feito pela IA: a IA apenas sugere; o pipeline nunca é alterado automaticamente por ela.
- Sem ML complexo na detecção de anomalia (apenas estatística simples determinística).
- Sem lógica de negócio no n8n; sem novos endpoints além dos necessários para DevOps.
- Sem CD multi-ambiente, canary ou infraestrutura adicional além do Docker image já previsto.
