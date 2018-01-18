package co.rsk.signing;

/**
 * Represents a message that can be signed.
 *
 * @author Ariel Mendelzon
 */
public abstract class Message {
    public abstract byte[] getBytes();
}
