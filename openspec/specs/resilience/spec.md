# resilience Specification

## Purpose

Política única de resiliência para todas as integrações externas da aplicação — LLM, MCP, RAG, tools e agente — com timeout, retry limitado, backoff, fallback explícito e registro de cada tentativa.

## Requirements

### Requirement: Política única de resiliência

Toda integração externa DEVE executar com timeout configurável, retry limitado (no máximo 2 retries), backoff entre tentativas e fallback explícito quando o limite se esgota; cada tentativa DEVE ser registrada em log estruturado e em evento de auditoria com trace_id.

#### Scenario: Recuperação com retry

- **WHEN** a primeira tentativa de uma integração falha e a seguinte responde dentro do timeout
- **THEN** a integração retorna o resultado da tentativa bem-sucedida e as tentativas são registradas

#### Scenario: Limite esgotado com fallback

- **WHEN** todas as tentativas de uma integração falham
- **THEN** a integração registra erro estruturado e retorna fallback explícito marcado como degradado

#### Scenario: Cada tentativa registrada

- **WHEN** uma integração executa mais de uma tentativa
- **THEN** cada tentativa aparece em log estruturado e em evento de auditoria com número da tentativa, erro e trace_id

### Requirement: Falha crítica não escondida

Falha de uma integração crítica após o limite DEVE produzir erro estruturado com a causa registrada; o fallback DEVE ser explícito e marcado, nunca um sucesso silencioso.

#### Scenario: Agente indisponível

- **WHEN** o agente não responde dentro do timeout após os retries
- **THEN** o sistema registra a causa e a solicitação termina em estado de falha estruturado, sem simular sucesso

#### Scenario: Fallback marcado como degradado

- **WHEN** uma integração usa fallback
- **THEN** o resultado carrega marcação explícita de degradação visível no log, no evento e na resposta

### Requirement: Backoff entre tentativas

Entre duas tentativas de uma integração DEVE haver espera crescente (backoff) configurável, evitando retries imediatos.

#### Scenario: Espera entre tentativas

- **WHEN** uma integração falha e vai reexecutar
- **THEN** há espera configurável antes da tentativa seguinte, maior que zero e limitada
