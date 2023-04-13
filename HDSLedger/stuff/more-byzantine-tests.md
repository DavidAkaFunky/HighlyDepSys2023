

# Byzantine Client
Transfer from other accounts to himself

Alterar o transfer para fazer a operacao ao contrario
transfer 12 1  ---> self receber do 12 


# Byzantine Nodes

## Leader
Nao meter no bloco operacoes de um cliente especifico
Expected: timer explodir

Nao fazer bloco (aka nao comecar consenso)
Expected:

Nao propor nada
Expected: timer explodir

Lider tirar mais do que devia na fee
Expected: outros recusam updateaccount e o consenso prossegueu com o valor
correto

Modificar requests que recebeu que mete no bloco
Expected: arrebentar nas assinaturas

Adicionar requests falsos que aumentam o seu balance
Expected: arrebentam na assinatura

## Non-Leader

Dont respond to consensus protocol messages
Expected: continua e funciona

Sending messages with wrong signatures
Expected: alguem reclama

Pretending to be leader when it is not
Expected: arrebenta

Sending different messages to different nodes
Expected

Sending messages with a value different from the one pre-prepared
Expected 

Weak read devolver garbage (update account errado ou assinaturas erradas)
Expected: client falha a verificar
