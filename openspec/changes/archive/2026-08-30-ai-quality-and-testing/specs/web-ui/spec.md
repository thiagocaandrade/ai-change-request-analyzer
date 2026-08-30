## ADDED Requirements

### Requirement: Resultado de QA exibido na página de resultado

A página de resultado DEVE exibir os findings do code review com IA e as recomendações de teste priorizadas pela matriz de risco (prioridade e justificativa), além do conteúdo já exibido; conteúdo de QA DEVE ser renderizado escapado como os demais dados não confiáveis.

#### Scenario: Findings e recomendações exibidos

- **WHEN** a página de resultado de uma análise com etapa de QA concluída é aberta
- **THEN** a página exibe os findings do review e as recomendações de teste com prioridade e justificativa

#### Scenario: QA degradada exibida sem quebrar a página

- **WHEN** a etapa de QA seguiu degradada (sem modelo ou com fallback)
- **THEN** a página indica explicitamente que o QA está indisponível/degradado, sem quebrar a renderização

#### Scenario: Conteúdo de QA escapado

- **WHEN** um finding ou recomendação contém marcação HTML ou script
- **THEN** a página exibe o conteúdo literalmente (escapado), sem interpretá-lo como HTML
