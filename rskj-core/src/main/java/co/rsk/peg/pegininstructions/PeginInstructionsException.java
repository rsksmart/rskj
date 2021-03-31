package co.rsk.peg.pegininstructions;

public class PeginInstructionsException extends Exception {
    public PeginInstructionsException(String message) {
        super(message);
    }

    public PeginInstructionsException(String message, Throwable cause) {
        super(message, cause);
    }
}
