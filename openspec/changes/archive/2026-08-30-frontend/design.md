## Context

Estado atual (verificado no código):

- `spring-boot-starter-thymeleaf` e `spring-boot-starter-thymeleaf-test` já estão no `pom.xml` (linhas 51 e 99); `src/main/resources/templates/` e `src/main/resources/static/` existem e estão vazios.
- A API REST já entrega tudo que as telas precisam: `POST /api/change-requests` (dispara a análise e devolve id/status/traceId), `GET /api/change-requests/{id}` e `GET /api/change-requests/{id}/analysis` (risco, findings, recomendações, eventos de segurança, aprovação), `POST /api/change-requests/{id}/approval` (decisão humana) e `GET /api/traces/{traceId}` (eventos em ordem cronológica).
- `TraceEvent` (`observability/`) não registra hoje as fontes dos documentos recuperados pelo RAG — `RagService` grava eventos de `retrieve_knowledge` (busca/degradado) sem as fontes. O roadmap (change 07) exige a página de trace com "documentos recuperados", o que motiva o delta em `observability` (ver `specs/observability/spec.md`).
- `GlobalExceptionHandler` é `@RestControllerAdvice` (JSON) — erros de página precisam ser tratados pelo controller MVC, não pelo handler REST.
- Motivação e requisitos: ver `proposal.md` (Why) e `specs/` (web-ui, trace-viewer + delta observability).

## Goals / Non-Goals

**Goals:**

- Três páginas Thymeleaf (formulário, resultado, trace) server-side, sem duplicar a lógica de pipeline existente nos controllers REST.
- Documentos recuperados visíveis na página de trace, registrados deterministicamente no `TraceEvent` pelo `RagService`.
- Escaping garantido de todo conteúdo não confiável (solicitação, findings, evidências de segurança, fontes recuperadas).

**Non-Goals:**

- Sem JavaScript de framework, sem SPA, sem autenticação/sessão.
- Sem novos endpoints REST; sem alteração no sidecar Python/LangGraph.
- Sem dashboard de métricas (Actuator continua sendo a fonte).

## Decisions

### D1 — `WebController` MVC delega aos controllers REST existentes (sem extrair fachada)

Um único `WebController` no pacote `web/` lida com `GET /`, `POST /change-requests`, `GET /requests/{id}`, `POST /requests/{id}/approval` e `GET /traces/{traceId}`. Para não duplicar orquestração de pipeline nem mapeamento de DTOs, ele chama os beans `ChangeRequestController` (create/get/getAnalysis/approve) e usa `TraceService` diretamente para a página de trace.

- Alternativa rejeitada A: duplicar a orquestração no WebController — viola "sem lógica de negócio duplicada" e quebraria quando o pipeline mudar.
- Alternativa rejeitada B: extrair `AnalysisPipelineService` e refatorar o `ChangeRequestController` — mexe em código crítico já testado (541 linhas de teste) sem ganho funcional nesta change.
- Controllers no mesmo contexto Spring; chamar método de bean é determinístico e mantém os testes REST intactos.

### D2 — Formulário server-side com Post/Redirect/Get

O form envia para o `WebController` (form-urlencoded), que valida texto em branco (BindingResult → re-renderiza o form com mensagem) e, válido, chama `changeRequestController.create(...)` e redireciona (303) para `/requests/{id}`. A página de resultado carrega sempre o estado atual do repositório — cobre tanto análise concluída quanto falha (`agent_unavailable`) sem estado em sessão. A decisão de aprovação é um POST do formulário da própria página de resultado.

### D3 — Thymeleaf natural + 1 CSS estático; escaping via th:text

Renderização 100% server-side; templates `index.html`, `result.html`, `trace.html`; CSS único `static/css/app.css`. Todo dado não confiável é renderizado com `th:text` (escapamento automático do Thymeleaf); `th:utext` é proibido para conteúdo vindo de dados. Isso implementa o requisito de escaping sem código adicional.

### D4 — Fontes recuperadas registradas no `TraceEvent` (delta observability)

Novo campo opcional `detail` em `TraceEvent` (varchar curto, JSON compacto), exposto no `TraceEventDto`. `RagService` registra após busca bem-sucedida um evento `rag_search` com `detail` = lista `[{source, document_id, score}]` dos hits (truncada a 1024 caracteres; sem conteúdo do documento — dado não confiável e potencialmente grande). Busca degradada segue como hoje (`rag_degraded`), sem `detail`. `ddl-auto: update` adiciona a coluna sem migração manual; testes H2 usam tipos portáveis.

- Alternativa rejeitada: guardar documentos na página de resultado via API de análise — exigiria novo endpoint/entidade de domínio; a fonte natural da reconstrução por trace_id já é o `TraceEvent`, e o roadmap pede isso na página de trace.

### D5 — Erros de página tratados no `WebController`

O `GlobalExceptionHandler` (REST/JSON) não é usado pelas páginas: o `WebController` captura `TraceNotFoundException` e `ChangeRequestNotFoundException` e renderiza página amigável (404 próprio); erros de análise aparecem como status na página de resultado. Nenhum stack trace em HTML.

### D6 — Testes seguindo o padrão existente

Testes de view com `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` e `@MockitoBean AgentClient` (mesmo padrão de `ChangeRequestControllerTest`), com `spring-boot-starter-thymeleaf-test` para asserções de renderização. E2E reaproveita o fluxo dos Cenários A/B via MockMvc ponta a ponta (form → análise → resultado → aprovação → trace).

## Risks / Trade-offs

- [Chamada de controller REST a partir do WebController acopla os dois] → mesma aplicação/módulo, contrato é o retorno tipado (ResponseEntity/DTO); se a assinatura mudar, a compilação acusa. Documentado como aceito pelo baixo risco vs. churn.
- [POST do formulário espera até o agente responder (timeout 120s)] → aceitável para demonstração; o mesmo limite da API; sem polling nesta change.
- [`detail` pode crescer com muitos hits] → truncamento determinístico a 1024 caracteres e apenas metadados (source/document_id/score), nunca conteúdo.
- [Escaping pode ser contornado por template mal escrito] → regra de teste dedicada: casos com HTML/script em solicitação, finding e evidência verificam renderização literal.
- [Coluna nova em tabela existente] → `ddl-auto: update` adiciona sem tocar dados; rollback = reverter a change (coluna inerte).

## Migration Plan

1. Aplicar na branch `feature/frontend`; a coluna `detail` em `trace_event` é criada no boot (`ddl-auto: update`).
2. Rollback: reverter a change — páginas somem, API REST e `trace_event` antigos continuam funcionais.
3. Pré-condição de entrega: `mvn test` verde e smoke dos Cenários A/B via páginas (form → resultado → aprovação/trace).

## Open Questions

Nenhuma — as decisões acima são suficientes para o breakdown de tasks.
