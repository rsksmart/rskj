package co.rsk.signing;

import org.spongycastle.util.encoders.Hex;

/**
 * Represents a message that can be signed.
 *
 * @author Ariel Mendelzon
 */
public abstract class Message {
    public abstract byte[] getBytes();

    @Override
    public String toString() {
        return Hex.toHexString(getBytes());
    }
}
