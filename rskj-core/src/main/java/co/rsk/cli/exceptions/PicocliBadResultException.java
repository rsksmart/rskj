package co.rsk.cli.exceptions;

public class PicocliBadResultException extends RuntimeException {
    public PicocliBadResultException() {
        super("Picocli got a bad result.");
    }
}
