## MODIFIED Requirements

### Requirement: Timeout em chamadas ao modelo

Toda chamada ao modelo DEVE possuir timeout configurável; em caso de estouro ou falha, a falha DEVE ser tratada com retry limitado com backoff entre tentativas e fallback degradado, registrando cada tentativa em log estruturado com trace_id.

#### Scenario: Timeout tratado

- **WHEN** o modelo não responde dentro do timeout após os retries
- **THEN** a falha é registrada com trace_id e a análise segue degradada com fallback explícito

#### Scenario: Tentativas com backoff registradas

- **WHEN** uma chamada ao modelo falha e é reexecutada
- **THEN** há backoff entre as tentativas e cada tentativa é registrada com trace_id
