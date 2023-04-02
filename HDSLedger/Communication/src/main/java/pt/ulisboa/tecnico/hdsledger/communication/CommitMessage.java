package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.List;

import com.google.gson.Gson;

public class CommitMessage {

    // True if the prepared block is valid
    private boolean validBlock;
    // List of signatures for updated accounts
    private List<String> updateAccountSignatures;

    public CommitMessage(boolean validBlock) {
        this.validBlock = validBlock;
    }

    public void setUpdateAccountSignatures(List<String> updateAccountSignatures) {
        this.updateAccountSignatures = updateAccountSignatures;
    }

    public List<String> getUpdateAccountSignatures() {
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
