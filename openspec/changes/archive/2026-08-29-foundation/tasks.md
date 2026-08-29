## 1. Preparação do ambiente e spike de compatibilidade

- [x] 1.1 Verificar compatibilidade do Spring Boot 4.1.1 com a versão GA do Spring AI; adicionar ao pom.xml o driver PostgreSQL, logstash-logback-encoder e o esqueleto Spring AI (BOM + starter OpenAI-compatible); verificar `mvn -q compile` verde (se incompatível, pinar Spring Boot 3.5.x e documentar no README)
- [x] 1.2 Criar `.env.example` com todas as variáveis necessárias sem valores reais e garantir `.gitignore` cobrindo `.env`; verificar `git status --ignored` não rastreando nenhum segredo

## 2. Serviço agente (Python + LangGraph)

- [x] 2.1 Criar `agent/` com `requirements.txt` de versões pinadas (fastapi, uvicorn, langgraph, structlog, pydantic); verificar instalação limpa em venv novo
- [x] 2.2 Implementar grafo mínimo (estado tipado `AnalysisState`, nós `parse_stub` e `compile_stub`, edges explícitas START→END) com teste pytest do fluxo completo; verificar `pytest` verde
- [x] 2.3 Implementar `POST /analyze` (validação Pydantic, entrada sem texto → 400 sem executar o grafo) e `GET /health`; verificar testes de happy path e de entrada inválida passando
- [x] 2.4 Configurar logs JSON com structlog usando o trace_id do cabeçalho X-Trace-Id (gerando um próprio quando ausente); verificar teste que captura os logs e confirma o mesmo trace_id em todas as linhas

## 3. Pipeline Spring Boot

- [x] 3.1 Criar pacotes `web`, `api`, `domain`, `config` com a entidade `ChangeRequest` (id, text, status PENDING/COMPLETED/FAILED, traceId, result JSON, timestamps) e repositório JPA; verificar teste de persistência com H2
- [x] 3.2 Implementar filter de trace_id (UUID no MDC) e logback JSON com logstash encoder; verificar teste que confirma trace_id único e presente em todas as linhas de log de uma requisição
- [x] 3.3 Implementar `AgentClient` (RestClient, timeout 10s, 3 tentativas com backoff, header X-Trace-Id) e DTOs de contrato; verificar testes de sucesso e de falha após esgotar retries
- [x] 3.4 Implementar `POST /requests` e `GET /requests/{id}` com tratamento global de exceções (`@RestControllerAdvice`, sem stack trace em resposta); verificar testes de happy path, 404 e agente indisponível → status FAILED com causa registrada
- [x] 3.5 Configurar esqueleto Spring AI exclusivamente por variáveis de ambiente (`AI_CHAT_MODEL`, `AI_CHAT_API_KEY`, `AI_CHAT_BASE_URL`) sem bean que exija chave no startup; verificar teste de contexto subindo sem nenhuma variável definida
- [x] 3.6 Adicionar starter-actuator e expor `/health`; verificar resposta 200 em teste de contexto

## 4. Docker Compose

- [x] 4.1 Criar Dockerfile do app (multi-stage: Maven build → JRE 21) e Dockerfile do agente (python:3.12-slim + requirements); verificar `docker build` das duas imagens
- [x] 4.2 Criar `docker-compose.yml` com `db` (pgvector/pgvector:pg16, volume, healthcheck pg_isready), `app` e `agent` com `depends_on` condicionado e env do `.env`; verificar `docker compose up` com health 200 nos três serviços

## 5. CI

- [x] 5.1 Adicionar Spotless com google-java-format ao pom e configurar ruff no agente; verificar `mvn spotless:check` e `ruff check` passando localmente
- [x] 5.2 Criar workflow `.github/workflows/ci.yml` com jobs `spring` (mvn test + package) e `agent` (ruff + pytest); verificar execução verde no GitHub Actions
- [x] 5.3 Adicionar job E2E no CI que sobe os serviços com docker compose e executa smoke test do fluxo completo (`POST /requests` → status COMPLETED com trace_id idêntico nos logs dos dois serviços); verificar job verde

## 6. Documentação e evidências

- [x] 6.1 Escrever README com visão geral dos componentes, diagrama da arquitetura, instruções de execução via Docker Compose e variáveis de ambiente; verificar cobertura dos cenários da spec `platform-foundation`
- [x] 6.2 Registrar em `docs/evidencias/` a evidência da execução local completa (compose up, análise de ponta a ponta e logs correlacionados por trace_id); verificar arquivo presente e coerente com a execução real
