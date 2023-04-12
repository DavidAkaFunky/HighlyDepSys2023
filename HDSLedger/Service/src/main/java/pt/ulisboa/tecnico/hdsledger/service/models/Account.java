package pt.ulisboa.tecnico.hdsledger.service.models;

import java.math.BigDecimal;

import pt.ulisboa.tecnico.hdsledger.communication.UpdateAccount;

public class Account {

    // Is active
    private boolean active = false;
    // Owner ID
    private String ownerId;
    // Account identifier
    private String publicKeyHash;
    // Most recent consensus instance that updated balance
    private UpdateAccount mostRecentUpdateAccount;
    // Update account signature
    private String updateAccountSignature;
    // Account balance
    private BigDecimal balance = new BigDecimal(INITIAL_BALANCE);
    // Initial balance
    private static final int INITIAL_BALANCE = 100;

    public Account(String ownerId, String publicKeyHash) {
        this.ownerId = ownerId;
        this.publicKeyHash = publicKeyHash;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isActive() {
        return active;
    }

    public String getOwnerId() {
        return ownerId;
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

    public void addBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public boolean subtractBalance(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            return false;
        }
        this.balance = this.balance.subtract(amount);
        return true;
    }

    public void updateAccount(UpdateAccount updateAccount, String updateAccountSignature) {
        this.mostRecentUpdateAccount = updateAccount;
        this.updateAccountSignature = updateAccountSignature;
        this.balance = updateAccount.getUpdatedBalance();
    }

    public UpdateAccount getMostRecentAccountUpdate() {
        return mostRecentUpdateAccount;
    }

    @Override
    public int hashCode() {
        return publicKeyHash.hashCode();
    }
}
