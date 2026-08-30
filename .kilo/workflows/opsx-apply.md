# /opsx-apply — Implementação com commits por grupo e tramitação automática

Implementar as tasks da change com commits por grupo e tramitação automática no Kanban. Nome da change vem de `$ARGUMENTS`.

**Passos**

1. Carregar o skill `openspec-apply-change` e segui-lo por completo, incluindo o bloco "Kanban + git (fluxo automatizado)".
2. Se `.kilo/flow/<name>.json` não existir: executar primeiro a Fase 1 (`kanban-plan`) de `.kilo/workflows/opsx-flow.md` (com confirmação do usuário) e gravar o estado.
3. Branch de integração: `git checkout master` + `git checkout -b feature/<change>` (master permanece limpa; toda a implementação fica nesta branch).
4. A cada grupo `## N.` do `tasks.md`:
   - Antes do grupo: subtarefa correspondente → `InProgress` (`Set-KanbanStatus` com o itemId do estado)
   - Depois de todas as tasks do grupo `- [x]` e verificação passando: `git add <arquivos do grupo>` + `git commit -m "[NN.M] <título da subtarefa>"` + subtarefa → `InReview`
5. No fim: `mvn test` completo verde (Windows: `.\mvnw.cmd test`; Linux: `./mvnw test`). CI verde é pré-condição para prosseguir.

**Guardrails**

- Nunca commitar secrets/`.env` real.
- Marcar `- [x]` apenas quando o comportamento especificado estiver implementado e testado.
- Ao final, informar o próximo passo: `/opsx-verify` → `/opsx-sync` → `/opsx-archive <change>`.
