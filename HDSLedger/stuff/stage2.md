PASSADO:
NAO VERIFICAVAMOS A INTEGRIDADE DA MENSAGEM
DO CLIENT, NO SENTIDO EM QUE OS NODES PODIAM
TROCAR O CLIENT_ID E OS RESTANTES IAM FAZER
VERIFICACOES COM ESSE ID ERRADO (ups)

    NO ENTANTO
    AGORA JA PODEMOS

    PORQUE A SOURCE DA TRANSACAO É QUEM DEVE
    ASSINAR, SE ALGUEM TENTAR TROCAR
    (O LIDER P.EX) ESTARA A PERDER DINHEIRO

    NIVEIS DE INTEGRIDADE:
    - BLOCO ASSINADO PELO LIDER
    - TRANSACOES ASSINADAS PELA SOURCE

    EM CADA PASSADO DO CONSENSO É VERIFICADO
    SE O BLOCO AINDA É VALIDO E SE CADA
    TRANSACAO É VALIDA

FUTURO:
DOMINOS TRACKER BUT FOR TRANSACTIONS
SEND TRANSACTION ID TO CLIENT
ALLOW CLIENT TO QUERY TRANSACTION STATUS

Cliente

    create account_id
    Library
        get pubkey do client_id
        send request <pubkey>

    transfer source_id dest_id amount
    Library
        get pubkey do source_id
        get pubkey do dest_id
        send request <pubkey, pubkey, amount>

    read account_id
    Library
        get pubkey do account_id
        send request <pubkey>

Cada servidor tem uma conta

Messagens

    SignedTransferMessage
        messageSignature - hash(message)
        TransferMessage
            id (counter)
            source
            destination
            amount
            sourceSignature - hash(source, destination, amount, couter)

Servidor

    blockchain
    committed_soft_state (accounts)
    uncommitted_soft_state (updated accounts)
    unverified_mempool (messages)
    verified_mempool (messages)

    Quando um nó é réplica:2
    1) committed_soft_state == uncommitted_soft_state
    2) verified_mempool == {} (não verifica nenhuma transação)
    3) Guardar timer (como implementar?) para anular transações
    após N instâncias de consenso na unverified_mempool
    4) Quando um bloco é recebido, as transações incluídas
    são removidas da unverified_mempool e vão diretamente para o
    committed e o uncommitted soft state

    mint() {
        new Thread() ->
        if(verified_mempool.size() >= block_size){
            remove verified_mempool.size() messages from verified_mempool
            create block
            block.add(messages)
            block.add(hash(blockchain.last()))
            block.add(consensusInstace + 1)
            sign(hash(block))
            block.add(signature)

            consenso(block)
        }
    }


    listen() {
        new Thread() ->
        switch (message) {
            case TRANSACTION -> {
                unverified_transactions.append(message)

                if leader => verifyTransaction(message) {
                    verify client signature
                    verify transaction signature
                    verify if source.balance >= amount + fee
                }

                bool isBlockComplete = false;

                atomico {
                    if verified (atomico) {
                        unverified_transactions.remove(message)
                        uncommitted_soft_state.update(message)
                        verified_transaction.append(message)
                    }

                    if verified_transactions.size() >= 10 {
                        notifyMiner()
                    }
                }
            }
            case CREATE ACCOUNT -> {
                // Será preciso? 🤔
            }

            case READ ACCOUNT BALANCE -> {

            }
        }
    }

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
