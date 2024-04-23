package co.rsk.peg.feeperkb;

public enum FeePerKbResponseCode {
    SUCCESSFUL(1),
    GENERIC(-10),
    NEGATIVE(-1),
    EXCESSIVE(-2);

    private final int code;

    FeePerKbResponseCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
