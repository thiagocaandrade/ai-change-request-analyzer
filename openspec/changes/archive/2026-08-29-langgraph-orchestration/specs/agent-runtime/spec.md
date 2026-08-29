## RENAMED Requirements

### Requirement: Análise mínima via LangGraph

FROM: `Análise mínima via LangGraph`
TO: `Análise completa via LangGraph`

## MODIFIED Requirements

### Requirement: Análise completa via LangGraph

O serviço agente DEVE aceitar solicitações de mudança em `POST /analyze` e executar o grafo LangGraph completo com estado compartilhado — treze nós com paralelização na coleta de evidências, branching por risco e condição de parada — retornando resposta estruturada JSON com identificador, status e resultado tipado.

#### Scenario: Análise executada com sucesso

- **WHEN** uma solicitação válida contendo texto e identificador é enviada ao agente
- **THEN** o agente executa o grafo completo e retorna JSON com o identificador da solicitação, status "completed" e resultado com texto processado, resumo, classificação e avaliação de risco (nível LOW/MEDIUM/HIGH, confidence entre 0 e 1 e racional)

#### Scenario: Risco HIGH pendente de aprovação

- **WHEN** a avaliação de risco da análise é HIGH
- **THEN** o agente retorna status "pending_approval" e resultado com aprovação exigida e estado PENDING, sem nunca aprovar por conta própria

#### Scenario: Resultado inválido esgotado

- **WHEN** o resultado final permanece inválido após as tentativas limitadas de correção
- **THEN** o agente retorna status "failed" com erros estruturados, sem loop infinito

#### Scenario: Entrada inválida

- **WHEN** uma solicitação sem texto é enviada ao agente
- **THEN** o agente retorna erro estruturado HTTP 400 sem executar o grafo
