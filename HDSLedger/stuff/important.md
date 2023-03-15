
# Final Report

## Leader change

That said, it is important to reason about the protocol features that 
you leave in place so that Byzantine leaders can be implemented in the future 
(and describe this reasoning in the final report).

### How should the client know who is the leader?

Idea 1: Ask the nodes who is the leader (if they're good, they will correctly
tell it -> it's an eventually synchronous) and use if for a time slot
Assumption: client needs to know how many nodes there are to wait for a valid quorum

Idea 2: Receive consensus values from all nodes, receive info regarding the leader
piggybacked into the decision message, then, once a quorum was achieved, read from
that leader

## Byzantine behaviour

Byzantine nodes won't verify signatures to allow them to cooperate with each other,
otherwise they'd stop each other's attempts to modify the algorithm

### DROP

Sends ACK to incoming message but doesn't broadcast anything
Meaning that the other nodes will not be stuck waiting for a reply

### PASSIVE

Does nothing wrong, just doesn't verify signatures

### BAD CONSENSUS

A non-leader node attempts to start consensus, to test if other nodes
only accept messages from the actual leader

### FAKE LEADER

Because other nodes fail to verify the signature, they will not
reply with ACK meaning that this node will be stuck sending messages
forever

### FAKE VALUE

Since the byzantine node cant form a quorum of messages with this value,
the other nodes will never prepare/commit this fake value

### BAD BROADCAST

Broadcast different messages to different nodes but same as FAKE VALUE test
this will not affect the consensus

## Client signatures


## What if the leader isn't necessarily good?

Idea: Send the client's value with a signature (just like a message) and have
each peer check if it's correct (i.e. was not tampered anywhere during the algorithm)

Consequence: A byzantine process may allow a wrong value to procede