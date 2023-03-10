package pt.ulisboa.tecnico.hdsledger.utilities;

public class LedgerException extends RuntimeException {

    private final ErrorMessage errorMessage;

    public LedgerException(ErrorMessage message) {
        errorMessage = message;
    }

    @Override
    public String getMessage() {
        return errorMessage.getMessage();
    }
}
