LAB
    - é preciso suportar leader bizantino
    - é preciso notificar que o lider é bizantino (timer expirou, p.ex)
    - lider pode ignorar pedido ou nao enviar resposta


SOLUTION
    - enviar para todos (porque o lider pode dropar), melhoria seria enviar para f+1
    - preparar um timer, se o timer expirar antes de receber  o pre prepare: BOOM
    - no commit f+1 (2f+1 tambem pode funcionar) tem de responder ao cliente
    - 2f+1 tambem funciona (é melhor ? nao percebi) é preciso dar ack
    - é preciso enviar as assinaturas (um quorum de assinaturas)
    - client tem de assinar e todas as replicas tem de receber esta assinatura para verificar
    - client tem de ter nonce para prevenir replay attacks


  C->L    L->PP     PP->P       P->C         C->L

    1       n-1     (n-1)(n-1)  n^2-n         1   
                                n^2-n acks    1 ack 
                                
    f+1  (porque lider bizatino)              f+1 ou 2f+1  (porque lider bizantino)
                                              2f+1 acks se 2f+1

    Sobre os ACKS: provavelmente vamos ter de fazer com que o perfect link esteja aware do
    tipo de mensagens que sao enviadas pelo consenso

    2f+1 apenas é preciso dizer o valor final (só precisa receber f+1 mensagens iguais no lado do cliente)
    f+1 seria preciso enviar as assinaturas e o valor final ()
    temos de garantir que as assinaturas sao todas sobre o mesmo valor final (dificil?)
    o cliente precisa de conseguir confirmar estas assinaturas

    como fazer acks
    - exponential backoff para o timer
    como fazer deduplication
    - sliding window (perfect)  [min mensgID, max mensgID] , hashset para guardar mensagens recebidas
    - hashset (ok)


    Assinaturas antigas:
    val -> assinado pelo C -> assinado pelo L -> assinado pela replica
    o commit é um array de 

    Assinaturas
    - objetivo usar menos assinaturas
    - no report: ser claro nas assinaturas (o porque de assinar X )
    - apos o prepare o valor do cliente ja foi propagado de forma segura, ja podemos p.ex enviar mensagens de commit com a instancia/round em vez de ter o valor do cliente

    COMO RESOLVER SOFT READ

    Assina que a conta mudou o seu saldo
    Ao enviar a a mensagem de commit processar qual o saldo final de cada conta e assinamos a conta
    Ao receber a mensagem de commit, guardar estas assinaturas 
    no entanto é preciso confirmar que um no bizantino nao submeteu uma assinatura para um balanco falso




    STAGE 2

    - block size -> parameter para fazer debug (default 10)

    vai ser clarificado mas se nao for façam o que quiserem
        - create_account and check_balance deve estar na blockchain?
        - create_account paga uma fee ?
        - invalid operation on the block invalida o block todo ou nao ?


    o lider cria blocos com as operacoes que tem 
    as operacoes sao verificas no fim do consenso, se a operacao for invalida
    o bloco fica invalido

    strongly consistent -> linearizable  (not necessarilly the most recent state but a recent state)
    fazer PBFT (OSDI '99 nota: o LAMPORT NAO FEZ ISTO, ATIRAR CADEIRA)
    weakly consistent -> 
        a consensus instance devia crescer 
        (ou seja se eu ja vi 7 o weakly read tem de dar pelo menos >7, se quisermos ser fixes)


    possibilidade: create_accounts acontece sempre primeiro num bloco
    ou nao


DUVIDAS PARA LAB
    - cliente bloqueia até receber confirmação de que foi appended à blockchain ?
        ou até receber confirmação de que tem "autoridade" (enunciado) ?
        ou nunca bloqueia ?

        R: clients dont block
        R: Só recebe confirmação no fim do consenso

    - quando responder ao cliente ? 
        é quando tem a "autoridade" = quando?
        caso seja rejeitado e caso seja aceite ? quando for aceite o bloco ou quando for validada ?

        R: Só recebe confirmação no fim do consenso

    - apenas o lider valida as transacoes ou todos validam ? 
        e se chegam operacoes com diferentes ordens ? pode gerar certas transacoes aceites nuns nós e outras nao

        se só o líder validar, pode ser bizantino e
        criar estados inconsistentes

        R: todos verificam NO FIM DO CONSENSO

    - create account pode ser omitido (putIfAbsent) ? tem de ir para a blockchain ?

        R: nao (debativel)

    - temos de garantir fifo nas transacoes de um cliente (nonce) ? 

        R: nao esta no enunciado logo nao

    - strong read deve ser um consenso parcial?

        R: ver PBFT
        

    - soft read -> ao enviar mensagem de commit, para alem do bloco, enviar tambem uma assinatura do bloco
        o nó que receber este commit tem de guardar a assinatura (por bloco) para mais tarde enviar ao cliente

Client
    envia mensagens assinadas

Nodes
    Lider e Replicas
        Conta (para guardar fee)
        SoftState
        Blockchain
        Mempool

    Lider
        recebe transfer
            verifica se é valida (assinatura e saldo)
                se sim guarda na mempool e atualiza softstate
                se nao rejeita, broadcast para nodes, responder ao client
        
        when 10 transfers na mempool
            remove da mempool
            cria bloco comeca consenso

        when consenso termina
            responde a todos os clientes que estiverem no bloco

        when leader change
            complexo, pensar bem no que é preciso fazer

        
    Replicas
        recebe transfer
            guardar na mempool (nao atualiza softstate)
            (ISTO É ESTRANHO PORQUE A MEMPOOL DAS REPLICAS TEM TRANSACOES NAO VERIFICADAS MAS NO LIDER SAO VERIFICADAS)

        when mensagem de consenso
            verifica assinatura de bloco (se foi assinado pelo lider)
            verifica assinatura transferencia (se foi assinado pelo cliente)
            verifica transferencia (se é valida)

        when mensagem de commit
            (verifica tudo denovo)
            (de alguma forma) guarda assinatura de quem enviou para depois poder enviar ao cliente

        when consenso termina
            atualiza softstate mempool e blockchain


FUTURO:
DOMINOS TRACKER BUT FOR TRANSACTIONS
SEND TRANSACTION ID TO CLIENT
ALLOW CLIENT TO QUERY TRANSACTION STATUS

# Token Exchange System (TES)

Account - Public Key - Balance

Guarantees - Balance should be non negative - State cant be modified by non authorized - Non-repudiation of all operations

Client's API - create_account - account with associated public key and pre defined positive balance - transfer - send money from source to destination - check_balance - obtain the balance of an account

Server and Clients can be byzantine

Transactions must be grouped in a (fixed-size) block, e.g. 10 transactions

check_balance should be able to run with two consistency modes (client specifies) - strongly consistent read - (acho) nao vai num block, faz consenso diretamente - weakly consistent read - (acho) read from a replica that is not the leader without consensus

we cant send the whole blockchain (we already do that)

all update transactions pay a fee to the block producer (the leader)
-> probably means that the leader must have an account

Steps - implement server side TES state (accounts) - syntax and semantics of operations, implement client side - optimized read-only operations - tests
