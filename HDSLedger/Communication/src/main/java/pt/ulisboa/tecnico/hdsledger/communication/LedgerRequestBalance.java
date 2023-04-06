package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.security.PublicKey;

public class LedgerRequestBalance {

    // Account Public Key
    private String accountPubKey;
    // Consistency mode
    private String consistencyMode;

    public LedgerRequestBalance(PublicKey accountPubKey, String consistencyMode) {
        this.accountPubKey = RSAEncryption.encodePublicKey(accountPubKey);
        this.consistencyMode = consistencyMode;
    }

    public PublicKey getAccountPubKey() {
        return RSAEncryption.decodePublicKey(this.accountPubKey);
    }

    public void setAccountPubKey(PublicKey accountPubKey) {
        this.accountPubKey = RSAEncryption.encodePublicKey(accountPubKey);
    }

    public String getConsistencyMode() {
        return consistencyMode;
    }

    public void setConsistencyMode(String consistencyMode) {
        this.consistencyMode = consistencyMode;
    }
}
