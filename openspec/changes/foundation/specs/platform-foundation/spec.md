## Purpose

Ambiente de execução local reprodutível via Docker Compose, com PostgreSQL (pgvector) e os serviços do sistema configurados exclusivamente por variáveis de ambiente.

## ADDED Requirements

### Requirement: Subida local via Docker Compose

O sistema DEVE subir completamente através de Docker Compose, composto por banco PostgreSQL com pgvector, aplicação Spring Boot e serviço agente Python.

#### Scenario: Ambiente sobe e responde

- **WHEN** o usuário executa `docker compose up` usando as variáveis do `.env.example`
- **THEN** banco, aplicação e agente ficam saudáveis e seus endpoints de health respondem com 200

### Requirement: Configuração por variáveis de ambiente

Toda configuração de conexão e integração DEVE ser fornecida por variáveis de ambiente, e o repositório DEVE conter um `.env.example` sem valores reais.

#### Scenario: Sem segredos no repositório

- **WHEN** o conteúdo do repositório é inspecionado
- **THEN** nenhum segredo, token ou arquivo `.env` real está versionado

### Requirement: Documentação básica de arquitetura

O repositório DEVE conter documentação básica descrevendo os componentes da solução, como executá-la via Docker Compose e como configurar o ambiente.

#### Scenario: Documentação presente

- **WHEN** o avaliador abre o README do repositório
- **THEN** encontra a visão geral dos componentes, as instruções de execução com Docker Compose e as variáveis de ambiente necessárias
