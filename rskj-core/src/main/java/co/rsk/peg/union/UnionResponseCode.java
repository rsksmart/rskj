package co.rsk.peg.union;

public enum UnionResponseCode {
    SUCCESS(0),
    UNAUTHORIZED_CALLER(-1),
    // Invalid value when The amount requested, combined with previously requested amounts, exceeds the current locking cap value.
    // or invalid value when increasing union bridge locking cap
    INVALID_VALUE(-2),
    // Response codes when enabling or disabling union bridge operations
    ALREADY_PAUSED(-3),
    ALREADY_UNPAUSED(-4),
    // Environment restriction when prevent union bridge address being updated on production
    ENVIRONMENT_DISABLED(-5),
    GENERIC_ERROR(-10)
    ;

    private final int code;

    UnionResponseCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
