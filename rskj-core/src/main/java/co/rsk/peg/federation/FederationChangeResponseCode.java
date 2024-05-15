package co.rsk.peg.federation;

public enum FederationChangeResponseCode {
    SUCCESSFUL(1),
    PENDING_FEDERATION_ALREADY_EXISTS(-1),
    EXISTING_FEDERATION_AWAITING_ACTIVATION(-2),
    EXISTING_RETIRING_FEDERATION(-3),
    PENDING_FEDERATION_NON_EXISTENT(-1),
    FEDERATOR_ALREADY_PRESENT(-2),
    INSUFFICIENT_MEMBERS(-2),
    PENDING_FEDERATION_MISMATCHED_HASH(-3),
    NON_EXISTING_FUNCTION_CALLED(-10),
    UNAUTHORIZED_CALLER(-10),
    GENERIC_ERROR(-10);

    private final int code;

    FederationChangeResponseCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
