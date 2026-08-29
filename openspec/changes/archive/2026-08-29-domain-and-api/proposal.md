## Why

A foundation persiste o resultado da análise como JSON opaco em `ChangeRequest.result` (evidência: `src/main/java/com/ai/change/request/analyzer/domain/ChangeRequest.java`). Sem modelo de domínio tipado e sem regras determinísticas, as fases seguintes do roadmap (grafo LangGraph, IA/RAG/memória, aprovação humana) não têm onde persistir achados, risco e plano de testes. É a change 02 do roadmap (FASE 5, `docs/roadmap.md` linha 10), em ordem obrigatória.

## What Changes

- Novas entidades JPA: `ChangeAnalysis`, `ImpactFinding`, `RiskAssessment`, `TestRecommendation`, `Approval`; enums `RiskLevel` (LOW/MEDIUM/HIGH) e `ApprovalStatus` (PENDING/APPROVED/REJECTED).
- `ChangeRequest` passa a referenciar a análise estruturada (1:1) e a aprovação, substituindo o campo `result` (JSON opaco) **BREAKING** para quem consome o resultado persistido.
- Serviço de regras determinísticas em Java (AGENTS.md decisão 4): risco HIGH ⇒ aprovação obrigatória, sempre — independente de qualquer sugestão do LLM; confidence fora de [0,1] ⇒ dado inválido rejeitado com erro de validação.
- API REST tipada: endpoints de solicitação migram de `/requests` para `/api/change-requests` (base canônica usada pelo roadmap nas changes 05 e 09) **BREAKING**; novos endpoints para persistir e consultar análise estruturada.
- Testes unitários: regras determinísticas, validação de confidence, mapeamento JPA e cenários de risco (happy path, HIGH, dados inválidos).

## Non-goals

- Sem mudanças no grafo LangGraph (change 03); sem IA/RAG/tools/busca de histórico (change 04); sem detecção de prompt injection e sem endpoint de decisão humana (change 05) — a entidade `Approval` nasce aqui, mas o fluxo de aprovação humana não.
- Sem regras de negócio definidas por LLM; sem frontend novo; sem infraestrutura adicional.

## Capabilities

### New Capabilities

- `domain-model`: entidades do domínio, enums, regras determinísticas de risco (HIGH ⇒ aprovação obrigatória) e validação de confidence, cobertas por testes unitários.
- `change-api`: endpoints REST tipados para solicitações de mudança e análises estruturadas (criação, persistência e consulta), com DTOs validados.

### Modified Capabilities

- `request-pipeline`: o requisito de recepção/consulta passa a usar a base `/api/change-requests` em vez de `/requests`, e o resultado persistido passa a ser análise estruturada tipada em vez de JSON opaco.

## Impact

- Código Java: pacotes `domain/` (novas entidades, enums, serviço de regras), `web/` ou `api/` (endpoints novos; remoção dos antigos sob `/requests`), testes em `src/test`.
- Banco: novas tabelas `change_analysis`, `impact_finding`, `risk_assessment`, `test_recommendation`, `approval` + evolução de `change_request` (AGENTS.md decisão 6 — memória = persistência de domínio).
- Dependências: nenhuma nova — Spring Data JPA/PostgreSQL já presentes pela foundation.
- Consumidores: controller web e testes da foundation ajustados à nova rota e ao novo shape de resposta; agente Python não é afetado (continua recebendo request_id e texto).
