package pt.ulisboa.tecnico.hdsledger.communication;

import java.math.BigDecimal;
import java.security.PublicKey;

public class LedgerRequestTransfer {

    // Client nonce
    private int nonce;
    // Source Public Key
    private PublicKey sourcePubKey;
    // Destination Public Key
    private PublicKey destinationPubKey;
    // Amount to transfer
    private BigDecimal amount;

    public LedgerRequestTransfer(int nonce, PublicKey sourcePubKey, PublicKey destinationPubKey, BigDecimal amount) {
        this.nonce = nonce;
        this.sourcePubKey = sourcePubKey;
        this.destinationPubKey = destinationPubKey;
        this.amount = amount;
    }

    public int getNonce() {
        return nonce;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }

    public PublicKey getSourcePubKey() {
        return sourcePubKey;
    }

    public void setSourcePubKey(PublicKey sourcePubKey) {
        this.sourcePubKey = sourcePubKey;
    }

    public PublicKey getDestinationPubKey() {
        return destinationPubKey;
    }

    public void setDestinationPubKey(PublicKey destinationPubKey) {
        this.destinationPubKey = destinationPubKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
