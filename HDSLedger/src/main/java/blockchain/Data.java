package blockchain;

import java.io.Serializable;

public class Data implements Serializable {

    private int senderId;
    private int messageId;
    private String name;
    private int data;

    public Data(int senderId, int messageId, String name, int data) {
        this.senderId = senderId;
        this.messageId = messageId;
        this.name = name;
        this.data = data;
    }
    
    public int getSenderId() {
        return senderId;
    }

    public int getMessageId() {
        return messageId;
    }

    public String getName() {
        return name;
    }

    public int getData() {
        return data;
    }

    @Override
    public String toString() {
        return "Message from " + senderId + " ID: " + messageId + " Data [name = " + name + ", data = " + data + "]";
    }

}