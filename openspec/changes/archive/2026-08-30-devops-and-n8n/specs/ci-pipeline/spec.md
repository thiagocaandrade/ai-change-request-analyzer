# ci-pipeline Delta

## MODIFIED Requirements

### Requirement: Pipeline de lint, testes e build

O repositório DEVE possuir workflow de CI que execute, a cada push ou pull request, os estágios de checkout, setup de Java, compilação, testes unitários, testes de integração, testes E2E e verificações de qualidade (lint/quality checks) dos serviços Java e Python, falhando quando qualquer teste crítico falhar.

#### Scenario: Build verde

- **WHEN** um push válido é feito para as branches principais ou um pull request é aberto
- **THEN** o pipeline executa todos os estágios (compile, unit, integration, E2E, quality) e termina com sucesso

#### Scenario: Falha detectada

- **WHEN** o código contém erro de lint, teste falhando ou build quebrado
- **THEN** o pipeline falha na etapa correspondente e não prossegue para os estágios seguintes

## ADDED Requirements

### Requirement: Imagem Docker publicável

O pipeline DEVE construir a imagem Docker da aplicação após os estágios de teste e de qualidade.

#### Scenario: Imagem construída

- **WHEN** todos os estágios anteriores passam
- **THEN** o pipeline constrói a imagem Docker da aplicação com sucesso

### Requirement: Artefatos de log analisáveis por IA

O pipeline DEVE publicar como artefatos os logs de pelo menos 2 etapas (build.log e test.log), permitindo análise posterior por IA.

#### Scenario: Logs disponíveis

- **WHEN** o pipeline termina, com sucesso ou falha
- **THEN** build.log e test.log das etapas correspondentes estão disponíveis como artefatos da execução
