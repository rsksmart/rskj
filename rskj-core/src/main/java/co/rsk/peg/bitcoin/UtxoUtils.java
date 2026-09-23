package co.rsk.peg.bitcoin;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.TransactionInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Codecs and extraction helpers for the parts of a transaction outpoint that the bridge
 * persists and logs: the outpoint values and the output indexes.
 */
public final class UtxoUtils {

    private UtxoUtils() {
    }

    /**
     * Decode a {@code byte[]} of encoded outpoint values.
     *
     * @param encodedOutpointValues the byte array of encoded outpoint values to decode
     * @return {@code List<Coin>} an unmodifiable list of the outpoint's values decoded,
     * preserving the order of the entries. Empty when {@code encodedOutpointValues}
     * is {@code null} or an {@code empty byte[]}.
     * @throws InvalidOutpointValueException when the bytes are not a valid sequence of
     * VarInts, or a value decodes to a negative number.
     */
    public static List<Coin> decodeOutpointValues(byte[] encodedOutpointValues) {
        try {
            return VarIntUtils.decode(encodedOutpointValues).stream()
                .map(Coin::valueOf)
                .toList();
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
     * @throws InvalidOutpointValueException when a value is {@code null} or negative. Zero
     * is valid
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

        try {
            return VarIntUtils.encode(values);
        } catch (VarIntException ex) {
            throw new InvalidOutpointValueException(ex.getMessage(), ex);
        }
    }

    private static void validateOutpointValue(Coin outpointValue) {
        if (outpointValue == null) {
            throw new InvalidOutpointValueException(
                "Invalid outpoint value: null values are not allowed.");
        }
    }

    /**
     * Decode a {@code byte[]} of encoded output indexes.
     *
     * @param encodedOutputIndexes the byte array of encoded output indexes to decode
     * @return {@code List<Long>} an unmodifiable list of the output indexes decoded,
     * preserving the order of the entries. Empty when {@code encodedOutputIndexes}
     * is {@code null} or an {@code empty byte[]}.
     * @throws InvalidOutputIndexException when the bytes are not a valid sequence of
     * VarInts, or a value decodes to a negative number.
     */
    public static List<Long> decodeOutputIndexes(byte[] encodedOutputIndexes) {
        try {
            return VarIntUtils.decode(encodedOutputIndexes);
        } catch (VarIntException ex) {
            throw new InvalidOutputIndexException(ex.getMessage(), ex);
        }
    }

    /**
     * Encode a {@code List<Long>} of output indexes.
     *
     * @param outputIndexes the list of output indexes to encode
     * @return {@code byte[]} the list of output indexes encoded preserving the order of the
     * entries. Or an {@code empty byte[]} when {@code outputIndexes} is {@code null} or
     * {@code empty}.
     * @throws InvalidOutputIndexException when an output index is {@code null} or negative.
     */
    public static byte[] encodeOutputIndexes(List<Long> outputIndexes) {
        try {
            return VarIntUtils.encode(outputIndexes);
        } catch (VarIntException ex) {
            throw new InvalidOutputIndexException(ex.getMessage(), ex);
        }
    }

    public static List<Coin> extractOutpointValues(BtcTransaction generatedTransaction) {
        if (generatedTransaction == null) {
            return Collections.emptyList();
        }

        return generatedTransaction.getInputs().stream().map(TransactionInput::getValue).toList();
    }
}
