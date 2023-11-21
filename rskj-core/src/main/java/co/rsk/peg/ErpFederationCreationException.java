package co.rsk.peg;

/**
 * Exception to be thrown when attempting to create a Federation with invalid values
 * that could result in the 2wp to stop working as expected
 */
public class ErpFederationCreationException extends RuntimeException {
    private final Reason reason;

    public enum Reason {
        NULL_OR_EMPTY_EMERGENCY_KEYS,
        INVALID_INTERNAL_REDEEM_SCRIPTS,
        INVALID_CSV_VALUE,
        HARDCODED_LEGACY_ERP_TESTNET_REDEEM_SCRIPT
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
