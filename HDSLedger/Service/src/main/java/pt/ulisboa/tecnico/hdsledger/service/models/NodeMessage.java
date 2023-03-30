package pt.ulisboa.tecnico.hdsledger.service.models;

import pt.ulisboa.tecnico.hdsledger.communication.Message;

public class NodeMessage extends Message {

    // Consensus instance
    private int consensusInstance;
    // Round
    private int round;
    // Block
    private Block block;

    public NodeMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public NodeMessage(String senderId, Type type, int consensusInstance, int round, Block block) {
        super(senderId, type);
        this.consensusInstance = consensusInstance;
        this.round = round;
        this.block = block;
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }
}