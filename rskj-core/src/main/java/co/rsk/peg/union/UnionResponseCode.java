package co.rsk.peg.union;

public enum UnionResponseCode {
    GENERIC_ERROR(-10),
    UNAUTHORIZED_CALLER(-1),

    // Environment restriction when prevent union bridge address being updated on production
    ENVIRONMENT_DISABLED(-2),

    // Invalid value when increasing union bridge locking cap
    INVALID_VALUE(-2),

    // Response codes when disabling union bridge operations
    ALREADY_PAUSED(-2),
    ALREADY_UNPAUSED(-2),

    SUCCESS(0);

    private final int code;

    UnionResponseCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
