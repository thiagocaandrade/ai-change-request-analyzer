# /opsx-archive — Arquiva e entrega: branches, PR/merge e Kanban Done

Arquivar a change e executar a entrega Git + Kanban (Fase 4 `deliver` de `.kilo/workflows/opsx-flow.md`). Nome da change vem de `$ARGUMENTS`.

**Passos**

1. Carregar o skill `openspec-archive-change` e segui-lo por completo, incluindo o passo 7 (Entrega Git + Kanban).
2. Pré-condição obrigatória: `mvn test` verde (rodar e conferir antes de qualquer merge na master).
3. Se `.kilo/flow/<name>.json` não existir: executar a Fase 1 (`kanban-plan`) a partir dos grupos do `tasks.md` (com confirmação do usuário) e, se houver alterações não commitadas, separá-las por grupo.
4. Para cada subtarefa `[NN.M]` em ordem:
   - `git checkout master` + `git pull --ff-only origin master`
   - `git checkout -b feature/<change>-<nn.m>`
   - Aplicar o commit do grupo: `git cherry-pick <sha>` (se ainda não commitado: `git add` dos arquivos do grupo + `git commit -m "[NN.M] <título>"`)
   - `git push -u origin feature/<change>-<nn.m>`
   - PR: `gh pr create --base master --head feature/<change>-<nn.m> --title "[NN.M] <título>" --body "Closes #<issue>"` e `gh pr merge --merge --delete-branch` (conferir CI verde antes; fallback sem PR: `git merge --no-ff` na master + push)
   - Depois do merge: `Set-KanbanStatus -ItemId <itemId> -Status Done` + `Close-KanbanIssue -IssueNumber <n> -ItemId <itemId>`
5. Pai `[NN]`: após todas as subtarefas mergeadas, `Set-KanbanStatus Done` + `Close-KanbanIssue`.
6. Atualizar `docs/roadmap.md` (status da change), commitar (`docs: roadmap <NN> concluída`) e `Set-FlowState` com `phase="done"`.

**Guardrails**

- Nunca mergear na master sem testes verdes; nunca commitar secrets.
- Nunca arquivar com sync de specs em voo; rodar o sync inline e verificar antes de mover `changeRoot`.
- Ao final, mostrar a tabela de saída: issues, branches, PRs, link do Kanban (https://github.com/orgs/IA-para-DEVs-SCTEC-T2/projects/62).
