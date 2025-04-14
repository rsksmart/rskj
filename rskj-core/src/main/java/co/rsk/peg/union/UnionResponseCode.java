package co.rsk.peg.union;

public enum UnionResponseCode {
    SUCCESS(0),
    UNAUTHORIZED_CALLER(-1),
    // Response codes when the value specified is invalid:
    // 1. The requested amount of RBTC, combined with previously requested amounts, exceeds the current locking cap value.
    // 2. The returned amount exceeds the total amount of RBTC previously transferred.
    // 3. The new cap value is less than the current cap or excessive.
    INVALID_VALUE(-2),
    // Response codes when enabling already enabled or disabling already disabled union bridge operations
    ALREADY_PAUSED(-3),
    ALREADY_UNPAUSED(-4),
    // Environment restriction for preventing union bridge address being updated on production
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
