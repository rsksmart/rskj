package co.rsk.peg;

public class ScriptCreationException extends RuntimeException {
    private final Reason reason;

    public enum Reason {
        ABOVE_MAX_SCRIPT_ELEMENT_SIZE
    }

    public ScriptCreationException(String s, Reason reason) {
        super(s);
        this.reason = reason;
    }

    public ScriptCreationException(String message, Throwable cause, Reason reason) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
