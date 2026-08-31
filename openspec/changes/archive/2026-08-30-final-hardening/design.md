## Context

Change 10 do roadmap (motivação em proposal.md). Estado atual relevante:

- `AiConfig.java` constrói o `ChatClient` manualmente com o client OpenAI e lê `ai.chat.api-key` / `ai.chat.model` / `ai.chat.base-url` (envs atuais `AI_CHAT_*`) — evidência: `src/main/java/com/ai/change/request/analyzer/config/AiConfig.java`.
- `AiAnalysisService.java` lê `ai.chat.timeout-ms` e `ai.chat.model`. Sem chave, o bean não é criado (`@ConditionalOnExpression`) e o fluxo usa fallback determinístico (especificado em `ai-capabilities`).
- Prompts em `resources/prompts/`, todos v1; a etapa de risco usa `risk-analysis-v1` (roadmap exige v1→v2 com evidência comparável).
- `docs/evidence/` tem 11 arquivos; faltam `02-parallel-execution.png`, `10-e2e.png` e `14-prompt-refinement.png`.
- README tem matriz parcial (template no roadmap); `.env.example` usa os nomes antigos `AI_CHAT_*`.

## Goals / Non-Goals

**Goals:**

- Definir o mapeamento exato entre as envs oficiais (`AI_PROVIDER`, `AI_MODEL`, `AI_TEMPERATURE`, `AI_API_KEY`) e a configuração Spring existente, sem quebrar o fallback sem chave.
- Definir a metodologia do experimento v1-vs-v2 reprodutível (mesmos casos, métricas comparáveis) e o critério de decisão para trocar a versão padrão.
- Definir como a auditoria final é produzida (matriz README + evidências) e o que acontece com problemas encontrados.

**Non-Goals:**

- Sem suporte a providers além do OpenAI nesta change (nenhuma dependência nova).
- Sem alterar nós do grafo, tools, endpoints ou regras determinísticas.
- Sem corrigir defeitos encontrados na auditoria — viram novas changes.

## Decisions

### D1 — Mapeamento de envs oficiais sobre as chaves existentes

Em `application.yml`, as chaves existentes passam a ler as envs oficiais:

```yaml
ai:
  chat:
    api-key: ${AI_API_KEY:}
    model: ${AI_MODEL:}
    temperature: ${AI_TEMPERATURE:}
    base-url: ${AI_CHAT_BASE_URL:}
  provider: ${AI_PROVIDER:openai}
```

- `AI_PROVIDER`: valores aceitos `openai` (default) — único client disponível hoje; outro valor → log estruturado com trace_id + fallback determinístico marcado (mesmo caminho do "sem modelo"). Documentado no README.
- `AI_TEMPERATURE`: vazia/ausente → temperatura default do provider (não é enviada); não-numérica → tratada como ausente com warning estruturado.
- `AI_CHAT_BASE_URL` permanece como extensão documentada (necessária para modelos locais via OpenAI-compatible endpoint); os nomes antigos `AI_CHAT_MODEL`/`AI_CHAT_API_KEY` deixam de ser documentados.
- Alternativa considerada: criar `@ConfigurationProperties` dedicado — descartada, adiciona superfície sem ganho (as chaves já existem e o fallback por `@ConditionalOnExpression` depende delas).

### D2 — Experimento de prompts reprodutível

- Script `scripts/prompt_experiment.py`: executa a etapa de risco com `risk-analysis-v1` e `risk-analysis-v2` sobre os mesmos 3 casos (Cenário A, Cenário B e caso sem evidência forte), capturando para cada versão: risco, confidence, racional e presença de evidências citadas.
- Critério de decisão (registrado em `docs/prompt-refinement.md`): a v2 vira padrão se, em todos os casos, apresentar risco/confidence consistentes com as regras determinísticas e exigir evidência no racional sem regressão; caso contrário a v1 permanece padrão e o resultado é documentado.
- Mudança de padrão: apenas o identificador de versão da etapa `risk-analysis` no registro de prompts (`PromptRegistry`/serviço que resolve `<etapa>-v<N>`) passa a resolver v2; a v1 continua carregável para reproduzir o experimento.
- Evidência: `docs/evidence/14-prompt-refinement.png` (saída lado a lado do script).

### D3 — Auditoria final

- Matriz no README: Requisito → Implementação → Evidência → Teste → Risco, gerada a partir do checklist de auditoria do roadmap + specs existentes em `openspec/specs/`.
- Evidências ausentes: `02-parallel-execution.png` (página de trace com nós paralelos executando), `10-e2e.png` (execução dos cenários oficiais), `14-prompt-refinement.png` (D2) — capturadas da aplicação rodando via docker compose.
- Problema encontrado na auditoria → nova change `/opsx-new fix-...`; nunca correção fora do OpenSpec.

### D4 — E2E dos cenários oficiais

Reusar `scripts/smoke_test.py` (já cobre Cenário A) e estender com o Cenário B (injeção) se necessário; executar ambos com o stack via docker compose e registrar `10-e2e.png`. `mvn test` completo verde é pré-condição de entrega.

## Risks / Trade-offs

- [v2 pior que v1 em algum caso] → o critério de D2 decide pela evidência; v1 permanece disponível e a decisão fica documentada.
- [Regressão por troca de envs (`AI_CHAT_*` → oficiais)] → `.env.example`, docker-compose e README atualizados na mesma change; fallback sem chave permanece coberto por teste existente.
- [`AI_PROVIDER` diferente de `openai`] → comportamento degradado documentado, sem exceção não tratada.
- [Evidências dependem do stack rodando] → captura via scripts existentes; se indisponível, gerar a evidência a partir de artefatos estáticos (logs/trace JSON) e registrar a origem.
- [Prompt v2 não muda resultado observável nos testes automatizados (LLM real não determinístico)] → testes unitários validam seleção de versão e fallback; a qualidade comparada é validada pelo experimento documentado, não por assert de LLM.

## Migration Plan

1. Aplicar D1 (config) + testes → `mvn test`.
2. Criar `risk-analysis-v2.txt` + script do experimento (D2); executar v1-vs-v2; documentar decisão; trocar a versão padrão se aprovado.
3. Auditoria (D3): completar matriz e evidências.
4. E2E (D4) e suite completa verde.
5. Rollback: reverter commits (prompts são aditivos — v1 preservada; envs antigas não são removidas do código, apenas deixam de ser documentadas).
