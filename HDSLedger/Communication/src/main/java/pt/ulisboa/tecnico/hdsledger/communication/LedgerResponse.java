package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

public class LedgerResponse extends Message {
    
    // True if the prepared block is valid
    private boolean successful;
    // The result of the transactions
    private UpdateAccount updateAccount;
    // Signatures of the account update
    private Map<String, String> signatures;
    // 
    private List<Integer> repliesTo = new ArrayList<>();

    public LedgerResponse(String senderId, boolean successful) {
        super(senderId, Type.REPLY);
        this.successful = successful;
    }

    public LedgerResponse(String senderId, boolean successful, UpdateAccount updateAccount, Map<String, String> signatures) {
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

    public Map<String, String> getSignatures() {
        return signatures;
    }

    public List<Integer> getRepliesTo() {
      return repliesTo;
    }

    public void setRepliesTo(List<Integer> repliesTo) {
      this.repliesTo = repliesTo;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
