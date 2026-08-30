# analysis-tools Specification

## Purpose

Ferramentas de consulta ao repositório e ao histórico que suprem a análise com evidências de código, testes e mudanças anteriores, com validação de entrada, proteções de segurança e exposição MCP.

## Requirements

### Requirement: Quatro ferramentas de evidência

A aplicação DEVE disponibilizar exatamente quatro tools — `search_code(query)`, `get_file(path)`, `search_change_history(query)` e `get_related_tests(component)` — e nenhuma outra DEVE executar operações arbitrárias.

#### Scenario: Tool disponível para análise

- **WHEN** a análise executa uma etapa de coleta
- **THEN** ela pode invocar qualquer uma das quatro tools e receber resultado estruturado

### Requirement: Validação de entrada

Toda tool DEVE validar seus argumentos — query, path e component não vazios e com tamanho limitado — e DEVE retornar erro estruturado para entrada inválida, sem executar a operação.

#### Scenario: Entrada inválida rejeitada

- **WHEN** uma tool recebe argumento vazio ou com tamanho excessivo
- **THEN** a tool retorna erro estruturado e nenhum arquivo ou consulta é executado

### Requirement: Acesso restrito ao repositório

As tools de arquivo DEVE restringir a leitura ao diretório do repositório configurado: caminhos com path traversal ou que resolvam para fora da raiz DEVE ser rejeitados.

#### Scenario: Path traversal bloqueado

- **WHEN** `get_file` recebe um caminho contendo `../`
- **THEN** a tool rejeita a leitura sem acessar arquivos fora do repositório

#### Scenario: Caminho fora da raiz bloqueado

- **WHEN** um caminho absoluto resolve para fora da raiz configurada
- **THEN** a tool rejeita a leitura com erro estruturado

### Requirement: Sem shell arbitrário

Nenhuma tool DEVE executar comandos de shell ou processos externos; as operações limitam-se a leitura de arquivos e consultas de dados.

#### Scenario: Somente leitura e consulta

- **WHEN** qualquer tool é invocada
- **THEN** nenhum processo externo é iniciado

### Requirement: Timeout, retry e logs das tools

Cada execução de tool DEVE possuir timeout configurável, retry limitado (máx. 2) com backoff entre tentativas e registro de log estruturado com trace_id, incluindo cada tentativa; falha após os retries DEVE ser registrada sem interromper a análise.

#### Scenario: Falha de tool registrada

- **WHEN** uma tool falha após os retries
- **THEN** a falha é registrada com trace_id e a análise segue degradada

#### Scenario: Tentativas com backoff registradas

- **WHEN** uma tool falha e é reexecutada
- **THEN** há backoff entre as tentativas e cada tentativa é registrada com trace_id

### Requirement: Exposição via MCP

Pelo menos `search_code` e `get_file` DEVE estar expostas como tools num servidor MCP da aplicação, com os mesmos contratos e proteções das implementações internas.

#### Scenario: Tool MCP funcional

- **WHEN** um cliente MCP lista as tools do servidor
- **THEN** `search_code` e `get_file` estão presentes e executáveis com as mesmas validações e proteções de path
