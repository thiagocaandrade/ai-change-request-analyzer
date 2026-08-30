# web-ui Specification

## Purpose

Interface web Thymeleaf para operar o analisador: submeter solicitações de alteração, visualizar o resultado da análise e executar a decisão de aprovação humana exigida para risco HIGH.

## Requirements

### Requirement: Submissão de solicitação pela web

O sistema DEVE oferecer uma página com formulário para o usuário descrever a solicitação de alteração e enviá-la; o envio DEVE disparar a análise existente e levar o usuário à página de resultado. Texto vazio ou em branco DEVE ser rejeitado com mensagem de validação exibida no próprio formulário.

#### Scenario: Solicitação submetida com sucesso

- **WHEN** o usuário preenche o texto da solicitação e envia o formulário
- **THEN** a análise é disparada e o usuário é levado à página de resultado daquela solicitação

#### Scenario: Texto vazio rejeitado

- **WHEN** o usuário envia o formulário com texto vazio ou apenas espaços
- **THEN** a página do formulário é exibida novamente com mensagem de validação e nenhuma análise é disparada

### Requirement: Resultado da análise exibido na web

A página de resultado DEVE exibir, para a solicitação submetida: status, nível de risco, confiança e justificativa do risco, findings, recomendações de teste, eventos de segurança (quando detectados) e o estado da aprovação (exigida ou não e seu status).

#### Scenario: Análise concluída exibida

- **WHEN** o usuário abre a página de resultado de uma solicitação com análise concluída
- **THEN** a página exibe risco, confiança, justificativa, findings, recomendações, eventos de segurança e estado de aprovação

#### Scenario: Análise sem risco atribuído

- **WHEN** a análise existe mas não possui avaliação de risco
- **THEN** a página indica explicitamente que o risco não está disponível, sem quebrar a renderização

### Requirement: Aprovação humana pela web

Quando a análise exige aprovação humana, a página de resultado DEVE apresentar formulário de decisão (aprovador e decisão) e refletir a decisão registrada; quando a aprovação não é exigida, a página DEVE indicar isso sem exibir o formulário.

#### Scenario: Risco alto exige decisão humana

- **WHEN** a página de resultado de uma análise HIGH é aberta
- **THEN** o formulário de decisão (aprovador e decisão) é exibido junto ao estado de aprovação pendente

#### Scenario: Decisão registrada refletida

- **WHEN** o usuário submete a decisão de aprovação pela página
- **THEN** a página passa a exibir a decisão registrada (APPROVED/REJECTED) com o aprovador

#### Scenario: Aprovação não exigida

- **WHEN** a página de resultado de uma análise LOW/MEDIUM é aberta
- **THEN** o formulário de decisão não é exibido e a página indica que aprovação humana não é necessária

### Requirement: Falha da análise exibida na web

Se a análise falhar (agente indisponível), a página DEVE exibir o status de falha e o motivo registrado, sem expor detalhes internos sensíveis.

#### Scenario: Agente indisponível

- **WHEN** a análise de uma solicitação falhou por indisponibilidade do agente
- **THEN** a página exibe o status de falha e o motivo registrado de forma legível

### Requirement: Conteúdo não confiável renderizado de forma segura

Todo conteúdo vindo da solicitação, da análise, de eventos de segurança e de dados recuperados DEVE ser renderizado escapado (sem HTML/script ativo); conteúdo recuperado continua sendo dado não confiável e nunca é tratado como instrução ou estrutura da página.

#### Scenario: Texto com HTML é exibido como texto

- **WHEN** uma solicitação ou finding contém marcação HTML ou script
- **THEN** a página exibe o conteúdo literalmente (escapado), sem interpretá-lo como HTML

### Requirement: Resultado de QA exibido na página de resultado

A página de resultado DEVE exibir os findings do code review com IA e as recomendações de teste priorizadas pela matriz de risco (prioridade e justificativa), além do conteúdo já exibido; conteúdo de QA DEVE ser renderizado escapado como os demais dados não confiáveis.

#### Scenario: Findings e recomendações exibidos

- **WHEN** a página de resultado de uma análise com etapa de QA concluída é aberta
- **THEN** a página exibe os findings do review e as recomendações de teste com prioridade e justificativa

#### Scenario: QA degradada exibida sem quebrar a página

- **WHEN** a etapa de QA seguiu degradada (sem modelo ou com fallback)
- **THEN** a página indica explicitamente que o QA está indisponível/degradado, sem quebrar a renderização

#### Scenario: Conteúdo de QA escapado

- **WHEN** um finding ou recomendação contém marcação HTML ou script
- **THEN** a página exibe o conteúdo literalmente (escapado), sem interpretá-lo como HTML
