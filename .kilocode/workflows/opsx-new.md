Start a new change using the experimental artifact-driven approach.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Once selected, treat `--store <id>` as sticky for the rest of the workflow. Every unscoped example of those commands below is shorthand: before running it, append the flag. For example, run `openspec status --change "<name>" --json --store "<id>"`, not the unscoped form shown below. Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Input**: The argument after `/opsx-new` is the change name (kebab-case), OR a description of what the user wants to build.

**Steps**

0. **Kanban GitHub primeiro (fluxo automatizado)**

   Antes de criar a change, execute a Fase 1 (`kanban-plan`) do workflow `/opsx-flow` (`.kilo/workflows/opsx-flow.md`):

   - Se `.kilo/flow/<name>.json` já existe e tem `phase` além de `kanban-plan`, pule este passo.
   - Carregue helpers: `. .kilo/scripts/kanban.ps1`
   - Derive do `docs/roadmap.md` (ou da descrição do usuário) a decomposição: 1 tarefa pai `[NN] <título>` (label `tarefa`) + 2–6 subtarefas `[NN.M] <título>` (label `subtarefa`). `NN` = número da linha do roadmap.
   - Mostre a tabela proposta e aguarde confirmação do usuário. Não crie issues sem confirmação.
   - Crie as issues (`New-KanbanIssue`), vincule sub-issues (`Link-KanbanSubIssues`), adicione ao projeto 62 (`Add-KanbanItem`) e tramite: pai → `Ready`, subtarefas → `Backlog` (`Set-KanbanStatus`).
   - Grave `.kilo/flow/<name>.json` (`Set-FlowState`) com fase `openspec-plan`, números das issues e itemIds.

   Depois siga para o passo 1 abaixo, com o fluxo OpenSpec normal.

1. **If no input provided, ask what they want to build**

   Ask the user (open-ended, no preset options):
   > "What change do you want to work on? Describe what you want to build or fix."

   From their description, derive a kebab-case name (e.g., "add user authentication" → `add-user-auth`).

   **IMPORTANT**: Do NOT proceed without understanding what the user wants to build.

2. **Determine the workflow schema**

   Use the default schema (omit `--schema`) unless the user explicitly requests a different workflow.

   **Use a different schema only if the user mentions:**
   - A specific schema name → use `--schema <name>`
   - "show workflows" or "what workflows" → run `openspec schemas --json` and let them choose

   **Otherwise**: Omit `--schema` to use the default.

3. **Create the change directory**
   ```bash
   openspec new change "<name>"
   ```
   Add `--schema <name>` only if the user requested a specific workflow.
   This creates a scaffolded change in the planning home resolved by the CLI.

4. **Show the artifact status**
   ```bash
   openspec status --change "<name>" --json
   ```
   Use the returned `planningHome`, `changeRoot`, `artifactPaths`, and `nextSteps` instead of assuming repo-local paths.

5. **Get instructions for the first artifact**
   The first artifact depends on the schema. Check the status output to find the first artifact with status "ready".
   ```bash
   openspec instructions <first-artifact-id> --change "<name>"
   ```
   This outputs the template and context for creating the first artifact.

6. **STOP and wait for user direction**

**Output**

After completing the steps, summarize:
- Change name and location
- Schema/workflow being used and its artifact sequence
- Current status (0/N artifacts complete)
- The template for the first artifact
- Prompt: "Ready to create the first artifact? Run `/opsx-continue` or just describe what this change is about and I'll draft it."

**Guardrails**
- Do NOT create any artifacts yet - just show the instructions
- Do NOT advance beyond showing the first artifact template
- If the name is invalid (not kebab-case), ask for a valid name
- If a change with that name already exists, suggest using `/opsx-continue` instead
- Pass --schema if using a non-default workflow
