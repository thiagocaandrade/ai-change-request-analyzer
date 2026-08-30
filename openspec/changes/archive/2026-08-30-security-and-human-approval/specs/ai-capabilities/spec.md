## ADDED Requirements

### Requirement: Análise de segurança com prompt versionado

A etapa de análise de segurança DEVE usar o prompt versionado `security-analysis-v1` carregado de `resources/prompts/`, produzir saída estruturada e validada (detecção, tipo e evidência) e, quando inválida, reexecutar com no máximo 2 retries e usar fallback determinístico marcado; a decisão final de detecção e a ação DEVE permanecer determinísticas na aplicação.

#### Scenario: Prompt carregado por versão

- **WHEN** a aplicação executa a etapa de análise de segurança
- **THEN** o prompt usado vem do arquivo versionado `security-analysis-v1` e a saída do modelo entra como dado, nunca como instrução

#### Scenario: Saída válida aceita

- **WHEN** o modelo retorna uma resposta conforme o schema de avaliação de segurança
- **THEN** a resposta é convertida em objeto tipado e contribui para a avaliação de segurança

#### Scenario: Saída inválida com fallback determinístico

- **WHEN** todas as tentativas da etapa de segurança produzem saída inválida
- **THEN** a etapa registra erro estruturado e usa fallback determinístico marcado, sem nova reexecução

#### Scenario: Sugestão do modelo não decide

- **WHEN** o modelo sugere detecção ou não detecção de conteúdo injetado
- **THEN** a decisão final de detecção, o registro do evento e a ação são aplicados deterministicamente pela aplicação, e nenhuma sugestão altera risco ou classificação
