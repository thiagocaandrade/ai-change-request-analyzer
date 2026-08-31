# anomaly-detection Specification

## Purpose

Detecção determinística de anomalia em métricas de execução e de tendência de falha em execuções de pipeline, com baseline histórico, desvio e severidade calculados sem depender de modelo de IA.

## ADDED Requirements

### Requirement: Detecção de anomalia por desvio de baseline

O serviço DEVE manter baseline histórico de uma métrica e, para cada nova observação, calcular o desvio em relação ao baseline e classificar a severidade (LOW/MEDIUM/HIGH) com regras determinísticas.

#### Scenario: Desvio significativo detectado

- **WHEN** uma observação supera o baseline por margem acima do limiar configurado (ex.: baseline 400ms, observado 2800ms)
- **THEN** o sistema registra a anomalia com baseline, valor observado, desvio e severidade correspondente

#### Scenario: Observação dentro da faixa normal

- **WHEN** o desvio está abaixo do limiar configurado
- **THEN** nenhuma anomalia é registrada

### Requirement: Tendência de falha em execuções

O serviço DEVE acompanhar o resultado das execuções recentes e sinalizar tendência de falha quando a taxa de falha cresce ao longo de no mínimo 5 execuções recentes.

#### Scenario: Taxa de falha crescente

- **WHEN** a taxa de falha das últimas 5 execuções é crescente
- **THEN** o sistema registra tendência de falha com a sequência de resultados e a taxa calculada

#### Scenario: Sem tendência de falha

- **WHEN** a taxa de falha não cresce nas execuções recentes
- **THEN** nenhuma tendência de falha é registrada

### Requirement: Estatística simples e determinística

A detecção DEVE usar apenas estatística simples determinística (média, desvio, taxa); nenhum modelo de IA DEVE participar do cálculo de severidade ou de tendência.

#### Scenario: Cálculo reprodutível

- **WHEN** a mesma sequência de observações é analisada duas vezes
- **THEN** os resultados (desvio, severidade, tendência) são idênticos

### Requirement: Registro correlacionado por trace_id

Toda detecção DEVE registrar evento estruturado com trace_id, métrica, baseline, valor observado, desvio e severidade.

#### Scenario: Anomalia registrada

- **WHEN** uma anomalia é detectada
- **THEN** o evento persistido contém trace_id, métrica, baseline, valor observado, desvio e severidade
