package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.math.BigDecimal;
import java.security.PublicKey;

public class LedgerRequestTransfer {

    // Client nonce
    private int nonce;
    // Source Public Key
    private String sourcePubKey;
    // Destination Public Key
    private String destinationPubKey;
    // Amount to transfer
    private BigDecimal amount;

    public LedgerRequestTransfer(int nonce, PublicKey sourcePubKey, PublicKey destinationPubKey, BigDecimal amount) {
        this.nonce = nonce;
        this.sourcePubKey = RSAEncryption.encodePublicKey(sourcePubKey);
        this.destinationPubKey = RSAEncryption.encodePublicKey(destinationPubKey);
        this.amount = amount;
    }

    public int getNonce() {
        return nonce;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }

    public PublicKey getSourcePubKey() {
        return RSAEncryption.decodePublicKey(this.sourcePubKey);
    }

    public void setSourcePubKey(PublicKey sourcePubKey) {
        this.sourcePubKey = RSAEncryption.encodePublicKey(sourcePubKey);
    }

    public PublicKey getDestinationPubKey() {
        return RSAEncryption.decodePublicKey(this.destinationPubKey);
    }

    public void setDestinationPubKey(PublicKey destinationPubKey) {
        this.destinationPubKey = RSAEncryption.encodePublicKey(destinationPubKey);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
