package co.rsk.peg.bitcoin;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

import co.rsk.bitcoinj.core.VarInt;
import co.rsk.core.types.bytes.Bytes;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VarIntUtils {

    private VarIntUtils() {
    }

    /**
     * Decode a {@code byte[]} of values encoded as VarInts.
     *
     * @param encodedValues the byte array of encoded values to decode
     * @return {@code List<Long>} the list of values decoded preserving the order of the
     * entries. Or an {@code Collections.EMPTY_LIST} when {@code encodedValues} is
     * {@code null} or {@code empty byte[]}.
     * @throws VarIntException when a VarInt cannot be read, or a value does not fit in a
     * signed long. A VarInt encodes an unsigned integer.
     */
    public static List<Long> decode(byte[] encodedValues) {
        if (encodedValues == null || encodedValues.length == 0) {
            return Collections.emptyList();
        }

        int offset = 0;
        List<Long> values = new ArrayList<>();

        while (encodedValues.length > offset) {
            VarInt valueAsVarInt;
            try {
                valueAsVarInt = new VarInt(encodedValues, offset);
            } catch (Exception ex) {
                throw new VarIntException(
                    String.format("Invalid value with invalid VarInt format: %s",
                        Bytes.toPrintableString(encodedValues).toUpperCase()
                    ),
                    ex
                );
            }

            offset += valueAsVarInt.getSizeInBytes();
            validateValue(valueAsVarInt.value);
            values.add(valueAsVarInt.value);
        }
        return values;
    }

    /**
     * Encode a {@code List<Long>} of values.
     *
     * @param values the list of values to encode
     * @return {@code byte[]} the list of values encoded as VarInts preserving the order of
     * the entries. Or an {@code empty byte[]} when {@code values} is {@code null} or
     * {@code empty}.
     * @throws VarIntException when a value is {@code null} or negative. A VarInt encodes an
     * unsigned integer, so a negative value has no representation. Zero is valid
     */
    public static byte[] encode(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return EMPTY_BYTE_ARRAY;
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
