package pt.ulisboa.tecnico.hdsledger.communication;

public class LedgerRequest {

    public enum LedgerRequestType {
        APPEND, READ
    }

    protected LedgerRequestType type;
    protected int clientSeq;
    protected String arg;
    protected int blockchainSize;

    public LedgerRequest(LedgerRequestType type, int clientSeq, String arg, int blockchainSize) {
        this.type = type;
        this.clientSeq = clientSeq;
        this.arg = arg;
        this.blockchainSize = blockchainSize;
    }

    public LedgerRequestType getType() {
        return type;
    }

    public void setType(LedgerRequestType type) {
        this.type = type;
    }

    public int getClientSeq() {
        return clientSeq;
    }

    public void setClientSeq(int clientSeq) {
        this.clientSeq = clientSeq;
    }

    public String getArg() {
        return arg;
    }

    public void setArg(String arg) {
        this.arg = arg;
    }

    public int getBlockchainSize() {
        return blockchainSize;
    }

    public void setBlockchainSize(int blockchainSize) {
        this.blockchainSize = blockchainSize;
    }
}
