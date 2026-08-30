# ai-capabilities Specification

## Purpose

Camada de inteligência que executa as etapas cognitivas da análise — classificação, impacto, risco e plano de testes — via modelo de IA com prompts versionados, saída estruturada e validada, e retry limitado.

## Requirements

### Requirement: Saída de LLM estruturada e validada

Toda saída de modelo de IA que entra no domínio DEVE ser convertida em objeto tipado e validada antes de uso; saída inválida DEVE ser descartada e nunca persistida.

#### Scenario: Saída válida aceita

- **WHEN** o modelo retorna uma resposta conforme o schema esperado
- **THEN** a resposta é convertida em objeto tipado e usada na análise

#### Scenario: Saída inválida descartada

- **WHEN** o modelo retorna uma resposta fora do schema esperado
- **THEN** a resposta é descartada, não é persistida e uma nova tentativa limitada é realizada

### Requirement: Retry limitado em geração inválida

Quando a saída do modelo é inválida, a aplicação DEVE reexecutar a geração com no máximo 2 retries e, esgotado o limite, registrar erro estruturado e usar fallback explícito, sem loop infinito.

#### Scenario: Recuperação dentro do limite

- **WHEN** a primeira saída é inválida e a tentativa seguinte é válida
- **THEN** a saída válida é usada e a recuperação é registrada

#### Scenario: Limite esgotado com fallback

- **WHEN** todas as tentativas produzem saída inválida
- **THEN** a etapa registra erro estruturado e retorna fallback determinístico marcado, sem nova reexecução

### Requirement: Prompts versionados

Os prompts do sistema DEVE estar em arquivos versionados em `resources/prompts/<etapa>-v<N>.txt`, carregados por identificador de etapa e versão; nenhum prompt de produção DEVE estar embutido em código.

#### Scenario: Prompt carregado por versão

- **WHEN** a aplicação executa uma etapa da análise
- **THEN** o prompt usado vem do arquivo versionado correspondente à etapa

### Requirement: Conteúdo recuperado não é instrução

Conteúdo recuperado (código, documentos, histórico) DEVE ser entregue ao modelo apenas como dado, em seção separada e delimitada, e nunca DEVE substituir ou sobrescrever o prompt do sistema.

#### Scenario: Instrução em dado não altera comportamento

- **WHEN** o conteúdo recuperado contém instrução dirigida ao modelo
- **THEN** a aplicação mantém o prompt do sistema e a instrução injetada não altera a saída de domínio

### Requirement: Segredos nunca em logs ou saídas

A camada de IA DEVE operar sem expor chaves, tokens ou segredos em logs, erros ou respostas.

#### Scenario: Erro sem segredo

- **WHEN** uma chamada ao modelo falha
- **THEN** o erro registrado não contém chave, token ou segredo

### Requirement: Timeout em chamadas ao modelo

Toda chamada ao modelo DEVE possuir timeout configurável; em caso de estouro ou falha, a falha DEVE ser tratada com retry limitado com backoff entre tentativas e fallback degradado, registrando cada tentativa em log estruturado com trace_id.

#### Scenario: Timeout tratado

- **WHEN** o modelo não responde dentro do timeout após os retries
- **THEN** a falha é registrada com trace_id e a análise segue degradada com fallback explícito

#### Scenario: Tentativas com backoff registradas

- **WHEN** uma chamada ao modelo falha e é reexecutada
- **THEN** há backoff entre as tentativas e cada tentativa é registrada com trace_id

### Requirement: Sem modelo configurado, análise degradada

Quando nenhum modelo de IA está configurado, a aplicação DEVE executar as etapas com fallback determinístico marcado (ex.: risco MEDIUM com racional "analysis_unavailable"), mantendo o fluxo funcional.

#### Scenario: Análise sem modelo

- **WHEN** a aplicação não possui modelo de IA configurado
- **THEN** as etapas retornam fallback determinístico marcado e o fluxo conclui sem erro

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
