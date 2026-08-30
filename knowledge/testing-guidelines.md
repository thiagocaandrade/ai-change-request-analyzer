# Diretrizes de Testes

## Cobertura obrigatória

- Happy path: fluxo completo da análise com resultado válido.
- Segurança: path traversal, injeção de prompt e ausência de secrets nas respostas.
- Falha de tool e falha de integração: análise segue degradada com erro registrado.
- E2E: cenário de sucesso e cenário degradado (sem chave de IA).

## Testes unitários e integração

- Java: JUnit com H2 para repositórios; VectorStore e ChatModel são mockados em testes unitários (pgvector e provedor de IA não rodam em H2).
- Python: pytest com client HTTP mockado nos testes do grafo.
- `mvn test` deve ficar verde sem chave de API configurada.

## Priorização por risco

Alterações em regras financeiras têm prioridade máxima de teste; alterações cosméticas têm prioridade baixa. Testes de regressão cobrem a regra antiga e a nova.
