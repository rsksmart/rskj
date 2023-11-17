package co.rsk.peg;

/**
 * Exception to be thrown when attempting to create a Federation with invalid values
 * that could result in the 2wp to stop working as expected
 */
public class ErpRedeemScriptBuilderCreationException extends RuntimeException {
    private final Reason reason;

    public enum Reason {
        NULL_OR_EMPTY_EMERGENCY_KEYS,
        INVALID_CSV_VALUE,
        HARDCODED_LEGACY_ERP_TESTNET_REDEEM_SCRIPT
    }

    public ErpRedeemScriptBuilderCreationException(String s, Reason reason) {
        super(s);
        this.reason = reason;
    }

    public ErpRedeemScriptBuilderCreationException(String message, Throwable cause, Reason reason) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
