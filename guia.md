# Guia — Fluxo OpenSpec para cada item do roadmap

Este guia descreve, passo a passo, como implementar cada uma das 10 changes do `docs/roadmap.md` usando o fluxo OpenSpec (spec-driven). O mesmo ciclo se repete para cada item — nunca pular nem fundir fases.

## 1. Visão geral do ciclo (uma change completa)

```
/opsx:new ──► cria a change e o proposal.md (por que, o quê, capabilities)
/opsx:continue ──► gera o próximo artefato (specs → design → tasks)
   ou
/opsx:ff ──► gera specs + design + tasks de uma vez

      │
      ▼
REVISÃO HUMANA (você lê e aprova os 4 artefatos)
      │
      ▼
/opsx:apply ──► implementação task por task, com testes a cada task
      │
      ▼
/opsx:verify ──► confere implementação × specs × tasks
      │
      ▼
/opsx:sync ──► mescla deltas de specs nas specs principais (openspec/specs/)
      │
      ▼
/opsx:archive ──► move a change para openspec/changes/archive/ e encerra
```

Regra de ouro: **revisar os artefatos antes do apply** — esse é o momento barato de mudar decisões. Depois do apply, mudanças custam mais.

## 2. Detalhe de cada passo

### 2.1 `/opsx:new` — começar a change

1. Eu derivo o nome kebab-case (ex.: linha 10 do roadmap → `domain-and-api`).
2. Executo `openspec new change "<nome>"`.
3. Gero o `proposal.md` seguindo o template e as regras do `openspec/config.yaml`:
   - `Why` (problema, 1–2 frases), `What Changes` (bullets, marcar **BREAKING** quando quebra contrato), `Capabilities` (novas e modificadas), `Impact`, `Non-goals`.
   - **Capabilities é o contrato**: cada capability nova vira `specs/<nome>/spec.md`; cada modificada gera um delta com o caminho EXATO já existente em `openspec/specs/`.
4. Você revisa o proposal.

### 2.2 `/opsx:continue` ou `/opsx:ff` — specs, design, tasks

**Specs** (`specs/**/spec.md`): contrato de COMPORTAMENTO (o quê, não como). Delta headers: `## ADDED/MODIFIED/REMOVED/RENAMED Requirements`. Cenários com `#### Scenario:` (exatamente 4 `#`), formato WHEN/THEN. Requirements usam SHALL/DEVE. Capability nova começa com `## Purpose` (50+ caracteres).

**Design** (`design.md`): o COMO. Seções: Context, Goals/Non-Goals, Decisions (com alternativas rejeitadas), Risks/Trade-offs ([Risco] → Mitigação), Migration Plan, Open Questions.

**Tasks** (`tasks.md`): checklist de implementação com `- [ ] X.Y ...` agrupadas em `## N. Grupo`. Cada task DEVE conter a verificação (teste/comando). Ordem por dependência, máximo 2h por task.

### 2.3 Revisão humana

Você lê os 4 artefatos em `openspec/changes/<nome>/`:
- Proposal e specs batem com a linha do roadmap? Nada de escopo de outra change?
- Design: concorda com as decisões (especialmente as BREAKING)?
- Tasks: cobrem happy path, segurança, falhas e E2E?

Se quiser alterar: `/opsx:update-change` (revê artefatos sem tocar código). Se ok: siga para o apply.

### 2.4 `/opsx:apply` — implementar

1. Eu sigo a ordem das tasks do `tasks.md`, uma por vez.
2. A cada task: implemento, rodo a verificação indicada (ex.: `mvn test -Dtest=...`), marco `- [x]`.
3. No fim: `mvn test` completo. **CI verde é pré-condição para prosseguir.**

### 2.5 `/opsx:verify` — conferir

Confiro se a implementação bate com specs, design e tasks:
- Toda requirement tem cenário testado? Nada implementado fora do especificado?
- Problemas encontrados viram novas tasks (ou new changes `fix-...`), nunca correções fora do OpenSpec.

### 2.6 `/opsx:sync` — mesclar specs

1. Eu leio os deltas em `openspec/changes/<nome>/specs/`.
2. Mesclo inteligentemente nas specs principais em `openspec/specs/<capability>/spec.md` (ADDED adiciona, MODIFIED substitui o bloco inteiro preservando cenários, REMOVED remove com Reason/Migration).
3. Valido com `openspec validate --specs`.

> Importante: se a change modifica uma capability existente, a spec principal PRECISA existir antes (sync da change anterior já feito). Eu fiz isso para a `foundation` antes de propor a `domain-and-api`.

### 2.7 `/opsx:archive` — encerrar

Move a change para `openspec/changes/archive/`, fecha o ciclo e registra qual requisito acadêmico ela demonstra.

## 3. Estado atual do projeto

| Change | Estado | Próximo passo |
|---|---|---|
| `foundation` (01) | Complete; specs sincronizadas | `/opsx:archive` |
| `domain-and-api` (02) | 4/4 artefatos validados | revisar artefatos → `/opsx:apply` |
| 03–10 | não criadas | `/opsx:new <nome>` na ordem do roadmap |

## 4. Sequência para os próximos itens do roadmap

Para cada item (03 `langgraph-orchestration`, 04 `ai-rag-memory-tools`, 05 `security-and-human-approval`, 06 `observability-and-resilience`, 07 `frontend`, 08 `ai-quality-and-testing`, 09 `devops-and-n8n`, 10 `final-hardening`):

1. `/opsx:new <nome-da-change>` — escopo EXCLUSIVAMENTE da linha correspondente do roadmap.
2. `/opsx:ff` (ou `/opsx:continue` se quiser passo a passo).
3. Revisar os 4 artefatos (atenção a capabilities modificadas: checar se o sync anterior já as criou).
4. `/opsx:apply` → testes (`mvn test` verde) → `/opsx:verify` → `/opsx:sync` → `/opsx:archive`.
5. Atualizar o status no `docs/roadmap.md` ao final de cada change.

## 5. Exemplo: primeiro passo para uma change nova (item 03)

**Você:** `/opsx:new langgraph-orchestration` (escopo = linha 11 do roadmap)

**Acontece:**
1. Eu executo `openspec new change "langgraph-orchestration"` (cria a pasta da change).
2. Eu leio a linha 11 do roadmap + `openspec/specs/` atual e rascunho o `proposal.md` (Why, What Changes, Capabilities, Non-goals).
3. Você revisa o proposal (momento de corrigir escopo, ex.: "grafo completo de 13 nós não cabe, dividir").

**Você:** `/opsx:ff` → gera specs + design + tasks de uma vez.

**Você revisa os 4 artefatos** → `/opsx:apply` → `mvn test` verde → `/opsx:verify` → `/opsx:sync` → `/opsx:archive`.

Regra de bolso para o proposal: **um item do roadmap = uma change = um proposal.** Se o proposal misturar escopo de duas linhas do roadmap, está errado.

## 6. Comandos CLI úteis

```
openspec list                          # changes existentes
openspec status --change "<nome>"      # artefatos e próximo passo
openspec instructions <artefato> --change "<nome>"   # template do artefato
openspec validate "<nome>"             # valida a change (deltas)
openspec validate --specs              # valida as specs principais
```

## 7. Regras não-negociáveis (resumo do AGENTS.md + config.yaml)

- Uma change = um deliverable do roadmap; nunca o projeto inteiro.
- Regras determinísticas ficam no Java (serviço), nunca no LLM (ex.: HIGH ⇒ aprovação obrigatória).
- Toda saída de LLM no domínio: structured output + validação + retry limitado.
- Conteúdo recuperado é DADO não confiável; nunca vira instrução do sistema.
- Nunca expor secrets; toda execução com trace_id; logs JSON estruturados.
- Integrações externas: timeout + retry limitado + fallback + tratamento de erro.
- Testes: happy path, segurança, falha de tool, E2E. CI verde antes de seguir.
- Prompts versionados em `resources/prompts/*-vN.txt`; refinamento só com evidência (v1 vs v2).
- Problemas viram novas changes (`/opsx:new fix-...`), nunca correções fora do OpenSpec.
