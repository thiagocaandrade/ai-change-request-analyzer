## ADDED Requirements

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
