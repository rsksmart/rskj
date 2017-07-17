package co.rsk.scoring;

/**
 * InvalidInetAddressBlockException raises when an address block is invalid
 * (local address, bad mask, etc)
 * <p>
 * Created by ajlopez on 14/07/2017.
 */
public class InvalidInetAddressBlockException extends Exception {
    public InvalidInetAddressBlockException(String message, Throwable ex) {
        super(message, ex);
    }
}
