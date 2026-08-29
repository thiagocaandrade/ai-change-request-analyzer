## Purpose

Pipeline Spring Boot que recebe solicitações de mudança, delega a análise ao serviço agente e persiste o ciclo de vida da solicitação com rastreabilidade e tratamento de falhas.

## ADDED Requirements

### Requirement: Health check da aplicação

A aplicação DEVE expor um endpoint de health que responda 200 quando a aplicação estiver em execução e configurada.

#### Scenario: Aplicação saudável

- **WHEN** uma requisição GET é enviada ao endpoint de health da aplicação
- **THEN** a resposta possui código HTTP 200 e indica estado operacional

### Requirement: Recepção e delegação de solicitação

O sistema DEVE aceitar `POST /requests` com o texto da solicitação de mudança, gerar um trace_id único, delegar a análise ao agente com timeout e retry limitado, e persistir a solicitação com seu status.

#### Scenario: Solicitação aceita

- **WHEN** o usuário envia uma solicitação de mudança válida
- **THEN** o sistema retorna o identificador e o status inicial da solicitação, registrando-a com trace_id

#### Scenario: Agente indisponível

- **WHEN** o agente não responde dentro do timeout após as tentativas configuradas
- **THEN** o sistema retorna erro estruturado e marca a solicitação como "failed" com a causa registrada

### Requirement: Consulta de status e resultado

O sistema DEVE expor endpoint para consultar o status e o resultado de uma solicitação persistida.

#### Scenario: Status consultado

- **WHEN** o usuário consulta o status de uma solicitação existente
- **THEN** o sistema retorna o estado atual persistido e, se concluída, o resultado estruturado

### Requirement: Tratamento global de exceções

O sistema DEVE tratar exceções não capturadas e retornar respostas de erro estruturadas e consistentes, sem expor stack traces ou informações sensíveis.

#### Scenario: Erro interno tratado

- **WHEN** ocorre uma exceção inesperada durante o processamento
- **THEN** o sistema retorna resposta de erro estruturada com código HTTP adequado e sem detalhes internos

### Requirement: Logs estruturados com trace_id

O sistema DEVE registrar logs estruturados em JSON com trace_id correlacionado em todas as entradas de uma mesma requisição.

#### Scenario: Requisição rastreável

- **WHEN** qualquer requisição é processada pelo sistema
- **THEN** os registros de log daquela requisição contêm o mesmo trace_id

### Requirement: Configuração do modelo por variável de ambiente

O sistema DEVE obter a configuração do modelo de IA exclusivamente por variáveis de ambiente, sem valores fixos no código, e DEVE iniciar normalmente mesmo quando essas variáveis não estiverem definidas.

#### Scenario: Inicialização sem modelo configurado

- **WHEN** o sistema inicia sem variáveis de modelo definidas
- **THEN** o sistema sobe normalmente e nenhuma chamada a modelo de IA é realizada

#### Scenario: Nenhum segredo em logs

- **WHEN** o sistema registra configuração ou erros em log
- **THEN** nenhuma chave, token ou segredo aparece nos registros
