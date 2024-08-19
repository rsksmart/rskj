package co.rsk.peg.bitcoin;

public class FlyoverRedeemScriptCreationException extends RuntimeException {
    private final Reason reason;

    public enum Reason {
        INVALID_FLYOVER_DERIVATION_HASH
    }

    public FlyoverRedeemScriptCreationException(String s, Reason reason) {
        super(s);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }

}
