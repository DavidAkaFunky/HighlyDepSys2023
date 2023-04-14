package pt.ulisboa.tecnico.hdsledger.communication;

import java.math.BigDecimal;
import java.util.List;

import com.google.gson.Gson;

public class UpdateAccount {

    // Who to reply after consensus
    private String ownerId;
    // Account identifier
    private String hashPubKey;
    // Account balance
    private BigDecimal balance;
    // Most recent consensus instance that updated balance
    private Integer consensusInstance;
    // The collection of nonces that were processed in this round
    private List<Integer> nonces;
    // UpdateAccount is part of a valid (or not) block
    private boolean valid;

    public UpdateAccount(String ownerId, String hashPubKey, BigDecimal balance, Integer consensusInstance, List<Integer> nonces, boolean isValid) {
        this.ownerId = ownerId;
        this.hashPubKey = hashPubKey;
        this.balance = balance;
        this.consensusInstance = consensusInstance;
        this.nonces = nonces;
        this.valid = isValid;
    }

    public UpdateAccount(UpdateAccount updateAccount) {
        this.ownerId = updateAccount.ownerId;
        this.hashPubKey = updateAccount.hashPubKey;
        this.balance = updateAccount.balance;
        this.consensusInstance = updateAccount.consensusInstance;
        this.nonces = updateAccount.nonces;
        this.valid = updateAccount.valid;
    }

    public List<Integer> getNonces() {
        return nonces;
    }

    public BigDecimal getUpdatedBalance() {
        return balance;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getHashPubKey() {
        return hashPubKey;
    }

    public Integer getConsensusInstance() {
        return consensusInstance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UpdateAccount that = (UpdateAccount) o;

        if (ownerId != null ? !ownerId.equals(that.ownerId) : that.ownerId != null) return false;
        if (hashPubKey != null ? !hashPubKey.equals(that.hashPubKey) : that.hashPubKey != null) return false;
        if (balance != null ? !balance.equals(that.balance) : that.balance != null) return false;
        if (consensusInstance != null ? !consensusInstance.equals(that.consensusInstance) : that.consensusInstance != null)
            return false;
        return nonces != null ? nonces.equals(that.nonces) : that.nonces == null;
    }

    @Override
    public int hashCode() {
        return new Gson().toJson(this).hashCode();
    }
}
