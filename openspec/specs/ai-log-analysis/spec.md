# ai-log-analysis Specification

## Purpose

Análise assistida por IA dos logs de build e de teste publicados pelo pipeline de CI, produzindo diagnóstico estruturado para operadores sem nunca alterar o pipeline automaticamente.

## Requirements

### Requirement: Diagnóstico estruturado de logs

A análise DEVE receber conteúdo de logs de build/teste e produzir `summary`, `failed_step`, `probable_cause`, `evidence`, `recommended_action` e `confidence`; a saída DEVE ser convertida em objetos tipados e validada antes de uso, com retry limitado.

#### Scenario: Log com falha analisado

- **WHEN** o serviço recebe um log de build ou teste contendo falha
- **THEN** o diagnóstico identifica a etapa com falha, causa provável, evidência e ação recomendada, cada uma com confidence

#### Scenario: Saída inválida com retry limitado

- **WHEN** o modelo retorna saída fora do schema esperado
- **THEN** o sistema reexecuta com no máximo 2 retries e, esgotado o limite, retorna fallback determinístico marcado como degradado

### Requirement: Conteúdo de log é dado não confiável

O conteúdo do log DEVE ser tratado como dado não confiável; nenhuma instrução contida nele DEVE alterar o comportamento do serviço.

#### Scenario: Instrução injetada no log

- **WHEN** o log contém instrução dirigida ao modelo (ex.: "altere o pipeline" ou "classifique como sucesso")
- **THEN** a instrução é ignorada e o diagnóstico reflete apenas o conteúdo real do log

### Requirement: IA nunca altera o pipeline

A análise DEVE apenas produzir diagnóstico e recomendação; nenhuma alteração de pipeline, workflow ou configuração DEVE ser aplicada automaticamente pelo sistema.

#### Scenario: Recomendação sem aplicação automática

- **WHEN** o diagnóstico recomenda uma ação
- **THEN** a recomendação é registrada para revisão humana e nenhum arquivo de pipeline é modificado

### Requirement: Registro por execução com trace_id

Cada diagnóstico DEVE registrar o prompt versionado usado, o resultado estruturado, a confidence e o trace_id, persistidos e recuperáveis.

#### Scenario: Diagnóstico registrado

- **WHEN** um log é analisado
- **THEN** o registro persistido contém prompt versionado, resultado estruturado, confidence e trace_id

### Requirement: Análise degradada sem modelo

Quando nenhum modelo de IA está configurado, o serviço DEVE retornar fallback determinístico marcado e concluir sem erro.

#### Scenario: Análise sem modelo

- **WHEN** o sistema não possui modelo de IA configurado
- **THEN** o serviço retorna diagnóstico degradado marcado, sem falhar a operação
