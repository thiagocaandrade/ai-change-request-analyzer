## 1. Tela principal: formulário de solicitação de alteração

- [x] 1.1 Criar `WebController` no pacote `web/` com `GET /` (formulário), `POST /change-requests` (valida texto em branco; delega ao `ChangeRequestController.create`; redirect 303 para `/requests/{id}`) e rota `GET /requests/{id}`/`POST /requests/{id}/approval` esboçadas; verificar `mvn test` verde com o controller registrado no contexto
- [x] 1.2 Criar template `templates/index.html` com formulário (`textarea` + submit), mensagem de erro de validação e links para trace; verificar `GET /` retorna 200 e contém o formulário via MockMvc
- [x] 1.3 Validar fluxo completo do formulário: texto válido dispara análise e redireciona para a página de resultado; texto vazio re-renderiza o formulário com mensagem de validação sem chamar o agente; verificar teste MockMvc dos dois caminhos e `mvn test` verde

## 2. Tela de resultado da análise (risco, findings, plano de testes, aprovação)

- [x] 2.1 Criar template `templates/result.html` renderizando status, nível de risco, confiança, justificativa, findings (componente, descrição, severidade), recomendações de teste (componente, descrição, prioridade) e eventos de segurança (tipo, origem, evidência, ação), tudo com `th:text`; verificar `GET /requests/{id}` 200 renderizando os campos via MockMvc com análise persistida
- [x] 2.2 Exibir estado de aprovação: HIGH com PENDING mostra formulário de decisão (aprovador + APPROVED/REJECTED); LOW/MEDIUM mostra "aprovação não exigida" sem formulário; decisão submetida reflete APPROVED/REJECTED com aprovador na página; verificar teste MockMvc dos três estados e `mvn test` verde
- [x] 2.3 Tratar falha da análise: solicitação com status FAILED exibe status e motivo de forma legível; solicitação inexistente exibe página amigável 404 do próprio WebController (não o JSON do GlobalExceptionHandler); verificar teste MockMvc de ambos

## 3. Página de trace (etapas, duração, tools, documentos recuperados)

- [ ] 3.1 Adicionar campo opcional `detail` a `TraceEvent` (coluna varchar limitada), ao `TraceEventDto` e ao construtor do `TraceService`; verificar teste de persistência H2 existente ainda verde e novo evento com detail salvo/recuperado
- [ ] 3.2 Registrar fontes recuperadas no `RagService`: após busca com sucesso, evento `rag_search` com `detail` = JSON compacto de `[{source, document_id, score}]` (truncado a 1024 caracteres, sem conteúdo dos documentos); busca degradada continua sem detail; verificar `RagServiceTest` cobrindo sucesso com fontes, sucesso sem hits e degradado
- [ ] 3.3 Criar rota `GET /traces/{traceId}` no `WebController` (consulta `TraceService`) e template `templates/trace.html` listando eventos em ordem cronológica (etapa, evento, duração, status, erro, tool, modelo, momento) com a seção de documentos recuperados (fontes + score) quando houver detail; verificar MockMvc: eventos renderizados na ordem e documentos exibidos
- [ ] 3.4 Trace inexistente renderiza página amigável "trace não encontrado"; nenhum segredo aparece nos eventos renderizados; verificar teste MockMvc com trace vazio e verificação de ausência de padrões de segredo

## 4. Estilo CSS e navegação entre as telas

- [ ] 4.1 Criar `static/css/app.css` (layout simples e responsivo: cabeçalho, cartões de risco por nível, tabela de eventos, badges de status) e aplicar nos três templates; verificar `GET` das três páginas retorna HTML com link para o CSS
- [ ] 4.2 Navegação consistente: formulário → resultado → trace (link por trace_id) e retorno ao formulário; badges de risco diferenciados por nível (LOW/MEDIUM/HIGH); verificar navegação via MockMvc e inspeção manual das páginas renderizadas
- [ ] 4.3 Auditoria de escaping: garantir que nenhum dado não confiável usa `th:utext`; solicitação/finding/evidência com HTML/script renderizam literalmente; verificar teste MockMvc com payload contendo `<script>` e `mvn test` verde

## 5. Testes das telas (MockMvc/E2E) + evidência

- [ ] 5.1 Criar `WebUiTest` (MockMvc): formulário válido → análise persistida → página de resultado com risco/findings; texto vazio → erro no formulário; falha do agente → página com status de falha; verificar `mvn test` verde
- [ ] 5.2 Criar `TraceViewTest` (MockMvc): reconstrução com eventos ordenados e documentos recuperados; 404 amigável para trace inexistente; escaping de HTML em campos renderizados; verificar `mvn test` verde
- [ ] 5.3 E2E dos Cenários A/B pelas páginas (form → resultado HIGH com aprovação → decisão refletida → trace reconstruído; Cenário B com evento de segurança exibido e risco não alterado pela injeção); verificar teste E2E verde e `mvn test` completo
- [ ] 5.4 Registrar evidência `docs/evidence/08-frontend.png` (telas: formulário, resultado HIGH e página de trace) e atualizar README (seção frontend + matriz de requisitos); verificar presença do arquivo e consistência do README
