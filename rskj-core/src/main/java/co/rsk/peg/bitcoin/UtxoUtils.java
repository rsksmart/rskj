package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.TransactionInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

public final class UtxoUtils {

    private UtxoUtils() {
    }

    /**
     * Decode a {@code byte[]} of encoded outpoint values.
     *
     * @param encodedOutpointValues the byte array of encoded outpoint values to decode
     * @return {@code List<Coin>} the list of outpoint values decoded preserving
     * the order of the entries. Or an {@code Collections.EMPTY_LIST} when {@code encodedOutpointValues} is
     * {@code null} or {@code empty byte[]}.
     */
    public static List<Coin> decodeOutpointValues(byte[] encodedOutpointValues) {
        try {
            return VarIntUtils.decode(encodedOutpointValues).stream()
                .map(Coin::valueOf)
                .collect(Collectors.toList());
        } catch (VarIntException ex) {
            throw new InvalidOutpointValueException(ex.getMessage(), ex);
        }
    }

    /**
     * Encode a {@code List<Coin>} of outpoint values.
     *
     * @param outpointValues the list of outpoint values to encode
     * @return {@code byte[]} the list of outpoint values encoded preserving the order of the
     * entries. Or an {@code empty byte[]} when {@code outpointValues} is {@code null} or
     * {@code empty}.
     */
    public static byte[] encodeOutpointValues(List<Coin> outpointValues) {
        if (outpointValues == null) {
            return EMPTY_BYTE_ARRAY;
        }

        List<Long> values = new ArrayList<>(outpointValues.size());
        for (Coin outpointValue : outpointValues) {
            validateOutpointValue(outpointValue);
            values.add(outpointValue.getValue());
        }

        return VarIntUtils.encode(values);
    }

    private static void validateOutpointValue(Coin outpointValue) {
        if (outpointValue == null || outpointValue.isNegative()) {
            throw new InvalidOutpointValueException(String.format(
                "Invalid outpoint value: %s. Negative and null values are not allowed.",
                outpointValue));
        }
    }

    public static List<Coin> extractOutpointValues(BtcTransaction generatedTransaction) {
        if (generatedTransaction == null) {
            return Collections.emptyList();
        }

        return generatedTransaction.getInputs().stream().map(TransactionInput::getValue).collect(
            Collectors.toList());
    }
}
