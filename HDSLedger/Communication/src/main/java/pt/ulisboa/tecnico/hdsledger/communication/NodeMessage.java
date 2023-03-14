package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.List;

public class NodeMessage extends Message {

    private List<String> args;

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

    @Override
    public String toString() {
        return "NodeMessage from " + getSenderId() + " ID: " + getMessageId() + " Content [type = " + getType() + ", args = " + args + "]";
    }

}