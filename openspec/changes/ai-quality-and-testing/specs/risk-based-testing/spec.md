## Purpose

Priorização de testes baseada em risco: uma matriz Impact × Likelihood classifica riscos de teste e a aplicação deriva determinísticamente as prioridades — a IA apenas sugere impacto e probabilidade.

## ADDED Requirements

### Requirement: Matriz de risco Impact × Likelihood

O sistema DEVE classificar os riscos de teste em uma matriz de duas dimensões — Impacto e Probabilidade — combinadas em prioridade pela aplicação, de forma determinística e sem depender de decisão do modelo.

#### Scenario: Prioridade derivada determinísticamente

- **WHEN** um risco de teste possui impacto e probabilidade sugeridos
- **THEN** a prioridade final é calculada pela aplicação com regra fixa sobre a combinação Impact × Likelihood

#### Scenario: Sugestão do modelo não decide a prioridade

- **WHEN** o modelo sugere impacto ou probabilidade inconsistentes com as evidências da análise
- **THEN** a aplicação mantém a regra determinística e a prioridade deriva das dimensões avaliadas, não da sugestão direta

### Requirement: Categorias de risco obrigatórias na priorização

A matriz DEVE priorizar explicitamente ao menos estas categorias: prompt injection, acesso não autorizado às tools, classificação incorreta de HIGH/LOW e regressão de regra de negócio financeira.

#### Scenario: Categorias avaliadas

- **WHEN** a priorização de testes é executada para uma alteração
- **THEN** cada uma das categorias obrigatórias é avaliada quanto a impacto e probabilidade, e as que se aplicam à alteração recebem prioridade derivada da matriz

### Requirement: Pelo menos um teste priorizado com justificativa

Toda análise DEVE entregar pelo menos um teste priorizado, acompanhado da justificativa de prioridade (categoria de risco, impacto, probabilidade e a combinação aplicada).

#### Scenario: Teste priorizado entregue

- **WHEN** uma análise é concluída com riscos identificados
- **THEN** ao menos uma recomendação de teste é entregue com prioridade calculada e justificativa da matriz aplicada

#### Scenario: Sem riscos identificados

- **WHEN** a análise não identifica riscos aplicáveis
- **THEN** o sistema indica explicitamente que nenhum teste priorizado foi necessário, com a matriz avaliada registrada
