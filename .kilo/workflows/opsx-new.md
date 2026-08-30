# /opsx-new — Nova change com Kanban GitHub automático

Iniciar uma nova change OpenSpec criando o Kanban GitHub ANTES do planejamento. Nome da change vem de `$ARGUMENTS` (kebab-case ou número da linha do roadmap); se vazio, listar as pendentes do roadmap e perguntar.

**Passos**

1. Determinar o nome da change (kebab-case) e o número `NN` do roadmap (`docs/roadmap.md`); se não estiver no roadmap, usar o próximo número livre.
2. Carregar o skill `openspec-new-change` (ferramenta skill) e segui-lo do início ao fim, incluindo o passo 0 (Kanban GitHub primeiro).
3. No passo 0, executar a Fase 1 (`kanban-plan`) de `.kilo/workflows/opsx-flow.md`:
   - `. .kilo/scripts/kanban.ps1` + `Assert-GhAuth`
   - Propor decomposição: 1 tarefa pai `[NN] <título>` (label `tarefa`) + 2–6 subtarefas `[NN.M] <título>` (label `subtarefa`)
   - **Mostrar a tabela e aguardar confirmação do usuário antes de criar qualquer issue**
   - `New-KanbanIssue` → `Link-KanbanSubIssues` → `Add-KanbanItem` → pai `Ready`, subtarefas `Backlog`
   - `Set-FlowState` gravando `phase="openspec-plan"`, issues e itemIds em `.kilo/flow/<name>.json`
4. Se `.kilo/flow/<name>.json` já existir com fase além de `kanban-plan`, pular o passo 3.

**Guardrails**

- Nunca criar issues sem confirmação explícita do usuário.
- Nunca commitar secrets; nada de shell arbitrário fora do permitido.
- Ao final, informar o próximo passo: `/opsx-ff <change>`.
