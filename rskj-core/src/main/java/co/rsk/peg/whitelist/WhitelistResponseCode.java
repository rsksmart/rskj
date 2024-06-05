package co.rsk.peg.whitelist;

public enum WhitelistResponseCode {
    GENERIC_ERROR(-10),
    INVALID_ADDRESS_FORMAT(-2),
    ADDRESS_ALREADY_WHITELISTED(-1),
    UNKNOWN_ERROR(0),
    SUCCESS(1);

    private final int code;

    WhitelistResponseCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
