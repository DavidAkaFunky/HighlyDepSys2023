package pt.ulisboa.tecnico.hdsledger.ledger;

import java.math.BigDecimal;
import java.security.PublicKey;

public class Account {

    // Account identifier
    private PublicKey publicKey;
    // Account balance
    private BigDecimal balance;
    // Initial balance
    private static final int INITIAL_BALANCE = 100;

    public Account(PublicKey publicKey) {
        this.publicKey = publicKey;
        this.balance = new BigDecimal(INITIAL_BALANCE);
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public boolean addBalance(BigDecimal amount) {
        syncronized (this.balance) {
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                return false;
            }
            this.balance = this.balance.add(amount);
            return true;
        }
    }

    public boolean subtractBalance(BigDecimal amount) {
        syncronized (this.balance) {
            if (amount.compareTo(BigDecimal.ZERO) < 0 || this.balance.compareTo(amount) < 0) {
                return false;
            }
            this.balance = this.balance.subtract(amount);
            return true;
        }
    }

}
