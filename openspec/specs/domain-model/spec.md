# domain-model Specification

## Purpose

Modelo de domínio tipado e persistente para solicitações de mudança e suas análises, com regras determinísticas de risco e validação executadas pela aplicação — nunca pelo LLM.

## Requirements

### Requirement: Modelo de domínio persistente

O sistema DEVE persistir solicitações de mudança com análise estruturada composta por achados de impacto, avaliação de risco, recomendações de teste e aprovação, todas recuperáveis por identificador.

#### Scenario: Análise completa persistida

- **WHEN** uma solicitação possui análise com achados de impacto, avaliação de risco, recomendações de teste e estado de aprovação
- **THEN** todas as partes são persistidas de forma relacionada e recuperáveis pela solicitação

### Requirement: Níveis de risco e estados de aprovação

O sistema DEVE representar o risco da análise em exatamente três níveis — LOW, MEDIUM e HIGH — e o estado de aprovação em exatamente três estados — PENDING, APPROVED e REJECTED.

#### Scenario: Risco e aprovação classificados

- **WHEN** uma análise é persistida com risco avaliado e estado de aprovação
- **THEN** o risco registrado é um dos níveis LOW/MEDIUM/HIGH e a aprovação um dos estados PENDING/APPROVED/REJECTED

### Requirement: Regra determinística — HIGH exige aprovação

A aplicação DEVE marcar toda análise com risco HIGH como exigindo aprovação humana, com estado PENDING, independentemente de qualquer outra entrada — incluindo sugestões do LLM ou de conteúdo recuperado.

#### Scenario: Análise HIGH exige aprovação

- **WHEN** uma análise é avaliada com risco HIGH
- **THEN** a aplicação define aprovação exigida com estado PENDING, sem qualquer intervenção de modelo de IA

#### Scenario: Sugestão externa não afasta a regra

- **WHEN** uma entrada externa (LLM ou conteúdo recuperado) sugere risco LOW ou aprovação dispensada para uma análise cujo risco avaliado é HIGH
- **THEN** a aplicação mantém a exigência de aprovação, pois a regra é determinística e decidida pela aplicação

### Requirement: Validação de confidence

A aplicação DEVE rejeitar avaliações de risco cuja confidence esteja fora do intervalo [0, 1], retornando erro de validação estruturado, e não DEVE persistir dados inválidos.

#### Scenario: Confidence inválida rejeitada

- **WHEN** uma avaliação de risco apresenta confidence fora do intervalo [0, 1]
- **THEN** a aplicação rejeita o dado com erro de validação e nada é persistido

#### Scenario: Confidence válida aceita

- **WHEN** uma avaliação de risco apresenta confidence dentro de [0, 1]
- **THEN** a aplicação aceita e persiste a avaliação
