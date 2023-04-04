package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.Map;

import com.google.gson.Gson;

public class LedgerResponse extends Message {
    
    // True if the prepared block is valid
    private boolean successful;
    // Consensus instance when value was decided
    private UpdateAccount updateAccount;
    // Signatures of the account update
    private Map<String, Map<String, String>> signatures;

    public LedgerResponse(String senderId, boolean successful) {
        super(senderId, Type.REPLY);
        this.successful = successful;
    }

    public LedgerResponse(String senderId, boolean successful, UpdateAccount updateAccount, Map<String, Map<String, String>> signatures) {
        this(senderId, successful);
        this.updateAccount = updateAccount;
        this.signatures = signatures;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public UpdateAccount getUpdateAccount() {
        return updateAccount;
    }

    public Map<String, Map<String, String>> getSignatures() {
        return signatures;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
