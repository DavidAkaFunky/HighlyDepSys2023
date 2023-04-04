package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.Map;

import com.google.gson.Gson;

public class CommitMessage {

    // True if the prepared block is valid
    private boolean validBlock;
    // Map public key hash -> signature for updated account
    private Map<String, String> updateAccountSignatures;

    public CommitMessage(boolean validBlock) {
        this.validBlock = validBlock;
    }

    public void setUpdateAccountSignatures(Map<String, String> updateAccountSignatures) {
        this.updateAccountSignatures = updateAccountSignatures;
    }

    public Map<String, String> getUpdateAccountSignatures() {
        return updateAccountSignatures;
    }

    public boolean isValidBlock() {
        return validBlock;
    }

    public void setValidBlock(boolean validBlock) {
        this.validBlock = validBlock;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
