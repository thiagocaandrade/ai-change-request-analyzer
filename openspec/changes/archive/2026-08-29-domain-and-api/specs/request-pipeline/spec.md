## MODIFIED Requirements

### Requirement: Recepção e delegação de solicitação

O sistema DEVE aceitar `POST /api/change-requests` com o texto da solicitação de mudança, gerar um trace_id único, delegar a análise ao agente com timeout e retry limitado, e persistir a solicitação com seu status e, quando concluída, a análise estruturada tipada.

#### Scenario: Solicitação aceita

- **WHEN** o usuário envia uma solicitação de mudança válida
- **THEN** o sistema retorna o identificador e o status inicial da solicitação, registrando-a com trace_id

#### Scenario: Agente indisponível

- **WHEN** o agente não responde dentro do timeout após as tentativas configuradas
- **THEN** o sistema retorna erro estruturado e marca a solicitação como "failed" com a causa registrada

### Requirement: Consulta de status e resultado

O sistema DEVE expor endpoint em `/api/change-requests/{id}` para consultar o status e o resultado de uma solicitação persistida, retornando a análise estruturada tipada quando concluída.

#### Scenario: Status consultado

- **WHEN** o usuário consulta o status de uma solicitação existente
- **THEN** o sistema retorna o estado atual persistido e, se concluída, a análise estruturada tipada
