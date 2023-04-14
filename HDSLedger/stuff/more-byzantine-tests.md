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

3. Líder tira mais do que devia na fee
  Expected: outros recusam updateaccount e o consenso prossegueu com o valor
    correto
  **WORKING**
  **PARA MOSTRAR AO BALTASAR: Mostrar isto fazer consensos e descontar certo mas avisa que houve updateAccount diferente**

4. Modificar requests que recebeu que mete no bloco
  Expected: arrebentar nas assinaturas
  **WORKING**
  **PARA MOSTRAR AO BALTASAR: Mostrar avisos das assinaturas não darem match + queixas da censura**

5. Adicionar requests falsos que aumentam o seu balance
  Expected: arrebentam na assinatura
  **WORKING**
  **PARA MOSTRAR AO BALTASAR: Mostrar avisos das assinaturas não darem match + queixas da censura**

12. Weak read devolver garbage (update account errado ou assinaturas erradas)
   **WORKING**
  **PARA MOSTRAR AO BALTASAR: Fazer balance 10 weak e mostrar que dá invalid response**

13. Force consensus read
  Expected: works
  **WORKING**  
  **PARA MOSTRAR AO BALTASAR: criar 3 contas fazer duas transferencias e um read (da trigger ao consenso) **

14. different blocks to different nodes
 Expected: either consensus doesnt progress or all decide the same

## Non-Leader

6. Dont respond to consensus protocol messages
  Expected: continua e funciona
  **WORKING**

7. Sending messages with wrong signatures
  **IMPLICITLY COVERED IN OTHER TESTS**

8. Node identifies itself correctly but claims to be a leader
  **REPEATED FROM 1ST STAGE, TESTING TO DO**

9. Node tries to steal the actual leader's identity
  **TESTING TO DO**

10. Sending different messages to different nodes
  Expected

11. Sending messages with a value different from the one pre-prepared
  **ALREADY COVERED IN TESTS THAT MODIFY THE BLOCK**