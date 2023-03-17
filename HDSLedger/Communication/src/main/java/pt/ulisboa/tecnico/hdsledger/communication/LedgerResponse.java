package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.List;

public class LedgerResponse extends Message {
    
    // Consensus instance when value was decided
    private int consensusInstance;
    // Message Identifier
    private int requestId;
    // New blockchain values
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
