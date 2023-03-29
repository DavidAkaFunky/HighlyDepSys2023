package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

public class LedgerRequestBalance {

    // Account Public Key
    private PublicKey accountPubKey;
    // Consistency mode
    private String consistencyMode;

    public LedgerRequestBalance(PublicKey accountPubKey, String consistencyMode) {
        this.accountPubKey = accountPubKey;
        this.consistencyMode = consistencyMode;
    }

    public PublicKey getAccountPubKey() {
        return accountPubKey;
    }

    public void setAccountPubKey(PublicKey accountPubKey) {
        this.accountPubKey = accountPubKey;
    }

    public String getConsistencyMode() {
        return consistencyMode;
    }

    public void setConsistencyMode(String consistencyMode) {
        this.consistencyMode = consistencyMode;
    }
}
