package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

public class CommitMessage {

    // True if the prepared block is valid
    private boolean validBlock;
    // Map Signature of update -> UpdatedAccount
    private Map<String, UpdateAccount> accountUpdates = new HashMap<>();

    public CommitMessage(boolean validBlock) {
        this.validBlock = validBlock;
    }

    public CommitMessage(boolean validBlock, Map<String, UpdateAccount> accountUpdates) {
        this.validBlock = validBlock;
        this.accountUpdates = accountUpdates;
    }

    public Map<String, UpdateAccount> getUpdateAccountSignatures() {
        return accountUpdates;
    }

    public boolean isValidBlock() {
        return validBlock;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
