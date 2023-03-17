package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.Serializable;

public class SignedMessage implements Serializable {

    // Message content
    private String message;
    // Message signature
    private String signature;

    public SignedMessage(String message, String signature) {
        this.message = message;
        this.signature = signature;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
