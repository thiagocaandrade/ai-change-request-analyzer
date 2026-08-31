## ADDED Requirements

### Requirement: Modelo configurável por variáveis de ambiente

O modelo de IA DEVE ser configurável pelas variáveis de ambiente `AI_PROVIDER`, `AI_MODEL`, `AI_TEMPERATURE` e `AI_API_KEY`; quando qualquer uma delas estiver ausente ou vazia, a aplicação DEVE usar o comportamento de fallback determinístico já especificado (análise degradada marcada), e o comportamento de ausência DEVE estar documentado no `.env.example`, que NÃO DEVE conter valores reais de segredos.

#### Scenario: Modelo configurado por env

- **WHEN** as variáveis `AI_PROVIDER`, `AI_MODEL`, `AI_TEMPERATURE` e `AI_API_KEY` estão definidas
- **THEN** o modelo de IA usado nas etapas de análise corresponde a provider, modelo e temperatura dessas variáveis

#### Scenario: Variável ausente com fallback determinístico

- **WHEN** `AI_PROVIDER`, `AI_MODEL` ou `AI_API_KEY` está ausente ou vazia
- **THEN** as etapas retornam fallback determinístico marcado e o fluxo conclui sem erro, com o comportamento documentado no `.env.example`

#### Scenario: Exemplo de env sem segredos reais

- **WHEN** o `.env.example` é consultado
- **THEN** ele lista as variáveis de configuração do modelo com valores de exemplo sem segredos reais

## MODIFIED Requirements

### Requirement: Prompts versionados

Os prompts do sistema DEVE estar em arquivos versionados em `resources/prompts/<etapa>-v<N>.txt`, carregados por identificador de etapa e versão; nenhum prompt de produção DEVE estar embutido em código. A etapa de avaliação de risco DEVE usar `risk-analysis-v2` como versão padrão, selecionada por evidência comparável (v1 vs v2 executados nos mesmos casos), mantendo `risk-analysis-v1` disponível para reprodução da comparação.

#### Scenario: Prompt carregado por versão

- **WHEN** a aplicação executa uma etapa da análise
- **THEN** o prompt usado vem do arquivo versionado correspondente à etapa

#### Scenario: Etapa de risco usa a versão refinada

- **WHEN** a aplicação executa a etapa de avaliação de risco
- **THEN** o prompt usado vem de `risk-analysis-v2`, e `risk-analysis-v1` permanece presente para comparação

#### Scenario: Seleção de versão com evidência comparável

- **WHEN** a versão padrão da etapa de risco é definida
- **THEN** a escolha da v2 é sustentada por evidência documentada do experimento v1 vs v2 nos mesmos casos
