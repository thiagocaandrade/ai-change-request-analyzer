# security-and-approval Specification

## Purpose

Avaliação de segurança de conteúdo não confiável e ciclo de aprovação humana para análises de risco HIGH, com regras determinísticas decididas pela aplicação.

## Requirements

### Requirement: Detecção determinística de conteúdo não confiável

A aplicação DEVE varrer o texto da solicitação e todo conteúdo recuperado (código, documentos de conhecimento e histórico) em busca de instruções injetadas, usando detecção determinística; cada evento detectado DEVE registrar tipo, fonte, evidência e ação.

#### Scenario: Injeção em conteúdo recuperado detectada

- **WHEN** conteúdo recuperado de código, documentos ou histórico contém instrução dirigida ao agente
- **THEN** a aplicação registra evento de segurança com tipo, fonte (origem do conteúdo), evidência e ação

#### Scenario: Conteúdo limpo não gera evento

- **WHEN** a solicitação e todo o conteúdo recuperado estão livres de instruções injetadas
- **THEN** nenhum evento de segurança é registrado e a análise segue normalmente

### Requirement: Eventos de segurança persistidos

Os eventos de segurança DEVE ser persistidos vinculados à solicitação de mudança, recuperáveis pela solicitação e rastreáveis por trace_id; nenhum evento DEVE conter segredos.

#### Scenario: Evento persistido e recuperável

- **WHEN** uma análise detecta conteúdo injetado
- **THEN** o evento de segurança fica persistido vinculado à solicitação, é retornado na consulta da análise e carrega o trace_id da execução

#### Scenario: Evento sem segredo

- **WHEN** um evento de segurança é registrado
- **THEN** o registro não contém chave, token ou qualquer segredo

### Requirement: Instrução injetada nunca altera a análise

Instruções injetadas DEVE ser ignoradas: a detecção NUNCA altera a classificação, a avaliação de risco ou o fluxo da análise; a análise DEVE prosseguir até o fim e o risco final DEVE refletir apenas a avaliação estruturada de risco.

#### Scenario: Injeção pedindo risco LOW é ignorada

- **WHEN** conteúdo recuperado instrui o agente a classificar a alteração como LOW e a avaliação estruturada de risco é HIGH
- **THEN** o evento de segurança é registrado com ação que registra a ignorância da instrução, o risco final permanece HIGH e a análise conclui

#### Scenario: Análise continua após injeção

- **WHEN** conteúdo injetado é detectado em qualquer etapa
- **THEN** a análise prossegue normalmente, sem interrupção ou mudança de fluxo

### Requirement: Aprovação humana via endpoint

O sistema DEVE expor endpoint para decisão humana sobre uma análise com aprovação exigida, aceitando decisão APPROVED ou REJECTED e registrando approver, decisão, momento da decisão e trace_id; a decisão DEVE ser aceita apenas enquanto a aprovação está PENDING.

#### Scenario: Aprovação registrada

- **WHEN** um humano envia decisão APPROVED para uma análise com aprovação exigida em PENDING
- **THEN** o sistema registra approver, decisão, momento e trace_id e a aprovação passa a APPROVED

#### Scenario: Rejeição registrada

- **WHEN** um humano envia decisão REJECTED para uma análise com aprovação exigida em PENDING
- **THEN** o sistema registra a decisão e a aprovação passa a REJECTED

#### Scenario: Decisão fora de PENDING rejeitada

- **WHEN** uma decisão é enviada para aprovação já decidida (APPROVED ou REJECTED) ou sem exigência de aprovação
- **THEN** o sistema retorna erro de conflito estruturado e não registra nova decisão

#### Scenario: Decisão inválida rejeitada

- **WHEN** o payload de decisão não contém APPROVED nem REJECTED
- **THEN** o sistema retorna erro de validação estruturado e nada é registrado

### Requirement: Risco HIGH nunca é aprovado automaticamente

Uma análise com risco HIGH DEVE permanecer com aprovação PENDING até decisão humana pelo endpoint; o agente nunca DEVE marcar aprovação como APPROVED ou REJECTED.

#### Scenario: HIGH fica pendente após a análise

- **WHEN** uma análise é avaliada com risco HIGH
- **THEN** a aprovação permanece PENDING após a conclusão da análise, aguardando decisão humana

#### Scenario: Apenas o endpoint decide

- **WHEN** uma análise com risco HIGH aguarda decisão
- **THEN** somente o endpoint de aprovação humana transita a aprovação para APPROVED ou REJECTED
