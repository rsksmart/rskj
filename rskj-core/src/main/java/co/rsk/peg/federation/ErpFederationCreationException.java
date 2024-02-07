package co.rsk.peg.federation;

/**
 * Exception to be thrown when attempting to create a Federation with invalid values
 * that could result in the 2wp to stop working as expected
 */
public class ErpFederationCreationException extends RuntimeException {
    private final Reason reason;

    public enum Reason {
        NULL_OR_EMPTY_EMERGENCY_KEYS,
        REDEEM_SCRIPT_CREATION_FAILED
    }

    public ErpFederationCreationException(String s, Reason reason) {
        super(s);
        this.reason = reason;
    }

    public ErpFederationCreationException(String message, Throwable cause, Reason reason) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
