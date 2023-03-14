package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.List;

public class LedgerResponse extends Message {
    
    private List<String> values;

    public LedgerResponse(String senderId, List<String> values) {
        super(senderId, Type.REPLY);
        this.values = values;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }
    
}
