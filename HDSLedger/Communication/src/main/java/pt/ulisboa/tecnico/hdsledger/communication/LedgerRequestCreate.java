package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.security.PublicKey;

public class LedgerRequestCreate {

    // Client nonce
    private int nonce;
    // Account Public Key
    private String accountPubKey;

    public LedgerRequestCreate(int nonce, PublicKey accountPubKey) {
        this.nonce = nonce;
        this.accountPubKey = RSAEncryption.encodePublicKey(accountPubKey);
    }

    public int getNonce() {
        return nonce;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }

    public PublicKey getAccountPubKey() {
        return RSAEncryption.decodePublicKey(this.accountPubKey);
    }

    public void setAccountPubKey(PublicKey accountPubKey) {
        this.accountPubKey = RSAEncryption.encodePublicKey(accountPubKey);
    }
}
