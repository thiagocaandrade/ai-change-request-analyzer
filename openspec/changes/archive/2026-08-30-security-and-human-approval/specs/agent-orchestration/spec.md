## MODIFIED Requirements

### Requirement: Conteúdo recuperado não confiável

O grafo DEVE tratar o texto da solicitação e todo conteúdo recuperado (código, documentos, histórico) como dado não confiável: instruções injetadas DEVE ser detectadas pela avaliação de segurança obtida da aplicação via HTTP com timeout e retry limitado, registradas como evento de segurança persistido, refletidas no estado e no resultado final, e nunca DEVE alterar a classificação, o risco ou o fluxo da análise.

#### Scenario: Injeção detectada e ignorada

- **WHEN** conteúdo recuperado contém instrução para alterar a classificação da análise
- **THEN** o evento de segurança é registrado e persistido pela aplicação, aparece no estado e no resultado final, e a instrução não altera o risco nem o fluxo — a análise prossegue normalmente

#### Scenario: Avaliação de segurança obtida da aplicação

- **WHEN** o grafo executa o nó de detecção de conteúdo não confiável
- **THEN** a avaliação de segurança (detecção e eventos) é obtida da aplicação via HTTP com timeout e retry limitado, espelhando a detecção determinística da aplicação

#### Scenario: Avaliação de segurança indisponível

- **WHEN** a aplicação não responde dentro do timeout após os retries na etapa de detecção
- **THEN** a falha é registrada em `errors` com o nó identificado, a avaliação de segurança fica vazia e a análise prossegue, sem interromper o grafo
