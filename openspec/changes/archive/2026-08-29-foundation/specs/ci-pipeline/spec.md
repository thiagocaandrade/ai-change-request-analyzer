## Purpose

Pipeline de integração contínua que valida automaticamente lint, testes e build dos serviços Java e Python a cada push ou pull request.

## ADDED Requirements

### Requirement: Pipeline de lint, testes e build

O repositório DEVE possuir workflow de CI que execute lint, testes e build dos serviços Java e Python.

#### Scenario: Build verde

- **WHEN** um push válido é feito para as branches principais ou um pull request é aberto
- **THEN** o pipeline executa lint, testes e build de ambos os serviços e termina com sucesso

#### Scenario: Falha detectada

- **WHEN** o código contém erro de lint, teste falhando ou build quebrado
- **THEN** o pipeline falha na etapa correspondente
