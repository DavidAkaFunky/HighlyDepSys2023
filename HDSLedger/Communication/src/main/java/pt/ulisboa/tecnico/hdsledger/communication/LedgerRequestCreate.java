package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

public class LedgerRequestCreate {

    // Account Public Key
    private PublicKey accountPubKey;

    public LedgerRequestCreate(PublicKey accountPubKey) {
        this.accountPubKey = accountPubKey;
    }

    public PublicKey getAccountPubKey() {
        return accountPubKey;
    }

    public void setAccountPubKey(PublicKey accountPubKey) {
        this.accountPubKey = accountPubKey;
    }
}
