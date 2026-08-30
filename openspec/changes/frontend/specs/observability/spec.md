## MODIFIED Requirements

### Requirement: Reconstrução de execução por trace_id

A aplicação DEVE persistir eventos de execução (trace_id, request_id, node, event, duration_ms, status, error, risk, tool, model, detalhes opcionais e momento) e expor `GET /api/traces/{traceId}` retornando os eventos da execução em ordem cronológica; trace_id inexistente DEVE retornar 404. Eventos de etapas de recuperação de conhecimento DEVE incluir nos detalhes as fontes dos documentos recuperados (origem e score quando disponíveis), permitindo que a reconstrução mostre os documentos usados pela análise.

#### Scenario: Execução reconstruível

- **WHEN** uma análise concluída é consultada pelo seu trace_id
- **THEN** o endpoint retorna a sequência ordenada de eventos da execução (etapas, duração, status e erros quando houver)

#### Scenario: Documentos recuperados visíveis na reconstrução

- **WHEN** a execução registrou eventos de recuperação de conhecimento com documentos recuperados
- **THEN** os eventos correspondentes expõem as fontes desses documentos (origem e score quando disponíveis)

#### Scenario: Trace inexistente

- **WHEN** o endpoint é consultado com trace_id sem eventos registrados
- **THEN** o sistema retorna 404

#### Scenario: Evento sem segredo

- **WHEN** qualquer evento de execução é registrado ou retornado
- **THEN** nenhum campo do evento contém chave, token ou segredo
