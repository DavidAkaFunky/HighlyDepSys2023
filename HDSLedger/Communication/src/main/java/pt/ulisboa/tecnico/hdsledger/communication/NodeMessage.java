package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.Serializable;
import java.util.List;

public class NodeMessage extends Message {

    private List<String> args;

    public NodeMessage(String senderId, int messageId, Type type) {
        super(senderId, messageId, type);
    }

    public NodeMessage(String senderId, int messageId, Type type, List<String> args) {
        super(senderId, messageId, type);
        this.args = args;
    }

    public List<String> getArgs() {
        return args;
    }

    @Override
    public String toString() {
        return "NodeMessage from " + getSenderId() + " ID: " + getMessageId() + " Content [type = " + getType() + ", args = " + args + "]";
    }

}