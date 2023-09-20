package co.rsk.bridge;

/**
 * Exception to be thrown when attempting to create a Federation with invalid values
 * that could result in the 2wp to stop working as expected
 */
public class FederationCreationException extends RuntimeException {

    public FederationCreationException() {
        super();
    }

    public FederationCreationException(String s) {
        super(s);
    }

    public FederationCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
