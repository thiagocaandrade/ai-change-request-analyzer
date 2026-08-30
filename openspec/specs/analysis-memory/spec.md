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
