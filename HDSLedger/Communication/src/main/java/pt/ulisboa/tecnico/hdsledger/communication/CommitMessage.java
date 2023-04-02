package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CommitMessage {

    // (Replica) Signature of the block
    private String blockSignature;

    public CommitMessage(String blockSignature) {
        this.blockSignature = blockSignature;
    }

    public void setBlockSignature(String blockSignature) {
        this.blockSignature = blockSignature;
    }

    public String getBlockSignature() {
        return blockSignature;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
