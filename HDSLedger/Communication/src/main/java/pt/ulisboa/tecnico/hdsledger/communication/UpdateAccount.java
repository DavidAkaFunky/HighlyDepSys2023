package pt.ulisboa.tecnico.hdsledger.communication;

import java.math.BigDecimal;

import com.google.gson.Gson;

public class UpdateAccount {

    // Account identifier
    private String hashPubKey;
    // Account balance
    private BigDecimal balance;
    // Most recent consensus instance that updated balance
    private Integer consensusInstance;

    public UpdateAccount(String hashPubKey, BigDecimal balance, Integer consensusInstance) {
        this.hashPubKey = hashPubKey;
        this.balance = balance;
        this.consensusInstance = consensusInstance;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
