package co.rsk.peg;

public class RegisterBtcTransactionException extends Exception {

    public RegisterBtcTransactionException(String message) {
        super(message);
    }

    public RegisterBtcTransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
