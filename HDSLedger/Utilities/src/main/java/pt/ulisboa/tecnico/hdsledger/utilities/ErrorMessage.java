package pt.ulisboa.tecnico.hdsledger.utilities;

public enum ErrorMessage {
    ConfigFileNotFound("The configuration file is not available at the path supplied"),
    ConfigFileFormat("The configuration file has wrong syntax"),
    NoSuchNode("Can't send a message to a non existing node"),
    SocketSendingError("Error while sending message"),
    SocketReceivingError("Error while receiving message"),
    CannotOpenSocket("Error while opening socket"),
    SignatureDoesntMatch("The message signature is not valid"),
    CannotParseMessage("Error while parsing received message");

    private final String message;

    ErrorMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
