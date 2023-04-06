package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.security.PublicKey;

public class LedgerRequestBalance {

    // Account Public Key
    private String accountPubKey;
    // Consistency mode
    private ConsistencyMode consistencyMode;
    // Last known consensus instance
    private int lastKnownConsensusInstance;

    public enum ConsistencyMode {
        STRONG,
        WEAK
    }

    public LedgerRequestBalance(PublicKey accountPubKey, ConsistencyMode consistencyMode, int lastKnownConsensusInstance) {
        this.accountPubKey = RSAEncryption.encodePublicKey(accountPubKey);
        this.consistencyMode = consistencyMode;
        this.lastKnownConsensusInstance = lastKnownConsensusInstance;
    }

    public PublicKey getAccountPubKey() {
        return RSAEncryption.decodePublicKey(this.accountPubKey);
    }

    public void setAccountPubKey(PublicKey accountPubKey) {
        this.accountPubKey = RSAEncryption.encodePublicKey(accountPubKey);
    }

    public ConsistencyMode getConsistencyMode() {
        return consistencyMode;
    }

    public void setConsistencyMode(ConsistencyMode consistencyMode) {
        this.consistencyMode = consistencyMode;
    }

    public int getLastKnownConsensusInstance() {
        return lastKnownConsensusInstance;
    }

    public void setLastKnownConsensusInstance(int lastKnownConsensusInstance) {
        this.lastKnownConsensusInstance = lastKnownConsensusInstance;
    }
}
