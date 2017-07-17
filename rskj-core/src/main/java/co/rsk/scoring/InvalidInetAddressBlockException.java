package co.rsk.scoring;

/**
 * Created by ajlopez on 14/07/2017.
 */
public class InvalidInetAddressBlockException extends Exception {
    public InvalidInetAddressBlockException(String message, Throwable ex) {
        super(message, ex);
    }
}
