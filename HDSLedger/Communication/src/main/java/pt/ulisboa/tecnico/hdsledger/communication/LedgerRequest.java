package pt.ulisboa.tecnico.hdsledger.communication;

public class LedgerRequest extends Message {

    private String arg;
    private int requestId;
    private int blockchainSize;

    public LedgerRequest(Type type, String senderId, int requestId, String arg, int blockchainSize) {
        super(senderId, type);
        this.arg = arg;
        this.requestId = requestId;
        this.blockchainSize = blockchainSize;
    }

    public String getArg() {
        return arg;
    }

    public void setArg(String arg) {
        this.arg = arg;
    }

    public int getRequestId(){
        return requestId;
    }

    public void setRequestId(int requestId){
        this.requestId = requestId;
    }

    public int getBlockchainSize() {
        return blockchainSize;
    }

    public void setBlockchainSize(int blockchainSize) {
        this.blockchainSize = blockchainSize;
    }
}
