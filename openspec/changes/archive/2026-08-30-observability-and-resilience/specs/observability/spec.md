## Purpose

Observabilidade da execução da análise: logs estruturados com campos padronizados, métricas de execução e registro de auditoria persistido que permite reconstruir qualquer execução pelo trace_id.

## ADDED Requirements

### Requirement: Logs estruturados com campos padronizados

Todos os registros de log estruturados da aplicação DEVE incluir o trace_id e, quando aplicável, request_id, node, event, duration_ms, status, error, risk, tool e model, em formato JSON; nenhum log DEVE conter segredos.

#### Scenario: Log de etapa com campos padronizados

- **WHEN** um componente registra a conclusão de uma etapa da análise
- **THEN** o registro JSON contém trace_id, node, event e duration_ms e os demais campos aplicáveis (status, error, risk, tool, model)

#### Scenario: Execução correlacionada por trace_id

- **WHEN** qualquer requisição é processada pelo sistema
- **THEN** todos os registros de log daquela execução carregam o mesmo trace_id

### Requirement: Métricas de execução

A aplicação DEVE registrar métricas de execução — analysis_duration, llm_calls, tool_calls, tool_errors, high_risk_changes, prompt_injection_count e validation_failures — via Micrometer e expô-las em endpoint de métricas do Actuator.

#### Scenario: Análise completa atualiza métricas

- **WHEN** uma análise é executada até a persistência
- **THEN** analysis_duration, llm_calls e tool_calls refletem a execução e high_risk_changes, prompt_injection_count e validation_failures refletem os eventos correspondentes

#### Scenario: Métricas consultáveis

- **WHEN** o operador consulta o endpoint de métricas
- **THEN** as sete métricas da análise estão presentes com nome e valor legíveis

### Requirement: Reconstrução de execução por trace_id

A aplicação DEVE persistir eventos de execução (trace_id, request_id, node, event, duration_ms, status, error, risk, tool, model e momento) e expor `GET /api/traces/{traceId}` retornando os eventos da execução em ordem cronológica; trace_id inexistente DEVE retornar 404.

#### Scenario: Execução reconstruível

- **WHEN** uma análise concluída é consultada pelo seu trace_id
- **THEN** o endpoint retorna a sequência ordenada de eventos da execução (etapas, duração, status e erros quando houver)

#### Scenario: Trace inexistente

- **WHEN** o endpoint é consultado com trace_id sem eventos registrados
- **THEN** o sistema retorna 404

#### Scenario: Evento sem segredo

- **WHEN** qualquer evento de execução é registrado ou retornado
- **THEN** nenhum campo do evento contém chave, token ou segredo

### Requirement: Dois sinais correlacionados

Os logs estruturados e os eventos de auditoria DEVE ser correlacionáveis pelo mesmo trace_id, permitindo investigar fluxo, decisões, erros e latência de uma execução.

#### Scenario: Sinais correlacionados

- **WHEN** uma execução é investigada
- **THEN** logs e eventos de auditoria daquela execução compartilham o mesmo trace_id
