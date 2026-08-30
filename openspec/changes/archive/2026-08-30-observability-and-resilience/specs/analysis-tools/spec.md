## MODIFIED Requirements

### Requirement: Timeout, retry e logs das tools

Cada execução de tool DEVE possuir timeout configurável, retry limitado (máx. 2) com backoff entre tentativas e registro de log estruturado com trace_id, incluindo cada tentativa; falha após os retries DEVE ser registrada sem interromper a análise.

#### Scenario: Falha de tool registrada

- **WHEN** uma tool falha após os retries
- **THEN** a falha é registrada com trace_id e a análise segue degradada

#### Scenario: Tentativas com backoff registradas

- **WHEN** uma tool falha e é reexecutada
- **THEN** há backoff entre as tentativas e cada tentativa é registrada com trace_id
