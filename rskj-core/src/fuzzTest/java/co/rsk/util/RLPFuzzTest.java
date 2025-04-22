package co.rsk.util;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;

import java.math.BigInteger;
import java.util.*;

// JAZZER_FUZZ=1 ./gradlew fuzzTest --tests co.rsk.util.RLPFuzzTest.fuzzDecodeInt
class RLPFuzzTest {
    @Tag("RLPFuzzDecodeInt")
    @FuzzTest
    public void fuzzDecodeInt(FuzzedDataProvider data) {
        byte[] input = generateMixedRLPData(data);
        if (input.length < 1) { return; }
        // int index = data.consumeInt(0, input.length - 1);
        int index = 0;
        try {
            RLP.decodeInt(input, index);
        } catch (RLPException e) {
            
        } catch (RuntimeException e) {
            if ("wrong decode attempt".equals(e.getMessage())) {
                throw new AssertionError("Wrong decode attempt, indicating a potential issue.");
            } else {
                throw e;
            }
        }
    }

    @Tag("RLPFuzzDecodeEncode")
    @FuzzTest
    public void fuzzDecodeEncode(FuzzedDataProvider data) {
        int intInput = data.consumeInt(0, Integer.MAX_VALUE);
        Assertions.assertEquals(intInput, RLP.decodeInt(RLP.encodeInt(intInput), 0));

        byte[] biInput = data.consumeBytes(256);
        BigInteger bi = BigIntegers.fromUnsignedByteArray(biInput);
        Assertions.assertEquals(bi, RLP.decodeBigInteger(RLP.encodeBigInteger(bi), 0));

        int vals = data.consumeInt(5, 20000);
        ArrayList<byte[]> list = new ArrayList<byte[]>(vals);
        for (int i = 0; i < vals; i++) {
            byte[] d = data.consumeBytes(4096);
            list.add(RLP.encodeElement(d));
        }

        byte[] res = RLP.encodeList(list.toArray(new byte[vals][]));
        ArrayList<RLPElement> decoded = RLP.decode2(res);
        RLPList outcome = (RLPList) decoded.get(0);

        for (int i = 0; i < vals; i++) {
            byte[] originalItem = RLP.decode2(list.get(i)).get(0).getRLPData();
            byte[] newItem = outcome.get(i).getRLPData();
            Assertions.assertArrayEquals(originalItem, newItem);
        }
    }

    @Tag("RLPFuzzDecodeBigInteger")
    @FuzzTest
    public void fuzzDecodeBigInteger(FuzzedDataProvider data) {
        byte[] input = generateMixedRLPData(data);
        if (input.length < 1) { return; }
        int index = data.consumeInt(0, input.length - 1);
        try {
            RLP.decodeBigInteger(input, index);
        } catch (RLPException e) {

        }
    }

    @Tag("RLPFuzzGetNextElementIndex")
    @FuzzTest
    public void fuzzGetNextElementIndex(FuzzedDataProvider data) {
        byte[] payload = generateMixedRLPData(data);;
        if (payload.length < 1) { return; }
        int pos = data.consumeInt(0, payload.length - 1);
        try {
            RLP.getNextElementIndex(payload, pos);
        } catch (RLPException e) {

        }
    }

    @Tag("RLPFuzzDecode2")
    @FuzzTest
    public void fuzzDecode2(FuzzedDataProvider data) {
        byte[] msgData = generateMixedRLPData(data);
        if (msgData.length < 1) { return; }
        try {
            RLP.decode2(msgData);
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzFirstElement")
    @FuzzTest
    public void fuzzDecodeFirstElement(FuzzedDataProvider data) {
        byte[] msgData = generateMixedRLPData(data);
        if (msgData.length < 1) { return; }
        // int position = data.consumeInt(0, msgData.length - 1);
        int position = 0;
        try {
            RLP.decodeFirstElement(msgData, position);
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzDecodeList")
    @FuzzTest
    public void fuzzDecodeList(FuzzedDataProvider data) {
        byte[] msgData = data.consumeRemainingAsBytes();
        if (msgData.length < 1) { return; }
        try {
            RLP.decodeList(msgData);
        } catch (RLPException e) {
            
        } catch (IllegalArgumentException e) {
            // Skip expected exceptions
            if (!"The decoded element wasn't a list".equals(e.getMessage()) && !e.getMessage().startsWith("Expected one RLP item but got ")) {
                throw e;
            }
        }
    }

    @Tag("RLPFuzzDecode2OneItem")
    @FuzzTest
    public void fuzzDecode2OneItem(FuzzedDataProvider data) {
        byte[] msgData = data.consumeRemainingAsBytes();
        if (msgData.length < 1) { return; }
        int startPos = data.consumeInt(0, msgData.length - 1);
        try {
            RLP.decode2OneItem(msgData, startPos);
        } catch (RLPException e) {
            
        }
    }

    private byte[] generateMixedRLPData(FuzzedDataProvider data) {
        int choice = data.consumeInt(0, 5);
        switch (choice) {
            case 0:
                return data.consumeRemainingAsBytes();
            case 1:
                String str = data.consumeString(100);
                return RLP.encodeString(str);
            case 2:
                int listSize = data.consumeInt(0, 10);
                byte[][] list = new byte[listSize][];
                for (int i = 0; i < listSize; i++) {
                    list[i] = data.consumeBytes(50);
                }
                return RLP.encodeList(list);
            case 3:
                return data.consumeBytes(10000);
            case 4:
                return generateBoundaryRLPData(data);
            case 5:
                byte[] baseCase = data.pickValue(BASE_CASES);
                return mutateRLPData(baseCase, data);
            default:
                return new byte[0];
        }
    }

    private byte[] generateBoundaryRLPData(FuzzedDataProvider data) {
        int choice = data.consumeInt(0, 3);
        switch (choice) {
            case 0:
                return new byte[] {(byte) 0x80};
            case 1:
                return new byte[] {(byte) 0xc0};
            case 2:
                byte[] maxLengthElement = data.consumeBytes(100000);
                return RLP.encodeElement(maxLengthElement);
            case 3:
                return generateNestedList(data, 100);
            default:
                return new byte[0];
        }
    }

    private byte[] generateNestedList(FuzzedDataProvider data, int depth) {
        if (depth == 0) {
            return new byte[] {(byte) 0x80};
        }
        byte[] innerList = generateNestedList(data, depth - 1);
        return RLP.encodeList(innerList);
    }

    private byte[] mutateRLPData(byte[] baseCase, FuzzedDataProvider data) {
        int mutationType = data.consumeInt(0, 2);
        switch (mutationType) {
            case 0:
                int bitFlipIndex = data.consumeInt(0, baseCase.length - 1);
                baseCase[bitFlipIndex] ^= (1 << data.consumeInt(0, 7));
                return baseCase;
            case 1:
                byte[] withExtraByte = new byte[baseCase.length + 1];
                System.arraycopy(baseCase, 0, withExtraByte, 0, baseCase.length);
                withExtraByte[baseCase.length] = data.consumeByte();
                return withExtraByte;
            case 2:
                if (baseCase.length > 1) {
                    byte[] withoutByte = new byte[baseCase.length - 1];
                    System.arraycopy(baseCase, 0, withoutByte, 0, baseCase.length - 1);
                    return withoutByte;
                }
                return baseCase;
            default:
                return baseCase;
        }
    }

    private static final List<byte[]> BASE_CASES = List.of(
        new byte[] {(byte) 0x80}, // Empty string
        new byte[] {(byte) 0xc0}, // Empty list
        RLP.encodeString("Hello, world!"), // Simple string
        RLP.encodeList(new byte[] {(byte) 0x80}, new byte[] {(byte) 0x81, 0x01}) // Simple list
    );

    // *********
    // ENCODING
    // *********
    @Tag("RLPFuzzEncodeLength")
    @FuzzTest
    void fuzzEncodeLength(FuzzedDataProvider data) {
        int length = data.consumeInt();
        int offset = data.consumeInt();
        try {
            byte[] result = RLP.encodeLength(length, offset);
            // Add assertions or checks to validate the result if necessary
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzEncodeByte")
    @FuzzTest
    void fuzzEncodeByte(FuzzedDataProvider data) {
        byte singleByte = data.consumeByte();
        try {
            byte[] result = RLP.encodeByte(singleByte);
            // Add assertions or checks to validate the result if necessary
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzEncodeShort")
    @FuzzTest
    void fuzzEncodeShort(FuzzedDataProvider data) {
        short singleShort = data.consumeShort();
        try {
            byte[] result = RLP.encodeShort(singleShort);
            // Add assertions or checks to validate the result if necessary
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzEncodeInt")
    @FuzzTest
    void fuzzEncodeInt(FuzzedDataProvider data) {
        int singleInt = data.consumeInt();
        try {
            byte[] result = RLP.encodeInt(singleInt);
            // Add assertions or checks to validate the result if necessary
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzEncodeString")
    @FuzzTest
    void fuzzEncodeString(FuzzedDataProvider data) {
        String srcString = data.consumeRemainingAsString();
        try {
            byte[] result = RLP.encodeString(srcString);
            // Add assertions or checks to validate the result if necessary
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzEncodeElement")
    @FuzzTest
    void fuzzEncodeElement(FuzzedDataProvider data) {
        byte[] srcData = data.consumeRemainingAsBytes();
        try {
            byte[] result = RLP.encodeElement(srcData);
            // Add assertions or checks to validate the result if necessary
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzEncodeListHeader")
    @FuzzTest
    void fuzzEncodeListHeader(FuzzedDataProvider data) {
        int size = data.consumeInt();
        try {
            byte[] result = RLP.encodeListHeader(size);
            // Add assertions or checks to validate the result if necessary
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzEncodeSet")
    @FuzzTest
    void fuzzEncodeSet(FuzzedDataProvider data) {
        Set<ByteArrayWrapper> byteArrayWrapperSet = new HashSet<>();
        int setSize = data.consumeInt(0, 10);  // Limit the set size for performance
        for (int i = 0; i < setSize; i++) {
            byte[] byteArray = data.consumeBytes(256);
            byteArrayWrapperSet.add(new ByteArrayWrapper(byteArray));
        }
        try {
            byte[] result = RLP.encodeSet(byteArrayWrapperSet);
            // Add assertions or checks to validate the result if necessary
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzEncodeList")
    @FuzzTest
    void fuzzEncodeList(FuzzedDataProvider data) {
        int listSize = data.consumeInt(0, 10);  // Limit the list size for performance
        byte[][] elements = new byte[listSize][];
        for (int i = 0; i < listSize; i++) {
            elements[i] = data.consumeBytes(256);
        }
        try {
            byte[] result = RLP.encodeList(elements);
            // Add assertions or checks to validate the result if necessary
        } catch (RLPException e) {
            
        }
    }

    // TODO:
    // public static byte[] encode(Object input);
    // public static byte[] encodeBigInteger(BigInteger srcBigInteger);
    // public static byte[] encodeRskAddress(RskAddress addr);
    // public static byte[] encodeCoin(@Nullable Coin coin);
    // public static byte[] encodeCoinNonNullZero(@CheckForNull Coin coin);
    // public static byte[] encodeSignedCoinNonNullZero(@CheckForNull Coin coin);
    // public static byte[] encodeCoinNullZero(Coin coin);
    // public static byte[] encodeBlockDifficulty(BlockDifficulty difficulty);
    @Tag("RLPFuzzEncodeDecodeByte")
    @FuzzTest
    void fuzzEncodeDecodeByte(FuzzedDataProvider data) {
        byte singleByte = data.consumeByte();
        try {
            byte[] encoded = RLP.encodeByte(singleByte);
            int decodedByte = RLP.decodeInt(encoded, 0);  // Assume decodeInt can be used to decode byte
            if (singleByte != (byte) decodedByte) {
                throw new AssertionError("Decoded byte does not match original byte");
            }
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzEncodeDecodeShort")
    @FuzzTest
    void fuzzEncodeDecodeShort(FuzzedDataProvider data) {
        short singleShort = data.consumeShort();
        try {
            byte[] encoded = RLP.encodeShort(singleShort);
            int decodedShort = RLP.decodeInt(encoded, 0);  // Assume decodeInt can be used to decode short
            if (singleShort != (short) decodedShort) {
                throw new AssertionError("Decoded short does not match original short");
            }
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzEncodeDecodeInt")
    @FuzzTest
    void fuzzEncodeDecodeInt(FuzzedDataProvider data) {
        int singleInt = data.consumeInt();
        try {
            byte[] encoded = RLP.encodeInt(singleInt);
            int decodedInt = RLP.decodeInt(encoded, 0);
            if (singleInt != decodedInt) {
                throw new AssertionError("Decoded int does not match original int");
            }
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzEncodeDecodeString")
    @FuzzTest
    void fuzzEncodeDecodeString(FuzzedDataProvider data) {
        String srcString = data.consumeRemainingAsString();
        if (srcString.length() == 0) {
            return;
        }
        try {
            byte[] encoded = RLP.encodeString(srcString);
            ArrayList<RLPElement> decodedElements = RLP.decode2(encoded);  // Assume decode2 can decode string
            if (decodedElements.isEmpty() || !srcString.equals(new String(decodedElements.get(0).getRLPData()))) {
                throw new AssertionError("Decoded string does not match original string");
            }
        } catch (RLPException e) {
            
        }
    }

    @Tag("RLPFuzzEncodeDecodeElement")
    @FuzzTest
    void fuzzEncodeDecodeElement(FuzzedDataProvider data) {
        byte[] srcData = data.consumeRemainingAsBytes();
        if (srcData.length == 0) {
            return;
        }
        try {
            byte[] encoded = RLP.encodeElement(srcData);
            ArrayList<RLPElement> decodedElements = RLP.decode2(encoded);  // Assume decode2 can decode element
            if (decodedElements.isEmpty() || decodedElements.size() != 1 || !Arrays.equals(srcData, decodedElements.get(0).getRLPData())) {
                throw new AssertionError("Decoded element does not match original element");
            }
        } catch (RLPException e) {
            
        }
    }
}
