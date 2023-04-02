package pt.ulisboa.tecnico.hdsledger.service.models;

import java.math.BigDecimal;

public class Account {

    // Account identifier
    private String publicKeyHash;
    // Most recent consensus instance that updated balance
    private int mostRecentConsensusInstance;
    // Account balance
    private BigDecimal balance;
    // Initial balance
    private static final int INITIAL_BALANCE = 100;

    public Account(int consensusInstance, String publicKeyHash) {
        this.mostRecentConsensusInstance = consensusInstance;
        this.publicKeyHash = publicKeyHash;
        this.balance = new BigDecimal(INITIAL_BALANCE);
    }

    public String getPublicKeyHash() {
        return publicKeyHash;
    }

    public void setPublicKeyHash(String publicKeyHash) {
        this.publicKeyHash = publicKeyHash;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void addBalance(int consensusInstance, BigDecimal amount) {
        synchronized (this) {
            setMostRecentConsensusInstance(consensusInstance);
            this.balance = this.balance.add(amount);
        }
    }

    public boolean subtractBalance(int nonce, BigDecimal amount) {
        synchronized (this) {
            setMostRecentConsensusInstance(nonce);
            if (this.balance.compareTo(amount) < 0) {
                return false;
            }
            this.balance = this.balance.subtract(amount);
            return true;
        }
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public int getMostRecentConsensusInstance() {
        return mostRecentConsensusInstance;
    }

    public void setMostRecentConsensusInstance(int consensusInstance) {
        this.mostRecentConsensusInstance = consensusInstance;
    }

}
