package pt.ulisboa.tecnico.hdsledger.service.models.consensus;

import pt.ulisboa.tecnico.hdsledger.service.models.Block;

public class PrePrepare extends ConsensusMessage {

    private Block block;

    private String leaderSignature;

    public PrePrepare(Block block, String signature)  {
        this.block = block;
        this.leaderSignature = signature;
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

    public void setLeaderSignature(String signature) {
        this.leaderSignature = signature;
    }
}
