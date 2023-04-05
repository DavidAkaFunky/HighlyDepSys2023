package pt.ulisboa.tecnico.hdsledger.communication;

import java.math.BigDecimal;
import java.util.List;

import com.google.gson.Gson;

public class UpdateAccount {

    // Account identifier
    private String hashPubKey;
    // Account balance
    private BigDecimal balance;
    // Most recent consensus instance that updated balance
    private Integer consensusInstance;
    // The collection of nonces that were processed in this round
    private List<Integer> nonces;

    public UpdateAccount(String hashPubKey, BigDecimal balance, Integer consensusInstance, List<Integer> nonces) {
        this.hashPubKey = hashPubKey;
        this.balance = balance;
        this.consensusInstance = consensusInstance;
        this.nonces = nonces;
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

    public String getHashPubKey() {
        return hashPubKey;
    }

    public Integer getConsensusInstance() {
        return consensusInstance;
    }
}
