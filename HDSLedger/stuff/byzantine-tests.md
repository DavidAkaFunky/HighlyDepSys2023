# Byzantine Behavior

ALL LINES OF CODE RELATED TO BYZANTINE BEHAVIOR ARE IDENTIFIED
BY A COMMENT WITH "BYZANTINE_TESTS"

- Not sending messages (or long delays)
- Sending messages with wrong signatures
- Pretending to be leader when it is not
- Sending different messages to different nodes
- Sending messages with a value different from the one pre-prepared

### Not sending messages
byzantineSend() {
    // do nohting
}

### Long delays
byzantineSend() {
    Thread.sleep(3000);
}

### Sending Messages with wrong signatures
### The leader signature (without loss of generality)
This is not possible because the node doesnt have access
to the private key of other nodes

### Pretending to be leader when it is not
byzantineSend(String leaderId) {
    ...
    data.setSenderId(leaderId);
    ...
}

### Sending different messages to different nodes
Como saber qual dos args é o valor ?
é sempre o ultimo ?
APENAS EM PRE PREPARE E COMMIT AS OUTRAS MENSAGEM NAO LEVAM INPUTVALUE
byzantineSend() {
    data.setArgs(Random)
}

### Sending messages with a value different from the one pre-prepared
Acaba por ser igual ao anterior provavelemente podemos colapsar
apenas neste
é kinda weird porque o broadcast envia a mesma mensagem assinada para todos
temos de ter um broadcast que receba varias mensagens (ou nao ? unsure)

byzantineSend() {
    
}

byzantineBroadcast() {

}

### Always sending the same value


### Not sending messages
byzantineSend() {
    // do nohting
}

### Long delays
byzantineSend() {
    Thread.sleep(3000);
}

### Sending Messages with wrong signatures
### The leader signature (without loss of generality)
This is not possible because the node doesnt have access
to the private key of other nodes

### Pretending to be leader when it is not
byzantineSend(String leaderId) {
    ...
    data.setSenderId(leaderId);
    ...
}

### Sending different messages to different nodes
Como saber qual dos args é o valor ?
é sempre o ultimo ?
APENAS EM PRE PREPARE E COMMIT AS OUTRAS MENSAGEM NAO LEVAM INPUTVALUE
byzantineSend() {
    data.setArgs(Random)
}

### Sending messages with a value different from the one pre-prepared
Acaba por ser igual ao anterior provavelemente podemos colapsar
apenas neste
é kinda weird porque o broadcast envia a mesma mensagem assinada para todos
temos de ter um broadcast que receba varias mensagens (ou nao ? unsure)

byzantineSend() {

}

byzantineBroadcast() {

}

### Always sending the same value

## Config

    {
        "id": "1",
        "isLeader": true,
        "hostname": "localhost",
        "port": 3001,
        "clientPort": 4001,
        "publicKeyPath": "../PKI/node1.pub",
        "privateKeyPath": "../PKI/node1.priv"
    },

## Tests

You must also include a set of tests for the correctness of the system. For these tests to
be thorough, they need to be more intrusive than simple “black box” tests that submit a
load consisting of strings to be appended to the blockchain: in particular, there must be
a way to change the way that messages are delivered or the behavior of Byzantine nodes
to test more challenging scenarios.
