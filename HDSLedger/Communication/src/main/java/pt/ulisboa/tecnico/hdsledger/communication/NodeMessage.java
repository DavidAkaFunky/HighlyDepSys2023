package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.List;

public class NodeMessage extends Message {

    private List<String> args;
    private String clientId;
    private String valueSignature;

    public NodeMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public NodeMessage(String senderId, Type type, List<String> args) {
        super(senderId, type);
        this.args = args;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public String getValueSignature() {
        return valueSignature;
    }

    public void setValueSignature(String valueSignature) {
        this.valueSignature = valueSignature;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public String toString() {
        return "NodeMessage from " + getSenderId() + " ID: " + getMessageId() + " Content [type = " + getType() + ", args = " + args + "]";
    }

}