package co.rsk.util;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.util.RLPTestUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.bouncycastle.util.BigIntegers;

import java.math.BigInteger;
import java.util.ArrayList;

/**
 * Created by ajlopez on 16/08/2017.
 */
class RLPTest {
    @Test
    void encodeEmptyByteArray() {
        byte[] bytes = new byte[0];

        byte[] result = RLP.encodeElement(bytes);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
        Assertions.assertEquals((byte)0x80, result[0]);
    }

    @Test
    void encodeNullByteArray() {
        byte[] result = RLP.encodeElement((byte[]) null);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
        Assertions.assertEquals((byte)0x80, result[0]);
    }

    @Test
    void encodeDecodeSingleBytes() {
        for (int k = 0; k < 128; k++) {
            byte[] bytes = new byte[1];
            bytes[0] = (byte) k;

            byte[] encoded = RLP.encodeElement(bytes);

            Assertions.assertNotNull(encoded);
            Assertions.assertEquals(1, encoded.length);

            RLPElement element = RLP.decodeFirstElement(encoded, 0);

            Assertions.assertNotNull(element);

            byte[] decoded = element.getRLPData();

            Assertions.assertNotNull(decoded);
            Assertions.assertArrayEquals(bytes, decoded);
        }
    }

    @Test
    void encodeDecodeSingleBytesWithHighValue() {
        for (int k = 128; k < 256; k++) {
            byte[] bytes = new byte[1];
            bytes[0] = (byte)k;

            byte[] encoded = RLP.encodeElement(bytes);

            Assertions.assertNotNull(encoded);
            Assertions.assertEquals(2, encoded.length);
            Assertions.assertEquals((byte)129, encoded[0]);

            RLPElement element = RLP.decodeFirstElement(encoded, 0);

            Assertions.assertNotNull(element);

            byte[] decoded = element.getRLPData();

            Assertions.assertNotNull(decoded);
            Assertions.assertArrayEquals(bytes, decoded);
        }
    }

    @Test
    void encodeDecodeSingleBytesWithHighValueUsingEncode() {
        for (int k = 128; k < 256; k++) {
            byte[] bytes = new byte[1];
            bytes[0] = (byte)k;

            byte[] encoded = RLPTestUtil.encode(bytes);

            Assertions.assertNotNull(encoded);
            Assertions.assertEquals(2, encoded.length);
            Assertions.assertEquals((byte)129, encoded[0]);

            RLPElement element = RLP.decodeFirstElement(encoded, 0);

            Assertions.assertNotNull(element);

            byte[] decoded = element.getRLPData();

            Assertions.assertNotNull(decoded);
            Assertions.assertArrayEquals(bytes, decoded);
        }
    }

    @Test
    void encodeDecodeShortByteArrays() {
        for (int k = 2; k < 56; k++) {
            byte[] bytes = new byte[k];

            byte[] encoded = RLP.encodeElement(bytes);

            Assertions.assertNotNull(encoded);
            Assertions.assertEquals(1 + k, encoded.length);
            Assertions.assertEquals((byte)(128 + k), encoded[0]);

            RLPElement element = RLP.decodeFirstElement(encoded, 0);

            Assertions.assertNotNull(element);

            byte[] decoded = element.getRLPData();

            Assertions.assertNotNull(decoded);
            Assertions.assertArrayEquals(bytes, decoded);
        }
    }

    @Test
    void encodeDecodeShortByteArraysUsingEncode() {
        for (int k = 2; k < 56; k++) {
            byte[] bytes = new byte[k];

            byte[] encoded = RLPTestUtil.encode(bytes);

            Assertions.assertNotNull(encoded);
            Assertions.assertEquals(1 + k, encoded.length);
            Assertions.assertEquals((byte)(128 + k), encoded[0]);

            RLPElement element = RLP.decodeFirstElement(encoded, 0);

            Assertions.assertNotNull(element);

            byte[] decoded = element.getRLPData();

            Assertions.assertNotNull(decoded);
            Assertions.assertArrayEquals(bytes, decoded);
        }
    }

    @Test
    void encodeDecodeLongByteArrayWithOneByteLength() {
        byte[] bytes = new byte[56];

        byte[] encoded = RLP.encodeElement(bytes);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(2 + 56, encoded.length);
        Assertions.assertEquals((byte)(183 + 1), encoded[0]);
        Assertions.assertEquals((byte)56, encoded[1]);

        RLPElement element = RLP.decodeFirstElement(encoded, 0);

        Assertions.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assertions.assertNotNull(decoded);
        Assertions.assertArrayEquals(bytes, decoded);
    }

    @Test
    void encodeDecodeLongByteArrayWithOneByteLengthUsingEncode() {
        byte[] bytes = new byte[56];

        byte[] encoded = RLPTestUtil.encode(bytes);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(2 + 56, encoded.length);
        Assertions.assertEquals((byte)(183 + 1), encoded[0]);
        Assertions.assertEquals((byte)56, encoded[1]);

        RLPElement element = RLP.decodeFirstElement(encoded, 0);

        Assertions.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assertions.assertNotNull(decoded);
        Assertions.assertArrayEquals(bytes, decoded);
    }

    @Test
    void encodeDecodeLongByteArrayWithTwoBytesLength() {
        byte[] bytes = new byte[256];

        byte[] encoded = RLP.encodeElement(bytes);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(3 + 256, encoded.length);
        Assertions.assertEquals((byte)(183 + 2), encoded[0]);
        Assertions.assertEquals((byte)1, encoded[1]);
        Assertions.assertEquals((byte)0, encoded[2]);

        RLPElement element = RLP.decodeFirstElement(encoded, 0);

        Assertions.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assertions.assertNotNull(decoded);
        Assertions.assertArrayEquals(bytes, decoded);
    }

    @Test
    void encodeDecodeLongByteArrayWithTwoBytesLengthUsingEncode() {
        byte[] bytes = new byte[256];

        byte[] encoded = RLPTestUtil.encode(bytes);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(3 + 256, encoded.length);
        Assertions.assertEquals((byte)(183 + 2), encoded[0]);
        Assertions.assertEquals((byte)1, encoded[1]);
        Assertions.assertEquals((byte)0, encoded[2]);

        RLPElement element = RLP.decodeFirstElement(encoded, 0);

        Assertions.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assertions.assertNotNull(decoded);
        Assertions.assertArrayEquals(bytes, decoded);
    }

    @Test
    void encodeDecodeLongByteArrayWithTwoBytesLengthBorderCase() {
        byte[] bytes = new byte[256 * 256 - 1];

        byte[] encoded = RLP.encodeElement(bytes);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(3 + 256 * 256 - 1, encoded.length);
        Assertions.assertEquals((byte)(183 + 2), encoded[0]);
        Assertions.assertEquals((byte)0xff, encoded[1]);
        Assertions.assertEquals((byte)0xff, encoded[2]);

        RLPElement element = RLP.decodeFirstElement(encoded, 0);

        Assertions.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assertions.assertNotNull(decoded);
        Assertions.assertArrayEquals(bytes, decoded);
    }

    @Test
    void encodeDecodeLongByteArrayWithTwoBytesLengthBorderCaseUsingEncode() {
        byte[] bytes = new byte[256 * 256 - 1];

        byte[] encoded = RLPTestUtil.encode(bytes);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(3 + 256 * 256 - 1, encoded.length);
        Assertions.assertEquals((byte)(183 + 2), encoded[0]);
        Assertions.assertEquals((byte)0xff, encoded[1]);
        Assertions.assertEquals((byte)0xff, encoded[2]);

        RLPElement element = RLP.decodeFirstElement(encoded, 0);

        Assertions.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assertions.assertNotNull(decoded);
        Assertions.assertArrayEquals(bytes, decoded);
    }

    @Test
    void encodeDecodeLongByteArrayWithThreeBytesLength() {
        byte[] bytes = new byte[256 * 256];

        byte[] encoded = RLP.encodeElement(bytes);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(4 + 256 * 256, encoded.length);
        Assertions.assertEquals((byte)(183 + 3), encoded[0]);
        Assertions.assertEquals((byte)0x01, encoded[1]);
        Assertions.assertEquals((byte)0x00, encoded[2]);
        Assertions.assertEquals((byte)0x00, encoded[3]);

        RLPElement element = RLP.decodeFirstElement(encoded, 0);

        Assertions.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assertions.assertNotNull(decoded);
        Assertions.assertArrayEquals(bytes, decoded);
    }

    @Test
    void encodeDecodeLongByteArrayWithThreeBytesLengthUsingEncode() {
        byte[] bytes = new byte[256 * 256];

        byte[] encoded = RLPTestUtil.encode(bytes);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(4 + 256 * 256, encoded.length);
        Assertions.assertEquals((byte)(183 + 3), encoded[0]);
        Assertions.assertEquals((byte)0x01, encoded[1]);
        Assertions.assertEquals((byte)0x00, encoded[2]);
        Assertions.assertEquals((byte)0x00, encoded[3]);

        RLPElement element = RLP.decodeFirstElement(encoded, 0);

        Assertions.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assertions.assertNotNull(decoded);
        Assertions.assertArrayEquals(bytes, decoded);
    }

    @Test
    void encodeDecodeLongByteArrayWithThreeBytesLengthBorderCase() {
        byte[] bytes = new byte[256 * 256 * 256 - 1];

        byte[] encoded = RLP.encodeElement(bytes);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(4 + 256 * 256 * 256 - 1, encoded.length);
        Assertions.assertEquals((byte)(183 + 3), encoded[0]);
        Assertions.assertEquals((byte)0xff, encoded[1]);
        Assertions.assertEquals((byte)0xff, encoded[2]);
        Assertions.assertEquals((byte)0xff, encoded[3]);

        RLPElement element = RLP.decodeFirstElement(encoded, 0);

        Assertions.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assertions.assertNotNull(decoded);
        Assertions.assertArrayEquals(bytes, decoded);
    }

    @Test
    void encodeDecodeLongByteArrayWithThreeBytesLengthBorderCaseUsingEncode() {
        byte[] bytes = new byte[256 * 256 * 256 - 1];

        byte[] encoded = RLPTestUtil.encode(bytes);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(4 + 256 * 256 * 256 - 1, encoded.length);
        Assertions.assertEquals((byte)(183 + 3), encoded[0]);
        Assertions.assertEquals((byte)0xff, encoded[1]);
        Assertions.assertEquals((byte)0xff, encoded[2]);
        Assertions.assertEquals((byte)0xff, encoded[3]);

        RLPElement element = RLP.decodeFirstElement(encoded, 0);

        Assertions.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assertions.assertNotNull(decoded);
        Assertions.assertArrayEquals(bytes, decoded);
    }

    @Test
    void encodeDecodeLongByteArrayWithFourBytesLength() {
        byte[] bytes = new byte[256 * 256 * 256];

        byte[] encoded = RLP.encodeElement(bytes);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(5 + 256 * 256 * 256, encoded.length);
        Assertions.assertEquals((byte)(183 + 4), encoded[0]);
        Assertions.assertEquals((byte)0x01, encoded[1]);
        Assertions.assertEquals((byte)0x00, encoded[2]);
        Assertions.assertEquals((byte)0x00, encoded[3]);
        Assertions.assertEquals((byte)0x00, encoded[4]);

        RLPElement element = RLP.decodeFirstElement(encoded, 0);

        Assertions.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assertions.assertNotNull(decoded);
        Assertions.assertArrayEquals(bytes, decoded);
    }

    @Test
    void encodeDecodeLongByteArrayWithFourBytesLengthUsingEncode() {
        byte[] bytes = new byte[256 * 256 * 256];

        byte[] encoded = RLPTestUtil.encode(bytes);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(5 + 256 * 256 * 256, encoded.length);
        Assertions.assertEquals((byte)(183 + 4), encoded[0]);
        Assertions.assertEquals((byte)0x01, encoded[1]);
        Assertions.assertEquals((byte)0x00, encoded[2]);
        Assertions.assertEquals((byte)0x00, encoded[3]);
        Assertions.assertEquals((byte)0x00, encoded[4]);

        RLPElement element = RLP.decodeFirstElement(encoded, 0);

        Assertions.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assertions.assertNotNull(decoded);
        Assertions.assertArrayEquals(bytes, decoded);
    }

    @Test
    void encodeDecodeBigIntegers() {
        for (int k = 0; k <= 1024; k++) {
            BigInteger value = BigInteger.valueOf(k);
            byte[] encoded = RLP.encodeBigInteger(value);
            BigInteger result = RLP.decodeBigInteger(encoded, 0);
            Assertions.assertNotNull(result);
            Assertions.assertEquals(value, result);
        }
    }

    @Test
    void encodeDecodeEmptyList() {
        byte[] encoded = RLP.encodeList();

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(1, encoded.length);
        Assertions.assertEquals((byte)192, encoded[0]);

        ArrayList<RLPElement> list = RLP.decodeListElements(encoded);

        Assertions.assertNotNull(list);
        Assertions.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assertions.assertNotNull(list2);
        Assertions.assertEquals(0, list2.size());
    }

    @Test
    void encodeDecodeShortListWithShortBytes() {
        byte[] value1 = new byte[] { 0x01 };
        byte[] value2 = new byte[] { 0x02 };
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(3, encoded.length);
        Assertions.assertEquals((byte)(192 + 2), encoded[0]);

        ArrayList<RLPElement> list = RLP.decodeListElements(encoded);

        Assertions.assertNotNull(list);
        Assertions.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assertions.assertNotNull(list2);
        Assertions.assertEquals(2, list2.size());
        Assertions.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assertions.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    void encodeDecodeShortListWithTwoByteArrays() {
        byte[] value1 = new byte[] { 0x01, 0x02 };
        byte[] value2 = new byte[] { 0x03, 0x04 };
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(1 + 3 + 3, encoded.length);
        Assertions.assertEquals((byte)(192 + 3 + 3), encoded[0]);

        ArrayList<RLPElement> list = RLP.decodeListElements(encoded);

        Assertions.assertNotNull(list);
        Assertions.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assertions.assertNotNull(list2);
        Assertions.assertEquals(2, list2.size());
        Assertions.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assertions.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    void encodeDecodeShortListWithTwoByteArraysWithElementsLength55() {
        byte[] value1 = new byte[25];
        byte[] value2 = new byte[28];
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(1 + 1 + 25 + 1 + 28, encoded.length);
        Assertions.assertEquals((byte)(192 + 55), encoded[0]);

        ArrayList<RLPElement> list = RLP.decodeListElements(encoded);

        Assertions.assertNotNull(list);
        Assertions.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assertions.assertNotNull(list2);
        Assertions.assertEquals(2, list2.size());
        Assertions.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assertions.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    void encodeDecodeShortListWithTwoByteArraysWithElementsLength56() {
        byte[] value1 = new byte[26];
        byte[] value2 = new byte[28];
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(1 + 1 + 1 + 26 + 1 + 28, encoded.length);
        Assertions.assertEquals((byte)(247 + 1), encoded[0]);
        Assertions.assertEquals((byte)(56), encoded[1]);

        ArrayList<RLPElement> list = RLP.decodeListElements(encoded);

        Assertions.assertNotNull(list);
        Assertions.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assertions.assertNotNull(list2);
        Assertions.assertEquals(2, list2.size());
        Assertions.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assertions.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    void encodeDecodeShortListWithTwoByteArraysWithOneByteLength() {
        byte[] value1 = new byte[125];
        byte[] value2 = new byte[126];
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(1 + 1 + 2 + 125 + 2 + 126, encoded.length);
        Assertions.assertEquals((byte)(247 + 1), encoded[0]);
        Assertions.assertEquals((byte)(255), encoded[1]);

        ArrayList<RLPElement> list = RLP.decodeListElements(encoded);

        Assertions.assertNotNull(list);
        Assertions.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assertions.assertNotNull(list2);
        Assertions.assertEquals(2, list2.size());
        Assertions.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assertions.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    void encodeDecodeShortListWithTwoByteArraysWithTwoBytesLength() {
        byte[] value1 = new byte[126];
        byte[] value2 = new byte[126];
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(1 + 2 + 2 + 126 + 2 + 126, encoded.length);
        Assertions.assertEquals((byte)(247 + 2), encoded[0]);
        Assertions.assertEquals((byte)(1), encoded[1]);
        Assertions.assertEquals((byte)(0), encoded[2]);

        ArrayList<RLPElement> list = RLP.decodeListElements(encoded);

        Assertions.assertNotNull(list);
        Assertions.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assertions.assertNotNull(list2);
        Assertions.assertEquals(2, list2.size());
        Assertions.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assertions.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    void encodeDecodeShortListWithTwoByteArraysWithTwoBytesLengthBorderCase() {
        byte[] value1 = new byte[128 * 256 - 3 - 1];
        byte[] value2 = new byte[128 * 256 - 3];
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(1 + 2 + 3 + (128 * 256 - 3 - 1) + 3 + (128 * 256 - 3), encoded.length);
        Assertions.assertEquals((byte)(247 + 2), encoded[0]);
        Assertions.assertEquals((byte)(255), encoded[1]);
        Assertions.assertEquals((byte)(255), encoded[2]);

        ArrayList<RLPElement> list = RLP.decodeListElements(encoded);

        Assertions.assertNotNull(list);
        Assertions.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assertions.assertNotNull(list2);
        Assertions.assertEquals(2, list2.size());
        Assertions.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assertions.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    void encodeDecodeShortListWithTwoByteArraysWithThreeBytesLength() {
        byte[] value1 = new byte[128 * 256 - 3];
        byte[] value2 = new byte[128 * 256 - 3];
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assertions.assertNotNull(encoded);
        Assertions.assertEquals(1 + 3 + 3 + (128 * 256 - 3) + 3 + (128 * 256 - 3), encoded.length);
        Assertions.assertEquals((byte)(247 + 3), encoded[0]);
        Assertions.assertEquals((byte)(1), encoded[1]);
        Assertions.assertEquals((byte)(0), encoded[2]);
        Assertions.assertEquals((byte)(0), encoded[3]);

        ArrayList<RLPElement> list = RLP.decodeListElements(encoded);

        Assertions.assertNotNull(list);
        Assertions.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assertions.assertNotNull(list2);
        Assertions.assertEquals(2, list2.size());
        Assertions.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assertions.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    void invalidLengthWithZeroByteLength() {
        byte[] encoded = new byte[] { (byte)0x81 };

        try {
            RLP.decodeFirstElement(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithZeroByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)0x81 };

        try {
            RLP.decodeListElements(encoded);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithOneByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 1), 0x01 };

        try {
            RLP.decodeFirstElement(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithOneByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 1), 0x01 };

        try {
            RLP.decodeListElements(encoded);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithOneByteLengthBorderCase() {
        byte[] encoded = new byte[256];
        encoded[0] = (byte)(183 + 1);
        encoded[1] = (byte)0xff;

        try {
            RLP.decodeFirstElement(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithOneByteLengthBorderCaseUsingDecode2() {
        byte[] encoded = new byte[256];
        encoded[0] = (byte)(183 + 1);
        encoded[1] = (byte)0xff;

        try {
            RLP.decodeListElements(encoded);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithTwoByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 2), 0x01, 0x00 };

        try {
            RLP.decodeFirstElement(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithTwoByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 2), 0x01, 0x00 };

        try {
            RLP.decodeListElements(encoded);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithTwoByteLengthBorderCase() {
        byte[] encoded = new byte[1 + 2 + 256 * 256 - 2];
        encoded[0] = (byte)(183 + 2);
        encoded[1] = (byte)0xff;
        encoded[2] = (byte)0xff;

        try {
            RLP.decodeFirstElement(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithTwoByteLengthBorderCaseUsingDecode2() {
        byte[] encoded = new byte[1 + 2 + 256 * 256 - 2];
        encoded[0] = (byte)(183 + 2);
        encoded[1] = (byte)0xff;
        encoded[2] = (byte)0xff;

        try {
            RLP.decodeListElements(encoded);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithThreeByteLengthBorderCase() {
        byte[] encoded = new byte[1 + 3 + 256 * 256 * 256 - 2];
        encoded[0] = (byte)(183 + 3);
        encoded[1] = (byte)255;
        encoded[2] = (byte)255;
        encoded[3] = (byte)255;

        try {
            RLP.decodeFirstElement(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithThreeByteLengthBorderCaseUsingDecode2() {
        byte[] encoded = new byte[1 + 3 + 256 * 256 * 256 - 2];
        encoded[0] = (byte)(183 + 3);
        encoded[1] = (byte)255;
        encoded[2] = (byte)255;
        encoded[3] = (byte)255;

        try {
            RLP.decodeListElements(encoded);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithFourByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 4), 0x01, 0x00, 0x00, 0x00 };

        try {
            RLP.decodeFirstElement(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidLengthWithFourByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 4), 0x01, 0x00, 0x00, 0x00 };

        try {
            RLP.decodeListElements(encoded);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void invalidOneByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 1) };

        try {
            RLP.decodeFirstElement(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    void invalidOneByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 1) };

        try {
            RLP.decodeListElements(encoded);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    void invalidTwoByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 2), 0x01 };

        try {
            RLP.decodeFirstElement(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    void invalidTwoByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 2), 0x01 };

        try {
            RLP.decodeListElements(encoded);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    void invalidThreeByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 3), 0x01, 0x02 };

        try {
            RLP.decodeFirstElement(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    void invalidThreeByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 3), 0x01, 0x02 };

        try {
            RLP.decodeListElements(encoded);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    void invalidFourByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 4), 0x01, 0x02, 0x03 };

        try {
            RLP.decodeFirstElement(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    void invalidFourByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 4), 0x01, 0x02, 0x03 };

        try {
            RLP.decodeListElements(encoded);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    void lengthOfLengthOfMaxIntegerDoesntOverflow() {
        try {
            // Integer.MAX_VALUE
            byte[] encoded = new byte[] { (byte)(183 + 4), (byte)0x7f, (byte)0xff, (byte)0xff, (byte)0xff };
            RLP.decodeBigInteger(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    void lengthOfLengthGreaterThanMaxIntegerOverflows() {
        try {
            // Integer.MAX_VALUE + 1
            byte[] encoded = new byte[] { (byte)(192 + 55 + 4), (byte)0x7F, (byte)0xff, (byte)0xff, (byte)0xff };
            RLP.decodeBigInteger(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The current implementation doesn't support lengths longer than Integer.MAX_VALUE because that is the largest number of elements an array can have", ex.getMessage());
        }
    }

    @Test
    void lengthOfLengthLessThanZero() {
        try {
            // Integer.MAX_VALUE + 1
            byte[] encoded = new byte[] { (byte)(183 + 4), (byte)0x80, (byte)0xff, (byte)0xff, (byte)0xff };
            RLP.decodeBigInteger(encoded, 0);
            Assertions.fail();
        }
        catch (RLPException ex) {
            Assertions.assertEquals("The length of the RLP item can't be negative", ex.getMessage());
        }
    }

    @Test
    void encodeDecodeInteger() {
        for (int k = 0; k < 2048; k++) {
            Assertions.assertEquals(k, RLPTestUtil.decodeInt(RLP.encodeInt(k), 0));
        }
    }

    @Test
    void encodeDecodeInteger128() {
        Assertions.assertEquals(128, RLPTestUtil.decodeInt(RLP.encodeInt(128), 0));
    }

    @Test
    @Disabled("Known issue, RLP.decodeInt should not be used in this case, to be reviewed")
    void encodeDecodeIntegerInList() {
        for (int k = 1; k < 2048; k++) {
            byte[] bytes = RLP.encodeList(RLP.encodeInt(k), new byte[0]);
            byte[] bytes2 = ((RLPList)(RLP.decodeListElements(bytes).get(0))).get(0).getRLPData();
            Assertions.assertEquals(k, RLPTestUtil.decodeInt(bytes2, 0));
        }
    }

    @Test
    void encodeDecodeIntegerInListUsingBigInteger() {
        for (int k = 1; k < 2048; k++) {
            byte[] bytes = RLP.encodeList(RLP.encodeInt(k), new byte[0]);
            byte[] bytes2 = ((RLPList)(RLP.decodeListElements(bytes).get(0))).get(0).getRLPData();
            Assertions.assertEquals(k, BigIntegers.fromUnsignedByteArray(bytes2).intValue());
        }
    }

    @Test
    void encodeDecodeInteger0InList() {
        byte[] bytes = RLP.encodeList(RLP.encodeInt(0));
        byte[] bytes2 = ((RLPList)(RLP.decodeListElements(bytes).get(0))).get(0).getRLPData();
        // known issue, the byte array is null
        Assertions.assertNull(bytes2);
    }

    @Test
    @Disabled("Known issue, RLP.decodeInt should not be used in this case, to be reviewed")
    void encodeDecodeInteger128InList() {
        byte[] bytes = RLP.encodeList(RLP.encodeInt(128));
        byte[] bytes2 = ((RLPList)(RLP.decodeListElements(bytes).get(0))).get(0).getRLPData();
        Assertions.assertEquals(128, RLPTestUtil.decodeInt(bytes2, 0));
    }

    @Test
    void encodeDecodeInteger128InListUsingBigInteger() {
        byte[] bytes = RLP.encodeList(RLP.encodeInt(128));
        byte[] bytes2 = ((RLPList)(RLP.decodeListElements(bytes).get(0))).get(0).getRLPData();
        Assertions.assertEquals(128, BigIntegers.fromUnsignedByteArray(bytes2).intValue());
    }

    @Test
    @Disabled("Known issue, RLP.decodeInt should not be used in this case, to be reviewed")
    void encodeDecodeInteger238InList() {
        byte[] bytes = RLP.encodeList(RLP.encodeInt(238));
        byte[] bytes2 = ((RLPList)(RLP.decodeListElements(bytes).get(0))).get(0).getRLPData();
        Assertions.assertEquals(238, RLPTestUtil.decodeInt(bytes2, 0));
    }

    @Test
    void encodeDecodeInteger238InListUsingBigInteger() {
        byte[] bytes = RLP.encodeList(RLP.encodeInt(238));
        byte[] bytes2 = ((RLPList)(RLP.decodeListElements(bytes).get(0))).get(0).getRLPData();
        Assertions.assertEquals(238, BigIntegers.fromUnsignedByteArray(bytes2).intValue());
    }
}
