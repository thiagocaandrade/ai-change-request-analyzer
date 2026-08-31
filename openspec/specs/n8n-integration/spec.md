# n8n-integration Specification

## Purpose

Integração com n8n para automação externa: um workflow exportável dispara a análise de uma solicitação de mudança via webhook e notifica quando o risco resultante é HIGH, mantendo toda lógica de negócio no backend.

## Requirements

### Requirement: Workflow n8n exportável

O repositório DEVE conter um workflow n8n exportável (`n8n/workflow.json`) composto por: webhook de entrada, chamada a `POST /api/change-requests/analyze`, leitura do resultado e condição sobre o risco.

#### Scenario: Workflow importável

- **WHEN** o arquivo `n8n/workflow.json` é importado no n8n
- **THEN** o workflow aparece com os nós de webhook, requisição HTTP, leitura do resultado e condição sobre risk

### Requirement: Disparo de análise via webhook

O webhook DEVE aceitar a descrição de uma solicitação de mudança e encaminhá-la ao endpoint de análise; a resposta DEVE ser repassada sem reescrita de lógica.

#### Scenario: Webhook dispara análise

- **WHEN** o webhook recebe uma solicitação de mudança
- **THEN** o backend executa a análise completa e o resultado retorna ao fluxo do n8n

### Requirement: Notificação somente para risco HIGH

A condição do workflow DEVE notificar apenas quando o risco da análise é HIGH; riscos LOW e MEDIUM DEVE seguir sem notificação.

#### Scenario: Risco HIGH notifica

- **WHEN** o resultado da análise tem risk HIGH
- **THEN** o workflow produz saída de notificação

#### Scenario: Risco não HIGH não notifica

- **WHEN** o resultado tem risk LOW ou MEDIUM
- **THEN** o workflow conclui sem notificação

### Requirement: Lógica de negócio no backend

Toda regra de negócio DEVE permanecer no Spring Boot; o workflow n8n DEVE conter apenas integração e roteamento.

#### Scenario: Workflow sem lógica de negócio

- **WHEN** o workflow é inspecionado
- **THEN** nenhum nó implementa regra de análise, risco ou aprovação além do roteamento HIGH/notificação

### Requirement: Documentação do workflow

A integração DEVE ser documentada com trigger, endpoint, payload, resposta, condição, saída e evidência.

#### Scenario: Documentação completa

- **WHEN** o operador lê a documentação do workflow
- **THEN** encontra trigger, endpoint, payload de entrada, formato da resposta, condição de risco, saída e evidência de execução
