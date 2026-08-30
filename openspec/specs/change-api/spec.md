# change-api Specification

## Purpose

API REST tipada em `/api/change-requests` para criar solicitações de mudança, registrar análises estruturadas e consultá-las, com validação de entrada e respostas de erro consistentes.

## Requirements

### Requirement: Criação de solicitação de mudança

O sistema DEVE aceitar `POST /api/change-requests` com o texto não vazio da solicitação e DEVE retornar identificador, status inicial e trace_id; textos vazios DEVE ser rejeitados com erro de validação.

#### Scenario: Solicitação criada

- **WHEN** o usuário envia uma solicitação com texto não vazio
- **THEN** o sistema persiste a solicitação e retorna 201 com identificador, status inicial e trace_id

#### Scenario: Texto vazio rejeitado

- **WHEN** o usuário envia uma solicitação com texto vazio ou ausente
- **THEN** o sistema retorna 400 com erro de validação e nada é persistido

### Requirement: Registro de análise estruturada

O sistema DEVE aceitar `POST /api/change-requests/{id}/analysis` com payload tipado contendo achados de impacto, avaliação de risco e recomendações de teste, validar o schema, aplicar as regras determinísticas de risco e persistir a análise vinculada à solicitação; payloads inválidos DEVE ser rejeitados com 400.

#### Scenario: Análise registrada

- **WHEN** um payload válido de análise é enviado para uma solicitação existente
- **THEN** a análise é persistida vinculada à solicitação e retornada com risco classificado

#### Scenario: Análise HIGH implica aprovação pendente

- **WHEN** a análise registrada possui risco HIGH
- **THEN** a resposta indica aprovação exigida com estado PENDING

#### Scenario: Payload inválido rejeitado

- **WHEN** o payload viola o schema (campo obrigatório ausente, tipo incorreto ou confidence fora de [0, 1])
- **THEN** o sistema retorna 400 com erro estruturado e nada é persistido

#### Scenario: Solicitação inexistente

- **WHEN** a análise é enviada para um identificador de solicitação inexistente
- **THEN** o sistema retorna 404 com erro estruturado

### Requirement: Consulta de solicitação e análise

O sistema DEVE expor `GET /api/change-requests/{id}` retornando a solicitação com resumo da análise e `GET /api/change-requests/{id}/analysis` retornando a análise completa tipada; identificadores inexistentes DEVE retornar 404.

#### Scenario: Solicitação e análise consultadas

- **WHEN** o usuário consulta uma solicitação existente com análise registrada
- **THEN** o sistema retorna a solicitação com resumo e a análise completa tipada

#### Scenario: Consulta inexistente

- **WHEN** o usuário consulta um identificador inexistente
- **THEN** o sistema retorna 404 com erro estruturado

### Requirement: Aprovação humana de análise

O sistema DEVE aceitar `POST /api/change-requests/{id}/approval` com payload contendo approver e decisão APPROVED ou REJECTED, registrar a decisão com approver, momento e trace_id e retornar o estado atualizado; identificadores inexistentes DEVE retornar 404, decisões inválidas 400 e decisões fora do estado PENDING 409.

#### Scenario: Aprovação registrada

- **WHEN** um humano envia decisão válida para uma solicitação com aprovação exigida em PENDING
- **THEN** o sistema registra a decisão e retorna 200 com o estado atualizado (APPROVED ou REJECTED) e os dados do registro

#### Scenario: Solicitação inexistente

- **WHEN** a decisão é enviada para um identificador de solicitação inexistente
- **THEN** o sistema retorna 404 com erro estruturado

#### Scenario: Decisão inválida

- **WHEN** o payload contém decisão diferente de APPROVED ou REJECTED, ou approver ausente
- **THEN** o sistema retorna 400 com erro de validação e nada é registrado

#### Scenario: Aprovação já decidida

- **WHEN** a decisão é enviada para uma solicitação cuja aprovação já está APPROVED ou REJECTED, ou cuja análise não exige aprovação
- **THEN** o sistema retorna 409 com erro estruturado e não registra nova decisão

### Requirement: Avaliação de segurança na resposta de análise

A resposta de `GET /api/change-requests/{id}/analysis` DEVE incluir a avaliação de segurança da análise — indicador de detecção e lista de eventos com tipo, fonte, evidência e ação — quando houver eventos registrados.

#### Scenario: Análise com eventos de segurança

- **WHEN** o usuário consulta a análise de uma solicitação com eventos de segurança registrados
- **THEN** a resposta inclui a avaliação de segurança com indicador de detecção e a lista completa de eventos

#### Scenario: Análise sem eventos

- **WHEN** o usuário consulta a análise de uma solicitação sem eventos de segurança
- **THEN** a resposta indica avaliação de segurança sem detecção e lista vazia
