package co.rsk.peg.feeperkb;

public enum FeePerKbResponseCode {
    SUCCESSFUL(1),
    UNSUCCESSFUL(-1),
    EXCESSIVE(-2),
    NEGATIVE(-3),
    UNAUTHORIZED(-4),
    GENERIC(-10);

    private final int code;

    FeePerKbResponseCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
