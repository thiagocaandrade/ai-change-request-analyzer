# /opsx-ff — Fast-forward dos artefatos com espelho no Kanban

Criar todos os artefatos de planejamento (specs, design, tasks) mantendo o espelho com o Kanban. Nome da change vem de `$ARGUMENTS`.

**Passos**

1. Carregar o skill `openspec-ff-change` e segui-lo por completo.
2. Se `.kilo/flow/<name>.json` não existir: executar primeiro a Fase 1 (`kanban-plan`) de `.kilo/workflows/opsx-flow.md` a partir do roadmap/descrição (com confirmação do usuário) e gravar o estado.
3. Ao gerar `tasks.md`: cada grupo `## N. <título>` DEVE corresponder 1:1 a uma subtarefa `[NN.N]` do Kanban. Se a decomposição divergir durante o planejamento, reconciliar: `gh issue edit <n> --title "<novo título>"` e atualizar `.kilo/flow/<name>.json` via `Set-FlowState`.

**Guardrails**

- Nunca criar issues sem confirmação explícita do usuário.
- Ao final, informar o próximo passo: `/opsx-apply <change>`.
