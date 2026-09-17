package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.VarInt;

import java.io.ByteArrayOutputStream;
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
        if (values == null || values.isEmpty()) {
            return new byte[]{};
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (Long value : values) {
            VarInt valueAsVarInt = new VarInt(value);
            outputStream.writeBytes(valueAsVarInt.encode());
        }
        return outputStream.toByteArray();
    }
}
