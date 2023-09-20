package co.rsk.bridge;

/**
 * Exception to be thrown when calling a method that has been deprecated after a certain HF activation
 * and the given HF is already active
 */
public class DeprecatedMethodCallException extends RuntimeException {

    public DeprecatedMethodCallException() {
        super();
    }

    public DeprecatedMethodCallException(String s) {
        super(s);
    }

    public DeprecatedMethodCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
