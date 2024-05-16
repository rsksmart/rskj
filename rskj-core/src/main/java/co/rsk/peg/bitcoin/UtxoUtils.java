package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.VarInt;
import com.google.common.primitives.Bytes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.spongycastle.util.encoders.Hex;

public class UtxoUtils {

    private UtxoUtils() {
    }

    /**
     * Decode a {@code byte[]} of encoded outpoint values.
     *
     * @param encodedOutpointValues
     * @return {@code List<Coin>} the list of outpoint values decoded in the same ordered they were
     * before encoding. Or an {@code Collections.EMPTY_LIST} when {@code encodedOutpointValues} is
     * {@code null} or {@code empty byte[]}.
     */
    public static List<Coin> decodeOutpointValues(byte[] encodedOutpointValues) {
        if (encodedOutpointValues == null || encodedOutpointValues.length == 0) {
            return Collections.emptyList();
        }
        int offset = 0;
        List<Coin> outpointValues = new ArrayList<>();

        while (encodedOutpointValues.length > offset) {
            VarInt valueAsVarInt;
            try {
                valueAsVarInt = new VarInt(encodedOutpointValues, offset);
            } catch (Exception ex) {
                throw new InvalidOutpointValueException(
                    String.format("Invalid value with invalid VarInt format: %s",
                        Hex.toHexString(encodedOutpointValues).toUpperCase()
                    ),
                    ex
                );
            }

            offset += valueAsVarInt.getSizeInBytes();
            Coin outpointValue = Coin.valueOf(valueAsVarInt.value);
            assertValidOutpointValue(outpointValue);

            outpointValues.add(outpointValue);
        }
        return outpointValues;

    }

    /**
     * Encode a {@code List<Coin} of outpoint values.
     *
     * @param outpointValues
     * @return {@code byte[]} the list of outpoint values encoded preserving the order of the
     * entries. Or an {@code empty byte[]} when {@code outpointValues} is {@code null} or
     * {@code empty}.
     */
    public static byte[] encodeOutpointValues(List<Coin> outpointValues) {
        if (outpointValues == null || outpointValues.isEmpty()) {
            return new byte[]{};
        }

        List<byte[]> encodedOutpointValues = new ArrayList<>();
        for (Coin outpointValue : outpointValues) {
            assertValidOutpointValue(outpointValue);
            VarInt varIntOutpointValue = new VarInt(outpointValue.getValue());
            encodedOutpointValues.add(varIntOutpointValue.encode());
        }
        return Bytes.concat(encodedOutpointValues.toArray(new byte[][]{}));
    }

    private static void assertValidOutpointValue(Coin outpointValue) {
        if (outpointValue == null || outpointValue.isNegative()) {
            throw new InvalidOutpointValueException(String.format(
                "Invalid outpoint value: %s. Negative and null values are not allowed.",
                outpointValue));
        }
    }
}
