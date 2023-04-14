# Byzantine Client
Transfer from other accounts to himself

Alterar o transfer para fazer a operacao ao contrario
transfer 12 1  ---> self receber do 12 

**WORKING**
**PARA MOSTRAR AO BALTASAR: Isto recusa logo o pedido, logo tem de se fazer mais um para o consenso ocorrer**

# Byzantine Nodes

## Leader
1. Nao meter no bloco operacoes de um cliente especifico
  Expected: timer explodir

**WORKING**
**PARA MOSTRAR AO BALTASAR: Mostrar isto a fazer consensos e esperar 30s para imprimir mensagem a reclamar**

2. Nao fazer bloco (aka nao comecar consenso)
  Expected:

**WORKING**
**PARA MOSTRAR AO BALTASAR: Mostrar isto a nao fazer consensos e esperar 30s para imprimir mensagem a reclamar**

4. Lider tirar mais do que devia na fee
  Expected: outros recusam updateaccount e o consenso prossegueu com o valor
    correto

5. Modificar requests que recebeu que mete no bloco
  Expected: arrebentar nas assinaturas

6. Adicionar requests falsos que aumentam o seu balance
  Expected: arrebentam na assinatura

## Non-Leader

7. Dont respond to consensus protocol messages
  Expected: continua e funciona

8. Sending messages with wrong signatures
  Expected: alguem reclama

9. Pretending to be leader when it is not
  Expected: arrebenta

10. Sending different messages to different nodes
  Expected

11. Sending messages with a value different from the one pre-prepared
  Expected 

12. Weak read devolver garbage (update account errado ou assinaturas erradas)
  Expected: client falha a verificar
