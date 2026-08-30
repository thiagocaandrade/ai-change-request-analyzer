## Why

O analisador hoje só é utilizável via API REST — não há interface web, embora `spring-boot-starter-thymeleaf` e `spring-boot-starter-thymeleaf-test` já estejam no `pom.xml` (verificado). Sem telas, a demonstração dos Cenários A/B e a operação humana (submissão de mudança, leitura do risco e aprovação) dependem de clientes HTTP externos. É a change 07 do roadmap (FASE 16): 1 tela Thymeleaf + página de trace.

## What Changes

- Nova tela principal Thymeleaf: formulário para enviar uma solicitação de alteração, que dispara a análise existente (`POST /api/change-requests`) e redireciona para o resultado.
- Nova tela de resultado: exibe classificação, risco (nível, confiança, justificativa), findings, plano de testes, eventos de segurança e estado de aprovação; quando HIGH, apresenta formulário de decisão humana (`POST /api/change-requests/{id}/approval`).
- Nova página de trace: consulta por trace_id (`GET /api/traces/{traceId}`, já existente — evidência: `TraceController.java`) e lista eventos em ordem cronológica (etapa, duração, status, tool, model) e, quando disponíveis, documentos recuperados pelo RAG.
- Estilo CSS estático próprio (sem SPA, sem framework JS — decisão de arquitetura nº 7 do AGENTS.md) e navegação consistente entre as três telas.
- Testes MockMvc/E2E das telas (happy path, HIGH com aprovação, trace 404, escaping de conteúdo não confiável) e evidência em `docs/evidence/`.

## Capabilities

### New Capabilities

- `web-ui`: interface Thymeleaf para submissão de solicitações de alteração e exibição do resultado da análise, incluindo a decisão de aprovação humana.
- `trace-viewer`: página que reconstrói uma execução pelo trace_id a partir dos eventos de auditoria persistidos (etapas, duração, tools, documentos recuperados).

### Modified Capabilities

Nenhuma — os requisitos da API (`change-api`, `observability`, `security-and-approval`) não mudam; as telas apenas consomem endpoints existentes.

## Impact

- Código: novo controller MVC de páginas (`web/`); templates em `src/main/resources/templates/` (hoje vazio) e CSS em `src/main/resources/static/` (hoje vazio). Nenhuma alteração nos endpoints REST existentes.
- Dependências: nenhuma nova — `spring-boot-starter-thymeleaf` e `spring-boot-starter-thymeleaf-test` já constam no `pom.xml` (linhas 51 e 99).
- Dados: nenhuma mudança de schema; a página de trace lê `trace_event` existente.
- Testes: novos testes de view (MockMvc + Thymeleaf) e E2E; evidência `docs/evidence/08-...` a definir conforme o roteiro do vídeo.

## Non-Goals

- Sem SPA, React, JavaScript de framework ou autenticação (decisão nº 7 do AGENTS.md).
- Sem alterar o sidecar Python, o grafo LangGraph ou as regras determinísticas (RiskPolicy, aprovação).
- Sem novos endpoints REST — apenas páginas que consomem a API existente.
- Sem dashboard de métricas (Actuator continua sendo a fonte de métricas).
