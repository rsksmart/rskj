package co.rsk.peg.feeperkb;

public enum FeePerKbResponseCode {
    SUCCESSFUL_VOTE(1),
    UNSUCCESSFUL_VOTE(-1),
    EXCESSIVE_FEE_VOTED(-2),
    NEGATIVE_FEE_VOTED(-1),
    UNAUTHORIZED_CALLER(-10),
    GENERIC_ERROR(-10);

    private final int code;

    FeePerKbResponseCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
