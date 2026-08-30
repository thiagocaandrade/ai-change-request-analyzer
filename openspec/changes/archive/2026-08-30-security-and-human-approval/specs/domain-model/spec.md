## MODIFIED Requirements

### Requirement: Modelo de domínio persistente

O sistema DEVE persistir solicitações de mudança com análise estruturada composta por achados de impacto, avaliação de risco, avaliação de segurança, recomendações de teste e aprovação, todas recuperáveis por identificador. A aprovação DEVE registrar approver, decisão, momento da decisão e trace_id quando uma decisão humana é tomada.

#### Scenario: Análise completa persistida

- **WHEN** uma solicitação possui análise com achados de impacto, avaliação de risco, avaliação de segurança, recomendações de teste e estado de aprovação
- **THEN** todas as partes são persistidas de forma relacionada e recuperáveis pela solicitação

#### Scenario: Decisão humana persistida na aprovação

- **WHEN** uma decisão humana de aprovação é registrada
- **THEN** a aprovação persistida carrega approver, decisão, momento da decisão e trace_id da execução

#### Scenario: Eventos de segurança recuperáveis

- **WHEN** uma análise registra eventos de segurança
- **THEN** os eventos são persistidos vinculados à solicitação e recuperáveis junto da análise
