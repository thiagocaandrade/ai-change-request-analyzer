# Política de Descontos

## Desconto de clientes VIP

Clientes VIP recebem desconto de 10% em todas as compras.

A regra atual: `VIP_DISCOUNT_RATE = 0.10` (10 por cento).

## Regras adicionais

- O desconto VIP é aplicado sobre o valor total do pedido, antes de impostos.
- Descontos não são cumulativos com outras promoções, salvo definição explícita.
- Alterações na política de descontos exigem aprovação humana quando classificadas como risco HIGH.

## Testes associados

Testes devem cobrir o cálculo do desconto com o valor atual e com o valor proposto, incluindo arredondamento e pedidos sem direito ao desconto.
