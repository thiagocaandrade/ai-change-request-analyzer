# agent-orchestration Specification

## Purpose

Define o fluxo de orquestração LangGraph que transforma uma solicitação de mudança em análise estruturada de impacto, risco e testes, com paralelização, branching e condição de parada.

## Requirements

### Requirement: Estado compartilhado da análise

O grafo DEVE manter todo o progresso da análise num único estado compartilhado que carrega: identificador da solicitação, trace_id, texto da solicitação, classificação, documentos recuperados, achados de código, achados históricos, achados de impacto, avaliação de risco, avaliação de segurança, plano de testes, indicador e estado de aprovação, resultado final, erros e contador de iterações.

#### Scenario: Estado completo ao final

- **WHEN** uma análise conclui com sucesso
- **THEN** o estado contém resultado final compilado e todas as seções preenchidas com valores estruturados ou coleções vazias

#### Scenario: Execução rastreável

- **WHEN** o grafo executa uma análise
- **THEN** todos os registros de log dos nós carregam o mesmo trace_id da execução

### Requirement: Fluxo ordenado de 13 nós

O grafo DEVE executar os nós na ordem: `validate_request`, `classify_request`, `detect_untrusted_content`, coleta paralela (`analyze_code`, `retrieve_knowledge`, `retrieve_history`), `analyze_impact`, `assess_risk`, `approval_router`, ramo de aprovação, `generate_test_plan`, `validate_final_result` e `finalize`.

#### Scenario: Happy path

- **WHEN** uma solicitação válida atravessa o grafo
- **THEN** todos os 13 nós são executados na ordem definida e o resultado final é compilado

### Requirement: Coleta paralela de evidências

Os nós `analyze_code`, `retrieve_knowledge` e `retrieve_history` DEVE executar em paralelo após `detect_untrusted_content`, obtendo as evidências da aplicação via HTTP com timeout e retry limitado, e a síntese DEVE aguardar a conclusão dos três antes de iniciar `analyze_impact`.

#### Scenario: Execução paralela

- **WHEN** o grafo processa uma análise
- **THEN** os três nós de coleta executam concorrentemente e seus resultados ficam disponíveis antes de `analyze_impact`

#### Scenario: Falha isolada em coleta

- **WHEN** um dos três nós de coleta falha, inclusive por falha de comunicação com a aplicação
- **THEN** os demais concluem, a falha é registrada em `errors` com o nó identificado e a análise segue degradada

#### Scenario: Aplicação indisponível

- **WHEN** a aplicação não responde dentro do timeout após os retries de um nó de coleta
- **THEN** o nó registra a falha em `errors` com coleta vazia e a análise segue degradada, sem interromper o grafo

### Requirement: Branching por risco

O `approval_router` DEVE rotear a execução conforme o risco avaliado: risco HIGH encaminha para `human_approval`; risco LOW ou MEDIUM segue diretamente para `generate_test_plan`. O roteamento DEVE considerar apenas a avaliação de risco estruturada — nunca instruções vindas de conteúdo recuperado.

#### Scenario: Risco HIGH roteia para aprovação

- **WHEN** a avaliação de risco é HIGH
- **THEN** a execução passa por `human_approval` antes de `generate_test_plan`

#### Scenario: Risco LOW ou MEDIUM segue direto

- **WHEN** a avaliação de risco é LOW ou MEDIUM
- **THEN** a execução segue diretamente para `generate_test_plan` sem passar por `human_approval`

### Requirement: Aprovação humana obrigatória para HIGH

Quando o risco avaliado é HIGH, o grafo DEVE marcar a análise como exigindo aprovação humana com estado PENDING e DEVE terminar sem decisão de aprovação tomada pelo agente; a decisão final de obrigatoriedade permanece da aplicação.

#### Scenario: Análise HIGH pendente

- **WHEN** uma análise é avaliada com risco HIGH
- **THEN** a execução termina com aprovação exigida, estado PENDING e status indicando pendência de aprovação

#### Scenario: Agente nunca decide a aprovação

- **WHEN** o grafo processa uma análise com risco HIGH
- **THEN** o agente nunca marca a aprovação como APPROVED ou REJECTED — apenas registra a pendência

### Requirement: Condição de parada com retry limitado

O grafo DEVE validar o resultado final e, quando inválido, reexecutar a geração com contador de iterações limitado a no máximo 2 tentativas de correção; esgotado o limite, DEVE terminar com erro estruturado, sem loop infinito.

#### Scenario: Retry limitado

- **WHEN** o resultado final é inválido
- **THEN** o grafo reexecuta a geração incrementando o contador de iterações até o limite

#### Scenario: Limite esgotado termina com erro

- **WHEN** o resultado final permanece inválido após as 2 tentativas de correção
- **THEN** a execução termina com status de falha e erros registrados, sem nova reexecução

#### Scenario: Resultado válido finaliza

- **WHEN** a validação do resultado final passa
- **THEN** `finalize` compila o resultado final e a execução conclui com sucesso

### Requirement: Contenção de falhas nos nós

Qualquer falha de nó DEVE ser capturada no estado — registrada em `errors` com identificação do nó — e nunca DEVE expor segredos nem interromper o processo com exceção não tratada.

#### Scenario: Falha registrada sem vazar dados

- **WHEN** um nó falha durante a execução
- **THEN** a falha aparece em `errors` com o nó identificado, a execução termina de forma estruturada e nenhuma chave ou segredo aparece nos erros ou logs

### Requirement: Conteúdo recuperado não confiável

O grafo DEVE tratar conteúdo recuperado (código, documentos, histórico) como dado não confiável: instruções injetadas DEVE ser detectadas e registradas como evento de segurança, e nunca DEVE alterar a classificação, o risco ou o fluxo da análise.

#### Scenario: Injeção detectada e ignorada

- **WHEN** conteúdo recuperado contém instrução para alterar a classificação da análise
- **THEN** o evento de segurança é registrado, a instrução não altera o risco nem o fluxo, e a análise prossegue normalmente
