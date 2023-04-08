package pt.ulisboa.tecnico.hdsledger.utilities;

public enum ErrorMessage {
    ConfigFileNotFound("The configuration file is not available at the path supplied"),
    ConfigFileFormat("The configuration file has wrong syntax"),
    NoSuchNode("Can't send a message to a non existing node"),
    NoSuchClient("Received message from a non existing client"),
    SocketSendingError("Error while sending message"),
    SocketReceivingError("Error while receiving message"),
    CannotOpenSocket("Error while opening socket"),
    SignatureDoesNotMatch("The message signature is not valid"),
    FailedToSignMessage("Error while signing message"),
    CannotParseMessage("Error while parsing received message"),
    InvalidAccount("Invalid account id"),
    FailedToReadPublicKey("Error while reading public key"),
    NoLeader("Error while getting leader"),
    InvalidResponse("Invalid response to client request");

    private final String message;

    ErrorMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
