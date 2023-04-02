package pt.ulisboa.tecnico.hdsledger.service.models;

import java.math.BigDecimal;
import java.security.PublicKey;

public class Account {

    // Account identifier
    private String publicKeyHash;
    // Account balance
    private BigDecimal balance;
    // Initial balance
    private static final int INITIAL_BALANCE = 100;

    public Account(String publicKeyHash) {
        this.publicKeyHash = publicKeyHash;
        this.balance = new BigDecimal(INITIAL_BALANCE);
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void addBalance(BigDecimal amount) {
        synchronized (this.balance) {
            this.balance = this.balance.add(amount);
        }
    }

    public boolean subtractBalance(BigDecimal amount) {
        synchronized (this.balance) {
            if (this.balance.compareTo(amount) < 0) {
                return false;
            }
            this.balance = this.balance.subtract(amount);
            return true;
        }
    }

}
