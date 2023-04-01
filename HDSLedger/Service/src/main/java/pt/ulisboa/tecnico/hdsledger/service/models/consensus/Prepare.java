package pt.ulisboa.tecnico.hdsledger.service.models.consensus;

import pt.ulisboa.tecnico.hdsledger.service.models.Block;

public class Prepare extends ConsensusMessage {

    private Block block;

    private String leaderSignature;

    private String replicaSignature;

    public Prepare(Block block, String leaderSignature, String replicaSignature) {
        this.block = block;
        this.leaderSignature = leaderSignature;
        this.replicaSignature = replicaSignature;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }

    public String getLeaderSignature() {
        return leaderSignature;
    }

    public void setLeaderSignature(String leaderSignature) {
        this.leaderSignature = leaderSignature;
    }

    public String getReplicaSignature() {
        return replicaSignature;
    }

    public void setReplicaSignature(String replicaSignature) {
        this.replicaSignature = replicaSignature;
    }
}
