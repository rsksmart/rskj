package co.rsk.scoring;

/**
 * InvalidInetAddressException raises when a network address is invalid
 * (local address, etc)
 * <p>
 * Created by ajlopez 15/07/2017.
 */
public class InvalidInetAddressException extends Exception {
    public InvalidInetAddressException(String message, Throwable ex) {
        super(message, ex);
    }
}
