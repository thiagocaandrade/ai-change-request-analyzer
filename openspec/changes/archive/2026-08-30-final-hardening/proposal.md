## Why

É a change 10 do roadmap (`docs/roadmap.md`), a última: sem ela, faltam três evidências exigidas pelo PDF (refinamento de prompts com evidência comparável, configuração do modelo por variável de ambiente com os nomes oficiais `AI_PROVIDER`/`AI_MODEL`/`AI_TEMPERATURE`/`AI_API_KEY`, e auditoria final com matriz completa) e o `docs/evidence/` fica incompleto (faltam `02-parallel-execution.png`, `10-e2e.png`, `14-prompt-refinement.png`). A entrega é 31/08/26.

## What Changes

- Refinamento de prompt com evidência comparável: `risk-analysis-v1` vs `risk-analysis-v2` executados nos mesmos casos (Cenário A e Cenário B); documentação do problema da v1, da alteração, do resultado e da decisão; a v2 passa a ser a versão padrão da etapa de risco e a v1 é preservada para comparação.
- Configuração do modelo por env com os nomes oficiais: `AI_PROVIDER`, `AI_MODEL`, `AI_TEMPERATURE`, `AI_API_KEY` (compatibilidade mantida com as chaves `ai.chat.*` atuais); comportamento documentado quando ausentes (fallback determinístico já especificado); `.env.example` atualizado sem valores reais.
- Auditoria final: matriz Requisito → Implementação → Evidência → Teste → Risco no README; `docs/evidence/` completo com uma evidência objetiva por requisito (incluindo os 3 arquivos ausentes); problemas encontrados viram novas changes, nunca correções fora do OpenSpec.
- E2E dos cenários oficiais (A: VIP 10%→15%; B: prompt injection) executados e registrados; suíte completa `mvn test` verde.

## Capabilities

### New Capabilities

Nenhuma — nenhuma capacidade nova é introduzida; a change consolida e evidencia capacidades existentes.

### Modified Capabilities

- `ai-capabilities`: o requisito "Prompts versionados" passa a exigir que a etapa de risco use `risk-analysis-v2` como versão padrão, escolhida por evidência comparável (v1 preservada); novo requisito "Modelo configurável por variáveis de ambiente" define `AI_PROVIDER`, `AI_MODEL`, `AI_TEMPERATURE` e `AI_API_KEY` como fonte de configuração do modelo, com ausência → fallback determinístico já especificado.

## Non-goals

- Sem mudanças nas regras determinísticas (`RiskPolicy` continua decidindo aprovação obrigatória; LLM apenas sugere).
- Sem novas tools, nós do grafo, endpoints públicos, frontend ou workflow n8n.
- Sem correção de defeitos encontrados na auditoria dentro desta change — eles viram novas changes OpenSpec.
- Sem mudança na topologia LangGraph nem nos schemas de saída tipados.

## Impact

- Código: `AiConfig`/`AiAnalysisService` (leitura de provider/model/temperature via env padrão), `resources/prompts/risk-analysis-v2.txt` + registro do experimento v1-vs-v2.
- Docs: README (matriz de requisitos completa), `docs/evidence/02-parallel-execution.png`, `10-e2e.png`, `14-prompt-refinement.png`, `.env.example`.
- Testes: suite unitária/integração cobrindo a seleção de prompt por versão e a configuração por env; E2E dos cenários A e B registrado como evidência.
- Sem novas dependências externas.
