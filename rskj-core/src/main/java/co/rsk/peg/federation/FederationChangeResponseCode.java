package co.rsk.peg.federation;

public enum FederationChangeResponseCode {
    SUCCESSFUL(1),
    EXISTING_PENDING_FEDERATION(-1),
    AWAITING_FEDERATION_ACTIVATION(-2),
    EXISTING_RETIRING_FEDERATION(-3),
    NON_EXISTING_PENDING_FEDERATION(-1),
    FEDERATOR_ALREADY_PRESENT(-2),
    UNSUFFICIENT_MEMBERS(-2),
    MISMATCHED_HASH(-3),
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
