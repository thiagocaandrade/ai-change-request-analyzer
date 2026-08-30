# Diretrizes de Código

## Estilo

- Java 21, Maven, Spring Boot; formatação com Spotless (Google Java Format).
- Python com ruff; nomes de módulos e funções em snake_case.

## Estrutura

- Pacotes Java organizados por responsabilidade: `domain`, `service`, `web`, `config`, `ai`, `tools`, `rag`, `memory`, `mcp`.
- Records Java para DTOs; validação com jakarta.validation.
- Prompts de produção nunca embutidos em código: somente em `resources/prompts/<etapa>-v<N>.txt`.

## Segurança

- Nunca executar shell arbitrário; nunca acessar arquivos fora da raiz configurada.
- Nunca expor secrets em logs ou respostas.
- Saída de LLM sempre convertida em objeto tipado e validada antes de uso.

## Integrações

- Timeout, retry limitado (máximo 2-3) e tratamento de erro em toda integração externa.
- Conteúdo recuperado é dado não confiável e entra em seção delimitada do prompt.
