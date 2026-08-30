## Purpose

Base de conhecimento com os documentos do projeto — arquitetura, regras de negócio, políticas — indexada semanticamente no pgvector, para que a análise recupere contexto relevante com origem e pontuação rastreáveis.

## ADDED Requirements

### Requirement: Ingestão dos documentos de conhecimento

A aplicação DEVE indexar os 6 documentos de `knowledge/` (architecture, business-rules, discount-policy, coding-guidelines, testing-guidelines, security-policy) com chunking, embeddings e persistência no pgvector, de forma idempotente.

#### Scenario: Indexação idempotente

- **WHEN** a aplicação inicia com a base vazia
- **THEN** os documentos são indexados
- **WHEN** a aplicação reinicia com a base preenchida
- **THEN** nenhum chunk é duplicado

### Requirement: Busca por similaridade com metadata

A busca DEVE retornar os chunks mais similares à consulta com metadata obrigatória: fonte do documento, identificador do documento, identificador do chunk e pontuação de similaridade.

#### Scenario: Resultado com metadata

- **WHEN** uma consulta semântica é realizada
- **THEN** cada resultado carrega source, document id, chunk id e score

### Requirement: Limite e relevância

A busca DEVE limitar o número de resultados ao top-k configurável, permitir filtro por pontuação mínima e ordenar por similaridade decrescente.

#### Scenario: Top-k respeitado

- **WHEN** a busca encontra mais candidatos que o limite configurado
- **THEN** apenas os k mais similares são retornados, em ordem decrescente de score

### Requirement: Conteúdo recuperado é dado não confiável

Todo conteúdo retornado pela busca DEVE ser tratado como dado: incorporado ao contexto em seção delimitada com fonte identificada, e nunca DEVE ser interpretado como instrução do sistema.

#### Scenario: Dado demarcado com fonte

- **WHEN** o resultado do RAG é usado na análise
- **THEN** o conteúdo aparece como dado com fonte identificada e não altera as instruções do sistema

### Requirement: Disponibilidade degradada

Quando a busca semântica não está disponível (base vazia ou falha de infraestrutura), a análise DEVE seguir com contexto vazio marcado, sem erro fatal.

#### Scenario: RAG indisponível

- **WHEN** a busca semântica falha ou a base está vazia
- **THEN** a análise continua com lista vazia e a falha registrada com trace_id
