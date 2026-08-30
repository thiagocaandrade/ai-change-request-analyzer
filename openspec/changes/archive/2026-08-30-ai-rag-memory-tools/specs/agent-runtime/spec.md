## MODIFIED Requirements

### Requirement: Análise completa via LangGraph

O serviço agente DEVE aceitar solicitações de mudança em `POST /analyze` e executar o grafo LangGraph completo com estado compartilhado — treze nós com paralelização na coleta de evidências, branching por risco e condição de parada — em que as etapas cognitivas e de coleta obtêm resultado real da aplicação (classificação, achados de código, documentos de conhecimento e histórico, produzidos por IA, tools, RAG e memória) e retornam resposta estruturada JSON com identificador, status e resultado tipado.

#### Scenario: Análise executada com sucesso

- **WHEN** uma solicitação válida contendo texto e identificador é enviada ao agente
- **THEN** o agente executa o grafo completo e retorna JSON com o identificador da solicitação, status "completed" e resultado com texto processado, resumo, classificação e avaliação de risco (nível LOW/MEDIUM/HIGH, confidence entre 0 e 1 e racional) produzidos com evidência real

#### Scenario: Risco HIGH pendente de aprovação

- **WHEN** a avaliação de risco da análise é HIGH
- **THEN** o agente retorna status "pending_approval" e resultado com aprovação exigida e estado PENDING, sem nunca aprovar por conta própria

#### Scenario: Resultado inválido esgotado

- **WHEN** o resultado final permanece inválido após as tentativas limitadas de correção
- **THEN** o agente retorna status "failed" com erros estruturados, sem loop infinito

#### Scenario: Entrada inválida

- **WHEN** uma solicitação sem texto é enviada ao agente
- **THEN** o agente retorna erro estruturado HTTP 400 sem executar o grafo

#### Scenario: Evidência real nas etapas

- **WHEN** o grafo executa com a aplicação disponível
- **THEN** a classificação, os achados de código, os documentos e o histórico do resultado vêm da aplicação (IA, tools, RAG e memória), não de stubs determinísticos
