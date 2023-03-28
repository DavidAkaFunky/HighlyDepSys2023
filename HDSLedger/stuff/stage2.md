


Cliente
- Criar conta:
Envia PublicKey



























# Token Exchange System (TES)


Account
    - Public Key
    - Balance

Guarantees
    - Balance should be non negative
    - State cant be modified by non authorized
    - Non-repudiation of all operations

Client's API
    - create_account
        - account with associated public key and pre defined positive balance
    - transfer
        - send money from source to destination
    - check_balance
        - obtain the balance of an account

Server and Clients can be byzantine

Transactions must be grouped in a (fixed-size) block, e.g. 10 transactions

check_balance should be able to run with two consistency modes (client specifies)
    - strongly consistent read
        - (acho) nao vai num block, faz consenso diretamente
    - weakly consistent read
        - (acho) read from a replica that is not the leader without consensus

we cant send the whole blockchain (we already do that)

all update transactions pay a fee to the block producer (the leader)
-> probably means that the leader must have an account

Steps
    - implement server side TES state (accounts)
    - syntax and semantics of operations, implement client side
    - optimized read-only operations
    - tests

