# Plano — Ajustes finais do fluxo automatizado Kanban + OpenSpec

## Contexto (já implementado e validado)

- Hooks Kanban/Git embutidos em `.kilocode/workflows/opsx-new.md`, `opsx-ff.md`, `opsx-apply.md`, `opsx-archive.md` (carregados pelo Kilo como `/opsx:*`).
- Orquestrador `.kilo/workflows/opsx-flow.md` (5 fases, retomável via `.kilo/flow/<change>.json`).
- Helpers `.kilo/scripts/kanban.ps1` testados contra o projeto 62 real (lookup de item + mutation de status OK; correção `--raw-field` para IDs numéricos).
- Usuário roda os comandos **só no Kilo** → NÃO espelhar hooks em `.cursor/commands/` nem `.claude/commands/opsx/`.

Comportamento esperado no exemplo do guia.md: `/opsx:new` cria pai `[NN]` + subtarefas `[NN.M]` no Kanban (após confirmação) → `/opsx:ff` espelha tasks.md → `/opsx:apply` tramita e commita por grupo na branch `feature/<change>` → `/opsx:archive` cria branches por subtarefa, PR/merge na master e tramita Kanban → Done.

## Tarefas pendentes

1. **Guarda no `opsx-ff.md`**: no passo "Create the change directory", se não existir `.kilo/flow/<name>.json`, executar a Fase 1 (kanban-plan) de `.kilo/workflows/opsx-flow.md` antes de criar a change (mesmo bloco do `opsx-new.md`). Cobre quem começa por `/opsx:ff` direto.

2. **Atualizar `guia.md` seção 5** (exemplo do item 03) para refletir os passos automáticos: Kanban no `/opsx:new`, tramitação/commits no `/opsx:apply`, branches/PR/merge/Kanban Done no `/opsx:archive`. Mencionar `/opsx-flow` como caminho alternativo de uma linha.

3. **Baseline do git (mutação, com confirmação do usuário)**: a master está suja (trabalho não commitado das changes 01/02). Commit de snapshot na master antes da primeira execução; o preflight do fluxo exige master limpa.

4. **Validação ponta a ponta com a change 03 (`langgraph-orchestration`)**:
   - `/opsx:new langgraph-orchestration` → conferir issues criadas (labels `tarefa`/`subtarefa`), sub-issues vinculadas, itens no projeto 62 (pai Ready, subtarefas Backlog), estado `.kilo/flow/langgraph-orchestration.json` gravado.
   - `/opsx:ff` → groups do tasks.md = subtarefas do Kanban.
   - `/opsx:apply` → tramitação (In progress/In review) + commit `[03.M] ...` por grupo na branch `feature/langgraph-orchestration`; `mvn test` verde.
   - verify → sync → `/opsx:archive` → branches `feature/langgraph-orchestration-03.m`, PRs mergeados na master, subtarefas → Done + issues fechadas, pai → Done.
   - Falha em qualquer passo: corrigir script/workflow e reexecutar (fluxo retomável pelo estado).

## Notas operacionais

- Novos comandos devem ir em `.kilo/workflows/` (o validador de `.kilo/command/` está quebrado nesta versão do Kilo — qualquer arquivo lá falha "Failed to parse frontmatter").
- `.kilo/flow/` está no `.gitignore` (estado local por máquina).
- Issues nunca são criadas sem confirmação humana; PR + merge com fallback para merge direto se PR não for possível.

## Riscos

- Cherry-pick de grupo em conflito → resolver mantendo a versão de `feature/<change>`.
- PR bloqueado (permissões) → fallback `git merge --no-ff` + push.
- Sessão interrompida entre fases → estado `.kilo/flow/<change>.json` permite retomar sem duplicar issues/branches.

## Fora de escopo

- Espelhamento dos hooks em Cursor/Claude Code (usuário roda só no Kilo).
- Automação de n8n/CI (mudanças posteriores do roadmap).
