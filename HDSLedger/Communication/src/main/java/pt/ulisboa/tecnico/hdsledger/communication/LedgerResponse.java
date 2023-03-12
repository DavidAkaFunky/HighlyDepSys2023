package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.List;

public class LedgerResponse {
    
    private int consensusInstance;
    private List<String> values;

    public LedgerResponse(int consensusInstance, List<String> values) {
        this.consensusInstance = consensusInstance;
        this.values = values;
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }
    
}
