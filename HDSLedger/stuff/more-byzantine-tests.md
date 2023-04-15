# Byzantine Client
## For One Client
### GREEDY CLIENT
Transfer from other accounts to himself

Alterar o transfer para fazer a operacao ao contrario
transfer 12 1  ---> self receber do 12 
**WORKING**
**PARA MOSTRAR AO BALTASAR: Isto recusa logo o pedido, logo tem de se fazer mais um para o consenso ocorrer**

Implementação:
O cli funciona normalmente, a library tem uma condição para verificar se o cliente é `GREEDY_CLIENT` e nesse caso,
troca a source e destination dos transfer requests. Isto não passa pois a chave da source e de quem enviou o pedido são diffs
(apenas isso e não precisa verificar assinaturas,... pois os canais já garantem autenticação).

# Byzantine Nodes

## Leader
### DICTATOR LEADER
1. Nao meter no bloco operacoes de um cliente especifico 
  Expected: timer explodir
  **WORKING**
  **PARA MOSTRAR AO BALTASAR: Mostrar isto a fazer consensos e esperar 30s para imprimir mensagem a reclamar**

Implementação:
O cliente faz sempre broadcast das operações para `f + 1` réplicas, incluíndo o lider. No pior caso apenas uma é correta.
Se passar um threshold de tempo desde que a réplica correta recebeu o pedido e ainda não foi emitido nenhum bloco com o mesmo,
a réplica reclama. (Na realidade iria enviar um `complain` ou assim)
O cliente que é censurado é escolhido aleatóriamente pelo Líder sempre que o sistema é inicializado.

### SILENT LEADER
2. Nao fazer bloco (aka nao comecar consenso)
  Expected:
  **WORKING**
  **PARA MOSTRAR AO BALTASAR: Mostrar isto a nao fazer consensos e esperar 30s para imprimir mensagem a reclamar**

Implementação:
Muito semelhante ao caso anterior mas generalizado.

### LANDLORD LEADER
3. Líder tira mais do que devia na fee
  Expected: outros recusam updateaccount e o consenso prossegueu com o valor
    correto
  **WORKING**
  **PARA MOSTRAR AO BALTASAR: Mostrar isto fazer consensos e descontar certo mas avisa que houve updateAccount diferente**

Implementação:
Ao inicializar o sistema o lider aumenta a sua propria fee (fixa e guardada numa variavel),
logo, ao taxar qualquer operacao de escrita ira cobrar mais do que o esperado pelas
outras replicas. Isto é detetado ao verificar os quoruns das mensagens de commit
pois os update balances gerados pelo lider sao != dos restantes. Apesar disto
como é possivel alcançar um quorum, o consenso termina com o resultado correto (ate no lider).

### HANDSY LEADER
4. Modificar requests que recebeu que mete no bloco
  Expected: arrebentar nas assinaturas
  **WORKING**
  **PARA MOSTRAR AO BALTASAR: Mostrar avisos das assinaturas não darem match + queixas da censura**

Implementação:
SO MODIFICA TRANSFERS.
Ao receber uma transfer, o lider ira duplicar o valor da mesma e somar mais um.
(rebenta no uponPrePrepare)

### CORRUPT LEADER
5. Adicionar requests falsos que aumentam o seu balance
  Expected: arrebentam na assinatura
  **WORKING**
  **PARA MOSTRAR AO BALTASAR: Mostrar avisos das assinaturas não darem match + queixas da censura**

Implementação:
Ao receber uma transfer de qualquer cliente, o lider vai injetar na propria mempool uma nova
transfer desse cliente para si proprio (assinada pelo lider). Isto vai falhar pois
no uponPrePrepare verificamos se as transacoes sao assinadas pela source da mesma.

12. Weak read devolver garbage (update account errado ou assinaturas erradas)
   **WORKING**
  **PARA MOSTRAR AO BALTASAR: Fazer balance 10 weak e mostrar que dá invalid response**

Implementação:
Somamos mais um no NodeService(read) para o cliente rebentar e dizer que o update account
nao bate certo com as assinaturas do mesmo.

13. Force consensus read
  Expected: works
  **WORKING**  
  **PARA MOSTRAR AO BALTASAR: criar 3 contas fazer duas transferencias e um read (da trigger ao consenso)**

Implementação:
Semelhante ao anterior mas cada replica soma o seu port ao valor do update account.
Isto vai fazer comecar um consenso.

### Bad Broadcast
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
  **IMPLICITLY COVERED IN OTHER TESTS**

11. Sending messages with a value different from the one pre-prepared
  **ALREADY COVERED IN TESTS THAT MODIFY THE BLOCK**
