## 1. Modelo de domínio tipado

- [x] 1.1 Criar enums `RiskLevel` (LOW/MEDIUM/HIGH) e `ApprovalStatus` (PENDING/APPROVED/REJECTED) em `domain/`; verificar com `mvn test-compile`
- [x] 1.2 Criar entidades JPA `ChangeAnalysis`, `ImpactFinding`, `RiskAssessment`, `TestRecommendation`, `Approval` com IDs UUID, relacionamentos conforme D1 do design e nomes de tabela explícitos (`change_analysis`, `impact_finding`, `risk_assessment`, `test_recommendation`, `approval`); verificar com `mvn test-compile`
- [x] 1.3 Evoluir `ChangeRequest`: remover campo `result`, adicionar relação 1:1 com `ChangeAnalysis` e `Approval`; verificar com `mvn test-compile`

## 2. Regras determinísticas

- [x] 2.1 Criar `RiskPolicy` (serviço Java puro) com regras: HIGH ⇒ `approvalRequired=true` + `ApprovalStatus.PENDING`; confidence fora de [0,1] ⇒ `InvalidConfidenceException`; verificar com teste unitário `RiskPolicyTest` cobrindo LOW/MEDIUM/HIGH e confidence inválida
- [x] 2.2 Criar `RiskPolicyTest` com cenários: risco HIGH exige aprovação mesmo com sugestão externa LOW; confidence 1.5 e -0.1 rejeitadas; verificar com `mvn test -Dtest=RiskPolicyTest`

## 3. Persistência

- [x] 3.1 Criar repositórios `ChangeAnalysisRepository`, `ImpactFindingRepository`, `RiskAssessmentRepository`, `TestRecommendationRepository`, `ApprovalRepository`; verificar com `mvn test-compile`
- [x] 3.2 Criar `@DataJpaTest` para mapeamentos: análise persistida com achados/risco/testes/aprovação é recuperável integralmente pela solicitação; verificar com `mvn test -Dtest=ChangeAnalysisRepositoryTest`

## 4. Serviço de aplicação

- [x] 4.1 Criar `AnalysisService` aplicando `RiskPolicy` no registro de análise (única porta de entrada), sem lógica em setters; verificar com teste de serviço cobrindo HIGH→PENDING e payload inválido
- [x] 4.2 Criar `AgentResultMapper` para conversão defensiva da resposta do agente stub em `ChangeAnalysis` (campos ausentes ⇒ análise vazia, nunca falha); verificar com teste unitário do mapper com respostas completas, parciais e vazias

## 5. API REST

- [x] 5.1 Criar DTOs record: `CreateAnalysisRequest` (achados, risco com confidence, recomendações) e `ChangeRequestResponse` estendido com resumo da análise; validação com Bean Validation; verificar com `mvn test-compile`
- [x] 5.2 Migrar rotas de `/requests` para `/api/change-requests` no controller; adicionar `POST /api/change-requests/{id}/analysis` e `GET /api/change-requests/{id}/analysis`; verificar com testes de controller
- [x] 5.3 Adicionar handlers no `GlobalExceptionHandler` para `MethodArgumentNotValidException` (400) e `InvalidConfidenceException` (400), sem expor stack traces; verificar com teste de erro estruturado

## 6. Testes e integração

- [x] 6.1 Criar testes `@WebMvcTest` para a API: criar solicitação (201), texto vazio (400), análise válida (200), risco HIGH ⇒ PENDING, payload inválido (400), solicitação inexistente (404); verificar com `mvn test -Dtest=ChangeRequestControllerTest`
- [x] 6.2 Ajustar testes existentes da foundation (`ChangeRequestControllerTest`, `ChangeRequestRepositoryTest`, `AnalyzerApplicationTests`) à nova rota e ao domínio sem `result`; verificar com `mvn test -Dtest='*ControllerTest,*RepositoryTest,AnalyzerApplicationTests'`
- [x] 6.3 Rodar `mvn test` completo e confirmar CI verde (suite inteira passa, incluindo testes da foundation)

## 7. Evidência

- [x] 7.1 Atualizar README com as novas rotas `/api/change-requests*`, as tabelas do domínio e a regra determinística HIGH→aprovação; verificar que a documentação corresponde ao comportamento testado
