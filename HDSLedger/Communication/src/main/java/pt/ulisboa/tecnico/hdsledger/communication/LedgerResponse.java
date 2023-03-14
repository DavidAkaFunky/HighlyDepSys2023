package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.List;

public class LedgerResponse extends Message {
    
    private int consensusInstance;
    private int requestId;
    private List<String> values;

    public LedgerResponse(String senderId, int requestId, int consensusInstance, List<String> values) {
        super(senderId, Type.REPLY);
        this.requestId = requestId;
        this.consensusInstance = consensusInstance;
        this.values = values;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }
    
    public int getRequestId(){
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }
}
