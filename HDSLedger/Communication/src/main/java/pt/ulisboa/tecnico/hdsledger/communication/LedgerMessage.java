package pt.ulisboa.tecnico.hdsledger.communication;

public class LedgerMessage {

    public enum LedgerMessageType {
        APPEND, READ
    }

    protected LedgerMessageType type;
    protected int clientSeq;
    protected String arg;

    public LedgerMessageType getType() {
        return type;
    }

    public void setType(LedgerMessageType type) {
        this.type = type;
    }

    public String getArg() {
        return arg;
    }

    public void setArg(String arg) {
        this.arg = arg;
    }
}
