package pt.ulisboa.tecnico.hdsledger.communication;

import java.math.BigDecimal;

public class LedgerRequestTransfer extends Message {

    // Message identifier
    private int nonce;
    // Stored blockchain size
    private int knownBlockchainSize;
    // Destination ID
    private String destId;
    // Amount to transfer
    private BigDecimal amount;
    // Signature of amount with client's private key
    private String clientSignature;

    public LedgerRequestTransfer(Type type, String senderId, int nonce, BigDecimal amount, int knownBlockchainSize) {
        super(senderId, type);
        this.amount = amount;
        this.nonce = nonce;
        this.knownBlockchainSize = knownBlockchainSize;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getClientSignature() {
        return clientSignature;
    }

    public void setClientSignature(String clientSignature) {
        this.clientSignature = clientSignature;
    }

    public int getNonce() {
        return nonce;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }

    public int getKnownBlockchainSize() {
        return knownBlockchainSize;
    }

    public void setKnownBlockchainSize(int knownBlockchainSize) {
        this.knownBlockchainSize = knownBlockchainSize;
    }
}
