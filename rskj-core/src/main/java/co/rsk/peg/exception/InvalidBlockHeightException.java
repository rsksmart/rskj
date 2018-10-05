package co.rsk.peg.exception;

public class InvalidBlockHeightException extends IllegalStateException {
    public InvalidBlockHeightException() {
    }

    public InvalidBlockHeightException(String var1) {
        super(var1);
    }

    public InvalidBlockHeightException(String var1, Throwable var2) {
        super(var1, var2);
    }

    public InvalidBlockHeightException(Throwable var1) {
        super(var1);
    }
}
