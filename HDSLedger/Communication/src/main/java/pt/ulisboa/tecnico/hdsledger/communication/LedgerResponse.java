package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.Map;

import com.google.gson.Gson;

public class LedgerResponse extends Message {
    
    // Consensus instance when value was decided
    private UpdateAccount updateAccount;
    // Signatures of the account update
    private Map<String, String> signatures;

    public LedgerResponse(String senderId, UpdateAccount updateAccount, Map<String, String> signatures) {
        super(senderId, Type.REPLY);
        this.updateAccount = updateAccount;
        this.signatures = signatures;
    }

    public UpdateAccount getUpdateAccount() {
        return updateAccount;
    }

    public Map<String, String> getSignatures() {
        return signatures;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
