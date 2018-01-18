package co.rsk.signing;

/**
 * Implementors of this should
 * be able to provide a means
 * of uniquely identifying a key.
 *
 * @author Ariel Mendelzon
 */
public interface KeyId {
    boolean isPlainId();

    String getPlainId();
}
