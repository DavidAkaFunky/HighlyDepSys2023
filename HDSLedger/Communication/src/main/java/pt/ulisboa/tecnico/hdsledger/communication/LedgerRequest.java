package pt.ulisboa.tecnico.hdsledger.communication;

public class LedgerRequest extends Message {

    private String arg;
    private int blockchainSize;

    public LedgerRequest(Type type, String senderId, int messageId, String arg, int blockchainSize) {
        super(senderId, messageId, type);
        this.arg = arg;
        this.blockchainSize = blockchainSize;
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
