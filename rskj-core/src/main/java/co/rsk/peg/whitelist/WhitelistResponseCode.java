package co.rsk.peg.whitelist;

public enum WhitelistResponseCode {
    GENERIC_ERROR(-10),
    UNAUTHORIZED_CALLER(-10),
    INVALID_ADDRESS_FORMAT(-2),
    DISABLE_BLOCK_DELAY_INVALID(-2),
    ADDRESS_ALREADY_WHITELISTED(-1),
    ADDRESS_NOT_EXIST(-1),
    DELAY_ALREADY_SET(-1),
    SUCCESS(1);

    private final int code;

    WhitelistResponseCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
