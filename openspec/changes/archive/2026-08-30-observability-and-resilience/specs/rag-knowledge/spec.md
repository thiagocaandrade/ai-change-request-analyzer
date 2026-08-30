## MODIFIED Requirements

### Requirement: Disponibilidade degradada

A busca semântica DEVE possuir timeout e retry limitado com backoff; quando indisponível (base vazia, falha ou estouro de tempo após os retries), a análise DEVE seguir com contexto vazio marcado, sem erro fatal.

#### Scenario: RAG indisponível

- **WHEN** a busca semântica falha ou a base está vazia
- **THEN** a análise continua com lista vazia e a falha registrada com trace_id

#### Scenario: Timeout com retry

- **WHEN** a busca excede o timeout nas primeiras tentativas
- **THEN** a busca reexecuta com backoff limitado e, esgotado o limite, retorna lista vazia marcada como degradada
