
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
