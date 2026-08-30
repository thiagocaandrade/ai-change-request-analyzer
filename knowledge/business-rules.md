# Regras de Negócio

## Regras determinísticas

As regras determinísticas vivem no Java e nunca são decididas pelo LLM:

- Risco `HIGH` implica aprovação humana obrigatória, sempre.
- Confidence fora do intervalo [0, 1] é rejeitada como entrada inválida.
- O LLM apenas sugere risco; a política de risco é aplicada pela aplicação.

## Classificação de solicitações

Uma solicitação que altera regras de negócio, preços, descontos, prazos ou políticas é classificada como `business_rule`. Alterações de infraestrutura, documentação ou ferramentas são classificadas como `general`.

## Prioridade de testes

Alterações em regras financeiras ou de preço (como descontos) exigem testes de regressão da regra afetada, incluindo os limites da regra e os cenários de arredondamento.

## Aprovação

Aprovação humana é registrada por solicitação, com decisão `PENDING`, `APPROVED` ou `REJECTED`. Enquanto `PENDING`, a análise permanece pendente de aprovação.
