# Tasks: ai-quality-and-testing

## 1. Serviço de AI code review (análise de diff e findings)

- [x] 1.1 Adicionar estágio `CODE_REVIEW` ao enum `AnalysisStage`, criar o prompt versionado `resources/prompts/code-review-v1.txt` (schema JSON de findings com component, description, severity e source; seção `DADOS NÃO CONFIÁVEIS` para o conteúdo recuperado) e o DTO tipado `CodeReviewResult` em `ai/dto/AiResults`; verificar `AiAnalysisServiceTest`/teste novo com prompt carregado por versão e `mvn test` verde
- [x] 1.2 Implementar `reviewCode(changeText, evidence)` no `AiAnalysisService` reusando `generate()` (structured output, validação, retry máx. 2, fallback degradado marcado, trace event e métrica); verificar teste unitário: saída válida convertida, saída inválida com retry limitado e fallback sem modelo configurado
- [x] 1.3 Criar `qa/QaCodeReviewService`: recupera `coding-guidelines` e `business-rules` via `RagService`, monta evidência delimitada como dado e executa o estágio `CODE_REVIEW`; verificar teste com `RagService` mockado garantindo que conteúdo recuperado entra como dado (e instrução injetada não altera findings) e `mvn test` verde
- [x] 1.4 Criar entidades `QaReviewRecord` e `QaFinding` (domínio + repositórios) com relação à análise e persistência em H2; verificar teste de persistência H2: registro com promptVersion, resultJson, degraded e traceId salvo/recuperado junto dos findings

## 2. Geração e refinamento de testes com IA

- [x] 2.1 Estender a geração de testes: o `QaCodeReviewService` entrega os findings de QA como evidência ao estágio `TEST_GENERATION` existente; verificar teste de que as recomendações geradas referenciam componentes dos findings e `mvn test` verde
- [x] 2.2 Implementar refinamento limitado: recomendações inválidas (ex.: descrição vazia) disparam até 2 iterações de regeneração com feedback, cada iteração registrada como trace event e no `QaReviewRecord`; esgotado o limite, a recomendação permanece marcada como não refinada; verificar teste unitário dos 3 caminhos (válida de primeira, refinada dentro do limite, limite esgotado)
- [x] 2.3 Garantir que nenhuma recomendação altera o repositório: revisar e testar que o fluxo QA apenas produz recomendações (nenhum arquivo criado/alterado); verificar teste que executa o fluxo QA e compara o working tree/lista de arquivos de teste antes e depois

## 3. Teste baseado em risco com matriz Impact × Likelihood

- [x] 3.1 Implementar `qa/RiskMatrixService` determinístico: combinação fixa Impact (LOW/MEDIUM/HIGH) × Likelihood (LOW/MEDIUM/HIGH) → prioridade; sugestões do modelo fora de faixa normalizadas; a prioridade final nunca vem diretamente da sugestão; verificar teste unitário da tabela completa de combinações e da normalização
- [x] 3.2 Avaliar as 4 categorias obrigatórias em toda análise (prompt injection, acesso não autorizado às tools, classificação incorreta de HIGH/LOW, regressão de regra de negócio financeira) e anexar impacto/probabilidade/prioridade calculada às recomendações; verificar teste de que cada categoria aplicável recebe prioridade derivada da matriz
- [x] 3.3 Adicionar `priorityJustification` e `riskCategory` a `TestRecommendation` (entidade + DTOs de entrada/saída) e garantir que toda análise entrega pelo menos um teste priorizado com justificativa (fallback determinístico quando QA degradada); verificar teste de persistência e de fallback degradado com justificativa presente

## 4. Integração no pipeline de análise, persistência e endpoints

- [x] 4.1 Orquestrar QA no endpoint `POST /generate-test-plan` do `AgentGatewayController`: RAG → code review → matriz → geração/refinamento → resposta com bloco `qa` (findings + recomendações priorizadas + registro); persistir `QaReviewRecord`/`QaFinding` no gateway via `requestId`; verificar teste MockMvc do endpoint com QA completa e com QA degradada
- [x] 4.2 Propagar o bloco `qa` pelo contrato: DTOs `AgentResponse`/`CreateAnalysisRequest` (campo opcional), nó `generate_test_plan` do sidecar Python repassando `qa` ao `final_result` sem lógica nova, e `AnalysisService` persistindo recomendações com justification/riskCategory; verificar `mvn test` + `pytest` verdes e teste de contrato
- [x] 4.3 Exibir QA na página de resultado: seção em `templates/result.html` com findings do review e recomendações priorizadas (prioridade + justificativa), indicação explícita de QA degradado e renderização escapada (`th:text`, sem `th:utext`); verificar teste MockMvc: QA completo renderizado, QA degradado sem quebrar a página e conteúdo com `<script>` escapado
- [x] 4.4 Registrar trace events e métricas do QA (ex.: `qa_review`, `qa_refinement`) e validar reconstrução por trace_id na página de trace; verificar teste de trace com eventos QA na ordem cronológica

## 5. Testes E2E dos cenários + evidência 09-ai-code-review.png

- [x] 5.1 E2E Cenário A (VIP 10%→15%): análise completa pela API com QA ativo — findings do review, recomendações priorizadas com justificativa e registro QA persistidos e recuperáveis; verificar teste E2E verde e `mvn test` completo
- [x] 5.2 E2E adversarial: conteúdo recuperado com instrução injetada não altera findings/prioridades do QA e evento de segurança continua registrado; verificar teste E2E verde
- [x] 5.3 Registrar evidência `docs/evidence/09-ai-code-review.png` (resultado do QA: findings, matriz de priorização e recomendações) e atualizar README (seção QA + matriz de requisitos: AI code review, AI test generation, risk-based testing); verificar presença do arquivo e consistência do README
