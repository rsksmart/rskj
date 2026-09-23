package co.rsk.peg.bitcoin;

public class InvalidOutputIndexException extends RuntimeException {

    public InvalidOutputIndexException(String message, Throwable cause) {
        super(message, cause);
    }
}
