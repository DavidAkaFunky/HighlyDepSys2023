package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class PrePrepareMessage {
    
    // Block object serialized with Gson
    private String block;
    // Leader signature of the block
    private String leaderSignature;

    public PrePrepareMessage(String block, String leaderSignature) {
        this.block = block;
        this.leaderSignature = leaderSignature;
    }

    public String getBlock() {
        return block;
    }

    public void setBlock(String block) {
        this.block = block;
    }

    public String getLeaderSignature() {
        return leaderSignature;
    }

    public void setLeaderSignature(String leaderSignature) {
        this.leaderSignature = leaderSignature;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}   
