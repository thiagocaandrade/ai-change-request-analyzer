# trace-viewer Specification

## Purpose

Página web que reconstrói uma execução pelo trace_id a partir dos eventos de auditoria persistidos, mostrando etapas, duração, tools, modelo e documentos recuperados.

## Requirements

### Requirement: Execução reconstruída por trace_id na web

A página de trace DEVE permitir informar um trace_id e exibir os eventos da execução em ordem cronológica com etapa (node), evento, duração, status, erro (quando houver), tool, modelo e momento de cada evento.

#### Scenario: Eventos listados em ordem cronológica

- **WHEN** o usuário consulta um trace_id com eventos registrados
- **THEN** a página lista os eventos do primeiro ao último com etapa, evento, duração, status, tool, modelo e momento

#### Scenario: Etapas com duração exibida

- **WHEN** um evento registra duração da etapa
- **THEN** a página exibe a duração em ms legível ao lado do evento

### Requirement: Documentos recuperados exibidos no trace

Quando os eventos da execução registram as fontes dos documentos recuperados pelo RAG, a página de trace DEVE exibi-los associados à etapa de recuperação, indicando origem e score quando disponíveis.

#### Scenario: Fontes recuperadas exibidas

- **WHEN** a execução consultada possui eventos de recuperação com fontes de documentos registradas
- **THEN** a página exibe essas fontes associadas à etapa de recuperação, com score quando disponível

#### Scenario: Nenhum documento recuperado

- **WHEN** a execução consultada não registra documentos recuperados (busca degradada ou sem hits)
- **THEN** a página indica que nenhum documento foi recuperado, sem quebrar a renderização

### Requirement: Trace inexistente na web

Consultar um trace_id sem eventos DEVE exibir mensagem clara de não encontrado na página, sem expor detalhes internos.

#### Scenario: Trace sem eventos

- **WHEN** o usuário consulta um trace_id sem eventos registrados
- **THEN** a página exibe mensagem de trace não encontrado

### Requirement: Nenhum segredo exibido no trace

A página de trace DEVE exibir apenas os campos registrados dos eventos; nenhum campo DEVE conter chave, token ou segredo.

#### Scenario: Eventos sem segredos renderizados

- **WHEN** a página de trace renderiza eventos de uma execução
- **THEN** nenhum segredo, chave ou token aparece no conteúdo exibido
