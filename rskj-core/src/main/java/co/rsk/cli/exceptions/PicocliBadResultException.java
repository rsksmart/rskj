package co.rsk.cli.exceptions;

public class PicocliBadResultException extends RuntimeException {
    private final int errorCode;

    public PicocliBadResultException(int errorCode) {
        super(String.format("Command finished with code: %s", errorCode));

        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
