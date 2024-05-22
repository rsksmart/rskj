package co.rsk.peg.bitcoin;

public class InvalidOutpointValueException extends RuntimeException {

    public InvalidOutpointValueException(String message) {
        super(message);
    }

    public InvalidOutpointValueException(String message, Throwable cause) {
        super(message, cause);
    }
}
