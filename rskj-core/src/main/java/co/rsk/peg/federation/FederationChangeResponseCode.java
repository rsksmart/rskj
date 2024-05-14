package co.rsk.peg.federation;

public enum FederationChangeResponseCode {
    SUCCESSFUL(1),
    EXISTING_PENDING(-1),
    AWAITING_ACTIVATION(-2),
    EXISTING_RETIRING(-3),
    NON_EXISTING_PENDING(-1),
    FEDERATOR_ALREADY_PRESENT(-2),
    UNCOMPLETE_PENDING(-2),
    MISMATCHED_HASHES(-3),
    NON_EXISTING_FUNCTION(-10),
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
