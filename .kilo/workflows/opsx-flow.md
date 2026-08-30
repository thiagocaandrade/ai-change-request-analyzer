# /opsx-flow — Fluxo completo automatizado (Kanban GitHub + OpenSpec + Git)

Orquestra o ciclo inteiro de uma change: cria tarefas/subtarefas no Kanban GitHub, tramita status, executa o fluxo OpenSpec, cria branches por tarefa/subtarefa, faz commit, merge na master e finaliza o Kanban (Done + issues fechadas).

**Entrada**: `$ARGUMENTS` = nome da change (kebab-case) ou número da linha do roadmap (`docs/roadmap.md`). Se vazio, listar as changes pendentes do roadmap e perguntar.

**Resumo das fases** (cada fase grava `.kilo/flow/<change>.json`; reexecutar `/opsx-flow <change>` retoma da fase pendente):

1. `preflight` — ambiente e git limpo
2. `kanban-plan` — cria tarefa pai `[NN]` + subtarefas `[NN.M]` no Kanban
3. `openspec-plan` — `/opsx-new` + `/opsx-ff`, tasks.md espelha as subtarefas do Kanban
4. `apply` — implementa, tramita subtarefas, commit por grupo
5. `deliver` — após `/opsx-archive`: branches por tarefa/subtarefa, commit, merge na master, Kanban → Done

Regras não-negociáveis: nunca commitar secrets; CI (`mvn test`) verde antes de `deliver`; uma change = uma tarefa pai; o LLM apenas propõe a decomposição — só criar issues depois de confirmar com o usuário.

## Fase 0 — preflight

1. `gh auth status` e acesso ao projeto (`Assert-GhAuth` do script).
2. `git status --porcelain` e `git log --oneline -3`. Se a working tree estiver suja, PARAR e avisar: o fluxo exige master limpa e commitada como baseline (as entregas anteriores precisam estar commitadas antes de iniciar). Pedir ao usuário para commitar ou autorizar um snapshot.
3. Carregar helpers: `. .kilo/scripts/kanban.ps1`
4. Ler `.kilo/flow/<change>.json` (se existir, mostrar fase atual e retomar de lá).
5. Determinar o número `NN` do roadmap em `docs/roadmap.md` para a change (ex.: change 03 `langgraph-orchestration` → `03`). Se a change não estiver no roadmap (ex.: `fix-...`), usar o próximo número sequencial livre.

## Fase 1 — kanban-plan (criar tarefas/subtarefas ANTES do OpenSpec)

1. Ler a linha do roadmap da change (escopo) e a estrutura de `tasks.md` de changes arquivadas para calibrar a granularidade.
2. Propor decomposição: 1 issue pai + 2–6 subtarefas:
   - Pai: título `[NN] <título do roadmap>`, label `tarefa`
   - Subtarefa: título `[NN.M] <subtítulo>`, label `subtarefa`
3. **Pausar e mostrar a tabela proposta** (títulos + labels). Só criar após o usuário confirmar.
4. Criar issues (nesta ordem):
   - Pai: `New-KanbanIssue -Title "[NN] ..." -Label tarefa -Body "<corpo: escopo, critérios de aceite, link da linha do roadmap>"`
   - Subtarefas: `New-KanbanIssue -Title "[NN.M] ..." -Label subtarefa -Body "<corpo: descrição + critérios>"`
5. Vincular sub-issues: `Link-KanbanSubIssues -ParentNumber <pai> -SubNumbers @(<m1>,<m2>,...)` (cria tasklist no corpo do pai).
6. Adicionar ao projeto e tramitar: `Add-KanbanItem` para cada issue; `Set-KanbanStatus -ItemId <id> -Status Ready` (pai) e `Backlog` (subtarefas).
7. Gravar estado:
   `Set-FlowState -Change <change> -State @{ change="..."; phase="openspec-plan"; roadmapNumber=NN; parentIssue=<n>; parentItemId="PVTI_..."; subtasks=@(@{id="NN.1";title="...";issue=<n>;itemId="PVTI_..."}, ...); branch="feature/<change>" }`

## Fase 2 — openspec-plan

1. Mover pai para `InProgress` (tramitar a tarefa): `Set-KanbanStatus`.
2. Executar o fluxo OpenSpec de planejamento: carregar e seguir o skill `openspec-new-change` (com o nome da change) e depois `openspec-ff-change` (ou `openspec-continue-change`).3. **tasks.md espelha o Kanban**: cada grupo `## N. <título>` do tasks.md DEVE corresponder 1:1 a uma subtarefa `[NN.N]` do Kanban. Se a decomposição mudar durante o planejamento, reconciliar: `gh issue edit <n> --title "<novo título>"` e atualizar o estado em `.kilo/flow/<change>.json`.
4. Revisão humana dos 4 artefatos (pausar).
5. Criar a branch de integração: `git checkout master` + `git checkout -b feature/<change>`. (Todo o apply acontece nesta branch; master permanece limpa.)
6. Atualizar estado: `phase="apply"`.

## Fase 3 — apply (implementação + commits por grupo)

1. Carregar e seguir o skill `openspec-apply-change` na branch `feature/<change>`.
2. Integração Kanban/commit durante o apply (a cada grupo `## N.` do tasks.md):
   - Antes do grupo: subtarefa correspondente → `InProgress`.
   - Depois de concluir todas as tasks do grupo e a verificação indicada:
     `git add -A` + `git commit -m "[NN.M] <título da subtarefa>"` + subtarefa → `InReview`.
3. No fim: `mvn test` completo verde (CI verde é pré-condição para seguir). Se falhar, corrigir antes de continuar.
4. Executar `openspec-verify-change` e `openspec-sync-specs`.
5. Executar `openspec-archive-change` (arquiva a change em `openspec/changes/archive/`).
6. Atualizar estado: `phase="deliver"`.

## Fase 4 — deliver (após o archive: branches, commit, merge, Kanban Done)

Pré-condição: todos os commits de grupo estão em `feature/<change>`, `mvn test` verde, change arquivada.

1. Obter os commits por grupo: `git log --oneline feature/<change> --not master` (1 commit `[NN.M]` por subtarefa).
2. Para cada subtarefa em ordem (`NN.1`, `NN.2`, ...):
   - `git checkout master` + `git pull --ff-only origin master` (se remoto disponível)
   - `git checkout -b feature/<change>-<nn.m>`
   - Aplicar o commit do grupo: `git cherry-pick <sha>` (base master já contém grupos anteriores, então aplica limpo; em conflito, resolver mantendo a versão de `feature/<change>`)
   - `git push -u origin feature/<change>-<nn.m>`
   - PR: `gh pr create --base master --head feature/<change>-<nn.m> --title "[NN.M] <título>" --body "Closes #<issue da subtarefa>"` e `gh pr merge --merge --delete-branch`
   - Fallback (sem PR permitido): `git checkout master` + `git merge --no-ff feature/<change>-<nn.m>` + `git push origin master`
   - Tramitar: `Set-KanbanStatus -ItemId <itemId> -Status Done` + `Close-KanbanIssue -IssueNumber <n> -ItemId <itemId>`
3. Pai (`[NN]`): a branch da tarefa pai é `feature/<change>` (integração); após todas as subtarefas mergeadas, master já contém tudo. Tramitar pai → `Done` + `Close-KanbanIssue`.
4. Limpar branches locais já mergeadas (opcional).
5. Atualizar `docs/roadmap.md` (status da change) e registrar no estado: `phase="done"`.

## Saída final

Tabela: change, issues (pai + subtarefas com números e status final), branches criadas, PRs/merges, link do Kanban (`https://github.com/orgs/IA-para-DEVs-SCTEC-T2/projects/62`).

## Guardrails

- Falha em qualquer passo: registrar erro no estado e parar; reexecutar `/opsx-flow <change>` retoma sem duplicar (checar existência antes de criar issues/branches).
- Nunca criar issues sem confirmação do usuário.
- Nunca pular CI. Nunca commitar secrets/`.env` real.
- Não fazer merge de master sem os testes verdes.
- Preferir PR + merge; fallback direto só se PR não for possível.
