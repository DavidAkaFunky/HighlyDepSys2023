package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.List;

public class NodeMessage extends Message {

    // Arguments for consensus messages
    // The size varies depending on the message type
    private List<String> args;
    // Client identifier
    private String clientId;
    // Client value signature
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
        return "NodeMessage from " + getSenderId() + " ID: " + getMessageId() + " Content [type = " + getType()
                + ", args = " + args + "]";
    }

}