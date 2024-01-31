package co.rsk.peg.bitcoin;

public class RedeemScriptCreationException extends RuntimeException {
    private final Reason reason;

    public enum Reason {
        INVALID_INTERNAL_REDEEM_SCRIPTS,
        INVALID_CSV_VALUE
    }

    public RedeemScriptCreationException(String s, Reason reason) {
        super(s);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
