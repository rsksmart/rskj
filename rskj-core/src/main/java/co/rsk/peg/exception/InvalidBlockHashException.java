package co.rsk.peg.exception;

public class InvalidBlockHashException  extends IllegalStateException {
    public InvalidBlockHashException() {
    }

    public InvalidBlockHashException(String var1) {
        super(var1);
    }

    public InvalidBlockHashException(String var1, Throwable var2) {
        super(var1, var2);
    }

    public InvalidBlockHashException(Throwable var1) {
        super(var1);
    }
}