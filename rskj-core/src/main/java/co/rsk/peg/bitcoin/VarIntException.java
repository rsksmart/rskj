package co.rsk.peg.bitcoin;

public class VarIntException extends RuntimeException {

    public VarIntException(String message) {
        super(message);
    }

    public VarIntException(String message, Throwable cause) {
        super(message, cause);
    }
}
