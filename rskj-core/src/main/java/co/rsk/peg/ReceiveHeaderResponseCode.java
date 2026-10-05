package co.rsk.peg;

public enum ReceiveHeaderResponseCode {
    SUCCESSFUL(0),
    CALLED_TOO_SOON(-1),
    BLOCK_TOO_OLD(-2),
    CANNOT_FIND_PREVIOUS_BLOCK(-3),
    BLOCK_PREVIOUSLY_SAVED(-4),
    HEADER_SIZE_MISMATCH(-20),
    UNEXPECTED_EXCEPTION(-99);

    private final int code;

    ReceiveHeaderResponseCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
