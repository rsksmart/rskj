package co.rsk.signing;

import java.util.Arrays;

/**
 * Immutable plain byte array message
 *
 * @author Ariel Mendelzon
 */
public class PlainMessage extends Message {
    private final byte[] message;

    public PlainMessage(byte[] message) {
        // Save a copy
        this.message = copy(message);
    }

    @Override
    public byte[] getBytes() {
        return copy(message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }

        return Arrays.equals(this.getBytes(), ((PlainMessage) o).getBytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(message);
    }

    private byte[] copy(byte[] a) {
        return Arrays.copyOf(a, a.length);
    }
}
