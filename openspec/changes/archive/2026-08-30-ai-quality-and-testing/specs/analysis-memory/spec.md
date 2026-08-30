## ADDED Requirements

### Requirement: Registros de QA persistidos com a análise

A análise concluída DEVE persistir também os registros de QA — revisão (prompt versionado, resultado, risco) e geração/refinamento de testes (prompt versionado, resultado, iterações) — vinculados à solicitação e recuperáveis em buscas históricas.

#### Scenario: Registros QA recuperáveis

- **WHEN** uma análise com etapa de QA é concluída e persistida
- **THEN** os registros de revisão e de geração de testes são recuperáveis pela solicitação, com prompt, resultado e trace_id

#### Scenario: Busca histórica inclui QA

- **WHEN** uma busca histórica recupera uma análise com registros de QA
- **THEN** o resultado da busca permite acessar os registros QA daquela análise
