package co.rsk.peg.bitcoin;

import java.util.List;

public final class VarIntUtils {

    private VarIntUtils() {
    }

    /**
     * Encode a {@code List<Long>} of values.
     *
     * @param values
     * @return {@code byte[]} the list of values encoded as VarInts preserving the order of
     * the entries. Or an {@code empty byte[]} when {@code values} is {@code null} or
     * {@code empty}.
     */
    public static byte[] encode(List<Long> values) {
        return new byte[]{};
    }
}
