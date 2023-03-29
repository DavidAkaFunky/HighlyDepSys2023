package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

public class LedgerRequestCreate {

    // Client nonce
    private int nonce;
    // Account Public Key
    private PublicKey accountPubKey;

    public LedgerRequestCreate(int nonce, PublicKey accountPubKey) {
        this.nonce = nonce;
        this.accountPubKey = accountPubKey;
    }

    public int getNonce() {
        return nonce;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }

    public PublicKey getAccountPubKey() {
        return accountPubKey;
    }

    public void setAccountPubKey(PublicKey accountPubKey) {
        this.accountPubKey = accountPubKey;
    }
}
