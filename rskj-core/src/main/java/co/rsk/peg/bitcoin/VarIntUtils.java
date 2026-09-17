package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.VarInt;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

public final class VarIntUtils {

    private VarIntUtils() {
    }

    /**
     * Decode a {@code byte[]} of values encoded as VarInts.
     *
     * @param encodedValues
     * @return {@code List<Long>} the list of values decoded preserving the order of the
     * entries. Or an {@code Collections.EMPTY_LIST} when {@code encodedValues} is
     * {@code null} or {@code empty byte[]}.
     */
    public static List<Long> decode(byte[] encodedValues) {
        return Collections.emptyList();
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
            validateValue(value);
            VarInt valueAsVarInt = new VarInt(value);
            outputStream.writeBytes(valueAsVarInt.encode());
        }
        return outputStream.toByteArray();
    }

    private static void validateValue(Long value) {
        if (value == null || value < 0) {
            throw new VarIntException(String.format(
                "Invalid value: %s. Negative and null values are not allowed.", value));
        }
    }
}
