package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.Serializable;
import java.util.List;

public class Message implements Serializable {

    private String senderId;
    private int messageId;
    private Type type;
    private List<String> args;

    public enum Type {
        START, PRE_PREPARE, PREPARE, COMMIT, ROUND_CHANGE, DECIDE, ACK, IGNORE;
    }

    public Message(String senderId, int messageId, Type type) {
        this.senderId = senderId;
        this.messageId = messageId;
        this.type = type;
    }

    public Message(String senderId, int messageId, Type type, List<String> args) {
        this.senderId = senderId;
        this.messageId = messageId;
        this.type = type;
        this.args = args;
    }
    
    public String getSenderId() {
        return senderId;
    }

    public int getMessageId() {
        return messageId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public List<String> getArgs() {
        return args;
    }

    @Override
    public String toString() {
        return "Message from " + senderId + " ID: " + messageId + " Content [type = " + type + ", args = " + args + "]";
    }

}