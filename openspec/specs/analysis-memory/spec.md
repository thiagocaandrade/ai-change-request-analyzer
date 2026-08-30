# analysis-memory Specification

## Purpose

Memória persistente de análises anteriores: recupera mudanças e análises passadas por semelhança de termos, componente, regra de negócio e classificação, para que a análise nova reutilize evidências históricas.

## Requirements

### Requirement: Busca histórica por múltiplos critérios

A aplicação DEVE permitir buscar análises anteriores por termos semelhantes no texto, por componente afetado, por regra de negócio e por classificação, retornando identificador e resumo de cada resultado.

#### Scenario: Busca por termos

- **WHEN** uma busca usa termos presentes em mudanças anteriores
- **THEN** as análises anteriores correspondentes são retornadas com identificador e resumo

#### Scenario: Busca por componente ou regra

- **WHEN** a busca usa um componente ou regra de negócio
- **THEN** somente análises relacionadas a esse componente ou regra são retornadas

### Requirement: Persistência completa da análise

Toda análise concluída DEVE ser persistida — solicitação, achados, avaliação de risco, recomendações de teste e aprovação — e permanecer consultável em buscas posteriores.

#### Scenario: Análise persistida consultável

- **WHEN** uma análise é concluída e persistida
- **THEN** ela aparece em buscas históricas futuras com seu identificador

### Requirement: Falha de memória não interrompe a análise

Quando a busca histórica falha, a análise DEVE seguir com histórico vazio marcado e a falha registrada com trace_id.

#### Scenario: Histórico indisponível

- **WHEN** a busca histórica falha
- **THEN** a análise continua com lista vazia e a falha registrada com trace_id

### Requirement: Registros de QA persistidos com a análise

A análise concluída DEVE persistir também os registros de QA — revisão (prompt versionado, resultado, risco) e geração/refinamento de testes (prompt versionado, resultado, iterações) — vinculados à solicitação e recuperáveis em buscas históricas.

#### Scenario: Registros QA recuperáveis

- **WHEN** uma análise com etapa de QA é concluída e persistida
- **THEN** os registros de revisão e de geração de testes são recuperáveis pela solicitação, com prompt, resultado e trace_id

#### Scenario: Busca histórica inclui QA

- **WHEN** uma busca histórica recupera uma análise com registros de QA
- **THEN** o resultado da busca permite acessar os registros QA daquela análise
