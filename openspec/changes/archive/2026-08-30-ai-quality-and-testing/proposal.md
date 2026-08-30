# Proposal: ai-quality-and-testing

## Why

O analisador identifica impacto e risco, mas não revisa o código/diff da alteração com IA nem prioriza testes por risco. O PDF de requisitos exige demonstração de "code review com IA", "geração/refinamento de testes" e "teste baseado em risco" (FASE 17/18 do roadmap); sem esta change, esses requisitos ficam sem evidência e o vídeo de demonstração (momento 6:15) não tem funcionalidade correspondente.

## What Changes

- Novo serviço QA de **code review com IA**: analisa o diff/descrição da alteração, consulta `coding-guidelines` e `business-rules` via RAG, identifica riscos e testes ausentes e produz findings estruturados.
- Novo serviço de **geração/refinamento de testes com IA**: sugere testes a partir dos findings, com iteração de refinamento; nunca altera código automaticamente.
- **Teste baseado em risco**: matriz Impact × Likelihood priorizando prompt injection, acesso não autorizado a tools, classificação incorreta de HIGH/LOW e regressão de regra financeira; pelo menos um teste priorizado com justificativa. A priorização final é determinística no Java — a IA apenas sugere impacto/probabilidade.
- Registro de **prompt, resultado, findings e risco** por execução QA, persistidos e correlacionados por trace_id.
- Exibição dos findings de review e recomendações priorizadas na página de resultado existente.
- Dois novos prompts versionados: `code-review-v1.txt` e `test-generation-v1.txt`.

## Capabilities

### New Capabilities

- `ai-code-review`: revisão com IA da alteração (diff/descrição) apoiada em guidelines e regras de negócio recuperadas, com findings estruturados, validados e persistidos com registro de prompt/resultado.
- `ai-test-generation`: geração e refinamento de recomendações de teste com IA a partir dos findings, sempre como recomendação (nunca aplicada automaticamente), com justificativa e registro de prompt/resultado.
- `risk-based-testing`: matriz de priorização de testes por Impact × Likelihood aplicada deterministicamente pela aplicação; recomendações priorizadas com justificativa.

### Modified Capabilities

- `analysis-memory`: a análise persistida passa a incluir os registros QA (prompt, resultado, findings, recomendações priorizadas), recuperáveis por solicitação e em buscas históricas.
- `web-ui`: a página de resultado passa a exibir os findings do code review e as recomendações de teste priorizadas por risco.

## Impact

- **Domínio/persistência:** novas entidades QA (`QaReviewRecord`, `QaFinding`, `QaTestRecommendation`) ligadas a `ChangeAnalysis`.
- **Pipeline:** integração da etapa QA no fluxo de análise após `analyze_impact` (sem duplicar regras nos nós do grafo).
- **IA:** novos prompts versionados em `resources/prompts/`; saída estruturada + validação + retry limitado (reuso da infraestrutura existente).
- **Web:** `result.html` com seção de QA.
- **Testes:** unitários (serviços QA, matriz de risco, persistência), MockMvc (exibição), E2E com exemplo real (desconto VIP 10%→15%) e evidência `docs/evidence/09-ai-code-review.png`.

## Non-goals

- Não executar/gerar código de teste automaticamente no repositório.
- Não criar novo agente, novo modelo de dados de risco ou novas tools.
- Não refinar prompts v1→v2 (change 10) nem análise de logs/anomalias (change 09).
