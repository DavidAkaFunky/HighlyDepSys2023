# Dados

Mensagem de Commit:
  - Lista de Assinaturas de Update Accounts (soft-reads)

  - Resposta ao cliente
    - Assinatura do ( isValid + Lista de nonces )

  - isValid

A -> B: 10

```json
{
  A: Balance_A - 10,
  nonces: <nonces>
} + signature
```

Durante o commit (uponCommit) uma réplica armazena (no mínimo) 2f + 1 listas disto ^.
Para responder ao cliente, cada réplica envia as assinaturas (^) que recebeu (apenas para a transação do cliente)
(f + 1) pois:
  - imaginando que f dizem que não aconteceu, temos um correto que tem as assinaturas
  - f respondem com assinaturas deles (f < 2f + 1 => cliente não aceita) + 1
  - f respondem com algo antigo e 1 com a resposta a sério: nonces
  - operação falha, f mentem 
