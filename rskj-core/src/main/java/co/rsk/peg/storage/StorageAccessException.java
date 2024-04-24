package co.rsk.peg.storage;

public class StorageAccessException extends RuntimeException {
    public StorageAccessException() {
        super();
    }

    public StorageAccessException(String s) {
        super(s);
    }

    public StorageAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
