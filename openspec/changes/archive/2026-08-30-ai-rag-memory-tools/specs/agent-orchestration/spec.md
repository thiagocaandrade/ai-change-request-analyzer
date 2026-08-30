## MODIFIED Requirements

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
