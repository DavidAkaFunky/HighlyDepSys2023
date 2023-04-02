package pt.ulisboa.tecnico.hdsledger.communication;

public class CommitMessage {

    private int consensusInstance;
    private int round;

    // (Replica) Signature of the block
    private String blockSignature;

    public CommitMessage(int consensusInstance, int round, String blockSignature) {
        this.consensusInstance = consensusInstance;
        this.round = round;
        this.blockSignature = blockSignature;
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

    public String getBlockSignature() {
        return blockSignature;
    }

    public void setBlockSignature(String blockSignature) {
        this.blockSignature = blockSignature;
    }
}
