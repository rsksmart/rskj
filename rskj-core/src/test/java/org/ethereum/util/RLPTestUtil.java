package org.ethereum.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.bouncycastle.util.BigIntegers.asUnsignedByteArray;

public class RLPTestUtil {

    public static byte[] encode(Object input) {
        Value val = new Value(input);
        if (val.isList()) {
            List<byte[]> l = new ArrayList<>();
            for (Object o:val.asList()) l.add(encode(o));
            return RLP.encodeList(l.toArray(new byte[][]{}));
        } else {
            return RLP.encodeElement(toBytes(input));
        }
    }

    /*
     *  Utility function to convert Objects into byte arrays
     */
    private static byte[] toBytes(Object input) {
        if (input == null) return null;
        if (input instanceof byte[]) {
            return (byte[]) input;
        } else if (input instanceof String) {
            String inputString = (String) input;
            return inputString.getBytes(StandardCharsets.UTF_8);
        } else if (input instanceof Long) {
            Long inputLong = (Long) input;
            return (inputLong == 0) ? ByteUtil.EMPTY_BYTE_ARRAY : asUnsignedByteArray(BigInteger.valueOf(inputLong));
        } else if (input instanceof Integer) {
            Integer inputInt = (Integer) input;
            return (inputInt == 0) ? ByteUtil.EMPTY_BYTE_ARRAY : asUnsignedByteArray(BigInteger.valueOf(inputInt));
        } else if (input instanceof BigInteger) {
            BigInteger inputBigInt = (BigInteger) input;
            return (inputBigInt.equals(BigInteger.ZERO)) ? ByteUtil.EMPTY_BYTE_ARRAY : asUnsignedByteArray(inputBigInt);
        } else if (input instanceof Value) {
            Value val = (Value) input;
            return toBytes(val.asObj());
        }
        throw new RuntimeException("Unsupported type: Only accepting String, Integer and BigInteger for now");
    }

    private static final int OFFSET_SHORT_ITEM = 0x80;
    private static final int OFFSET_LONG_ITEM = 0xb7;

    public static int decodeInt(byte[] data, int index) {
        int b0 = data[index] & 0xFF;

        if (b0 < OFFSET_SHORT_ITEM) return data[index];

        if (b0 < OFFSET_LONG_ITEM) {
            int value = 0;
            byte length = (byte) (data[index] - OFFSET_SHORT_ITEM);
            byte pow = (byte) (length - 1);
            for (int i = 1; i <= length; ++i) {
                value += (data[index + i] & 0xFF) << (8 * pow);
                pow--;
            }
            return value;
        }

        throw new RuntimeException("wrong decode attempt");
    }
}
