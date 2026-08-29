## Purpose

Serviço agente em Python que executa o fluxo LangGraph e expõe endpoints HTTP para análise de solicitações de mudança e verificação de saúde.

## ADDED Requirements

### Requirement: Health check do agente

O serviço agente DEVE expor um endpoint de health que responda 200 com status operacional quando o processo estiver em execução.

#### Scenario: Agente saudável

- **WHEN** uma requisição GET é enviada ao endpoint de health do agente
- **THEN** a resposta possui código HTTP 200 e corpo indicando que o serviço está operacional

### Requirement: Análise mínima via LangGraph

O serviço agente DEVE aceitar solicitações de mudança em `POST /analyze` e executar um fluxo modelado em LangGraph com estado compartilhado e ao menos dois nós sequenciais determinísticos, retornando resposta estruturada JSON.

#### Scenario: Análise executada com sucesso

- **WHEN** uma solicitação válida contendo texto e identificador é enviada ao agente
- **THEN** o agente executa o grafo e retorna JSON com o identificador da solicitação, status "completed" e o texto processado

#### Scenario: Entrada inválida

- **WHEN** uma solicitação sem texto é enviada ao agente
- **THEN** o agente retorna erro estruturado HTTP 400 sem executar o grafo

### Requirement: Propagação de trace_id nos logs do agente

O agente DEVE registrar logs estruturados incluindo o trace_id recebido no cabeçalho da requisição e, quando ausente, gerar um próprio.

#### Scenario: Log com trace_id correlacionado

- **WHEN** o agente processa uma análise com cabeçalho X-Trace-Id presente
- **THEN** cada registro de log daquela execução contém o mesmo trace_id
