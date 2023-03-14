# Byzantine Behavior

- Not sending messages (or long delays)
- Sending messages with wrong signatures
- Sending different messages to different nodes
- Sending messages with a value different from the one pre-prepared
- Pretending to be leader when it is not

## Config

    {
        "id": "1",
        "isLeader": true,
        "hostname": "localhost",
        "port": 3000,
        "clientPort": 4000,
        "publicKeyPath": "../PKI/node1.pub",
        "privateKeyPath": "../PKI/node1.priv"
    },

## Tests

You must also include a set of tests for the correctness of the system. For these tests to
be thorough, they need to be more intrusive than simple “black box” tests that submit a
load consisting of strings to be appended to the blockchain: in particular, there must be
a way to change the way that messages are delivered or the behavior of Byzantine nodes
to test more challenging scenarios.
