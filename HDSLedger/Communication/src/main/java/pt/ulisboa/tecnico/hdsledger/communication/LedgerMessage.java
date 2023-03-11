package pt.ulisboa.tecnico.hdsledger.communication;

public class LedgerMessage {

    public enum LedgerMessageType {
        Append, Read
    }
    protected LedgerMessageType type;
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
