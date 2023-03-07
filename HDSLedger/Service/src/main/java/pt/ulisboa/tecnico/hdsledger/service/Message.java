package pt.ulisboa.tecnico.hdsledger.service;

import java.io.Serializable;
import java.util.List;

public class Message implements Serializable {

    private int senderId;
    private int messageId;
    private Type type;
    private List<String> args;

    enum Type {
        PRE_PREPARE, PREPARE, COMMIT, ROUND_CHANGE, DECIDE, ACK, DUPLICATE,
    }

    public Message(int senderId, int messageId, Type type) {
        this.senderId = senderId;
        this.messageId = messageId;
        this.type = type;
    }

    public Message(int senderId, int messageId, Type type, List<String> args) {
        this.senderId = senderId;
        this.messageId = messageId;
        this.type = type;
        this.args = args;
    }
    
    public int getSenderId() {
        return senderId;
    }

    public int getMessageId() {
        return messageId;
    }

    public Type getType() {
        return type;
    }

    public List<String> getArgs() {
        return args;
    }

    @Override
    public String toString() {
        return "Message from " + senderId + " ID: " + messageId + " Content [type = " + type + ", args = " + args + "]";
    }

}