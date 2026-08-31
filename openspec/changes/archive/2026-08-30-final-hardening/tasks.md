## 1. Prompt refinement risk-analysis v1→v2 com evidência comparável

- [x] 1.1 Criar `resources/prompts/risk-analysis-v2.txt` incorporando business rules, exigência de evidência no racional, confidence e classificação de high-risk, mantendo a seção delimitada de DADOS NÃO CONFIÁVEIS; verificar com teste unitário que o PromptRegistry carrega `risk-analysis` v1 e v2
- [x] 1.2 Criar `scripts/prompt_experiment.py` executando v1 e v2 nos mesmos 3 casos (Cenário A, Cenário B, caso sem evidência) e capturando risco, confidence e racional por versão; verificar execução do script com o stack via docker compose
- [x] 1.3 Documentar em `docs/prompt-refinement.md` o problema da v1, a alteração, o resultado do experimento e a decisão (critério D2 do design); verificar que o documento contém a tabela v1-vs-v2 por caso
- [x] 1.4 Trocar a versão padrão da etapa de risco para v2 (v1 permanece carregável) e registrar `docs/evidence/14-prompt-refinement.png`; verificar `mvn test` verde e presença da evidência

## 2. Configuração de modelo por env (AI_PROVIDER/MODEL/TEMPERATURE/KEY) + .env.example

- [x] 2.1 Atualizar `application.yml`: `ai.chat.api-key=${AI_API_KEY:}`, `ai.chat.model=${AI_MODEL:}`, `ai.chat.temperature=${AI_TEMPERATURE:}`, `ai.provider=${AI_PROVIDER:openai}`; verificar `mvn test` verde
- [x] 2.2 Ajustar `AiConfig`/`AiAnalysisService` para aplicar `AI_TEMPERATURE` nas opções (ausente/inválida → default do provider com warning estruturado) e tratar `AI_PROVIDER` diferente de `openai` com fallback degradado marcado; verificar com testes unitários (ChatModel fake): temperatura aplicada, ausente, inválida, provider desconhecido
- [x] 2.3 Atualizar `.env.example` com as envs oficiais (sem valores reais) e o docker-compose; verificar ausência de segredos no git (`git grep` por chaves) e fallback sem chave rodando `mvn test` sem `AI_API_KEY`

## 3. Auditoria final: matriz de requisitos README + docs/evidence

- [x] 3.1 Gerar a matriz completa Requisito → Implementação → Evidência → Teste → Risco no README a partir do checklist do roadmap e das specs em `openspec/specs/`; verificar que todo requisito do PDF tem linha preenchida
- [x] 3.2 Completar `docs/evidence/` com `02-parallel-execution.png` (trace com nós paralelos executando) e conferir os 14 arquivos listados no roadmap; verificar presença de todos os arquivos
- [x] 3.3 Registrar problemas encontrados na auditoria como novas changes (`/opsx-new fix-...`) ou, se nenhum, registrar a conclusão no README; verificar registro

## 4. E2E dos cenários oficiais + mvn test verde

- [x] 4.1 Executar o Cenário A (VIP 10%→15%) e o Cenário B (prompt injection → instrução ignorada, security event registrado, HIGH continua exigindo aprovação) via `scripts/smoke_test.py` com o stack via docker compose; verificar a saída esperada de ambos e registrar `docs/evidence/10-e2e.png`
- [x] 4.2 Rodar `mvn test` completo e `pytest agent/` verdes; verificar CI verde no GitHub Actions e atualizar `docs/roadmap.md` (change 10 concluída)
