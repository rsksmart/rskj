package co.rsk.util;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.bouncycastle.util.BigIntegers;

import java.math.BigInteger;
import java.util.ArrayList;

/**
 * Created by ajlopez on 16/08/2017.
 */
public class RLPTest {
    @Test
    public void encodeEmptyByteArray() {
        byte[] bytes = new byte[0];

        byte[] result = RLP.encodeElement(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.length);
        Assert.assertEquals((byte)0x80, result[0]);
    }

    @Test
    public void encodeNullByteArray() {
        byte[] result = RLP.encodeElement((byte[]) null);

        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.length);
        Assert.assertEquals((byte)0x80, result[0]);
    }

    @Test
    public void encodeDecodeSingleBytes() {
        for (int k = 0; k < 128; k++) {
            byte[] bytes = new byte[1];
            bytes[0] = (byte) k;

            byte[] encoded = RLP.encodeElement(bytes);

            Assert.assertNotNull(encoded);
            Assert.assertEquals(1, encoded.length);

            RLPElement element = RLP.decode2OneItem(encoded, 0);

            Assert.assertNotNull(element);

            byte[] decoded = element.getRLPData();

            Assert.assertNotNull(decoded);
            Assert.assertArrayEquals(bytes, decoded);
        }
    }

    @Test
    public void encodeDecodeSingleBytesWithHighValue() {
        for (int k = 128; k < 256; k++) {
            byte[] bytes = new byte[1];
            bytes[0] = (byte)k;

            byte[] encoded = RLP.encodeElement(bytes);

            Assert.assertNotNull(encoded);
            Assert.assertEquals(2, encoded.length);
            Assert.assertEquals((byte)129, encoded[0]);

            RLPElement element = RLP.decode2OneItem(encoded, 0);

            Assert.assertNotNull(element);

            byte[] decoded = element.getRLPData();

            Assert.assertNotNull(decoded);
            Assert.assertArrayEquals(bytes, decoded);
        }
    }

    @Test
    public void encodeDecodeSingleBytesWithHighValueUsingEncode() {
        for (int k = 128; k < 256; k++) {
            byte[] bytes = new byte[1];
            bytes[0] = (byte)k;

            byte[] encoded = RLP.encode(bytes);

            Assert.assertNotNull(encoded);
            Assert.assertEquals(2, encoded.length);
            Assert.assertEquals((byte)129, encoded[0]);

            RLPElement element = RLP.decode2OneItem(encoded, 0);

            Assert.assertNotNull(element);

            byte[] decoded = element.getRLPData();

            Assert.assertNotNull(decoded);
            Assert.assertArrayEquals(bytes, decoded);
        }
    }

    @Test
    public void encodeDecodeShortByteArrays() {
        for (int k = 2; k < 56; k++) {
            byte[] bytes = new byte[k];

            byte[] encoded = RLP.encodeElement(bytes);

            Assert.assertNotNull(encoded);
            Assert.assertEquals(1 + k, encoded.length);
            Assert.assertEquals((byte)(128 + k), encoded[0]);

            RLPElement element = RLP.decode2OneItem(encoded, 0);

            Assert.assertNotNull(element);

            byte[] decoded = element.getRLPData();

            Assert.assertNotNull(decoded);
            Assert.assertArrayEquals(bytes, decoded);
        }
    }

    @Test
    public void encodeDecodeShortByteArraysUsingEncode() {
        for (int k = 2; k < 56; k++) {
            byte[] bytes = new byte[k];

            byte[] encoded = RLP.encode(bytes);

            Assert.assertNotNull(encoded);
            Assert.assertEquals(1 + k, encoded.length);
            Assert.assertEquals((byte)(128 + k), encoded[0]);

            RLPElement element = RLP.decode2OneItem(encoded, 0);

            Assert.assertNotNull(element);

            byte[] decoded = element.getRLPData();

            Assert.assertNotNull(decoded);
            Assert.assertArrayEquals(bytes, decoded);
        }
    }

    @Test
    public void encodeDecodeLongByteArrayWithOneByteLength() {
        byte[] bytes = new byte[56];

        byte[] encoded = RLP.encodeElement(bytes);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(2 + 56, encoded.length);
        Assert.assertEquals((byte)(183 + 1), encoded[0]);
        Assert.assertEquals((byte)56, encoded[1]);

        RLPElement element = RLP.decode2OneItem(encoded, 0);

        Assert.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assert.assertNotNull(decoded);
        Assert.assertArrayEquals(bytes, decoded);
    }

    @Test
    public void encodeDecodeLongByteArrayWithOneByteLengthUsingEncode() {
        byte[] bytes = new byte[56];

        byte[] encoded = RLP.encode(bytes);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(2 + 56, encoded.length);
        Assert.assertEquals((byte)(183 + 1), encoded[0]);
        Assert.assertEquals((byte)56, encoded[1]);

        RLPElement element = RLP.decode2OneItem(encoded, 0);

        Assert.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assert.assertNotNull(decoded);
        Assert.assertArrayEquals(bytes, decoded);
    }

    @Test
    public void encodeDecodeLongByteArrayWithTwoBytesLength() {
        byte[] bytes = new byte[256];

        byte[] encoded = RLP.encodeElement(bytes);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(3 + 256, encoded.length);
        Assert.assertEquals((byte)(183 + 2), encoded[0]);
        Assert.assertEquals((byte)1, encoded[1]);
        Assert.assertEquals((byte)0, encoded[2]);

        RLPElement element = RLP.decode2OneItem(encoded, 0);

        Assert.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assert.assertNotNull(decoded);
        Assert.assertArrayEquals(bytes, decoded);
    }

    @Test
    public void encodeDecodeLongByteArrayWithTwoBytesLengthUsingEncode() {
        byte[] bytes = new byte[256];

        byte[] encoded = RLP.encode(bytes);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(3 + 256, encoded.length);
        Assert.assertEquals((byte)(183 + 2), encoded[0]);
        Assert.assertEquals((byte)1, encoded[1]);
        Assert.assertEquals((byte)0, encoded[2]);

        RLPElement element = RLP.decode2OneItem(encoded, 0);

        Assert.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assert.assertNotNull(decoded);
        Assert.assertArrayEquals(bytes, decoded);
    }

    @Test
    public void encodeDecodeLongByteArrayWithTwoBytesLengthBorderCase() {
        byte[] bytes = new byte[256 * 256 - 1];

        byte[] encoded = RLP.encodeElement(bytes);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(3 + 256 * 256 - 1, encoded.length);
        Assert.assertEquals((byte)(183 + 2), encoded[0]);
        Assert.assertEquals((byte)0xff, encoded[1]);
        Assert.assertEquals((byte)0xff, encoded[2]);

        RLPElement element = RLP.decode2OneItem(encoded, 0);

        Assert.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assert.assertNotNull(decoded);
        Assert.assertArrayEquals(bytes, decoded);
    }

    @Test
    public void encodeDecodeLongByteArrayWithTwoBytesLengthBorderCaseUsingEncode() {
        byte[] bytes = new byte[256 * 256 - 1];

        byte[] encoded = RLP.encode(bytes);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(3 + 256 * 256 - 1, encoded.length);
        Assert.assertEquals((byte)(183 + 2), encoded[0]);
        Assert.assertEquals((byte)0xff, encoded[1]);
        Assert.assertEquals((byte)0xff, encoded[2]);

        RLPElement element = RLP.decode2OneItem(encoded, 0);

        Assert.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assert.assertNotNull(decoded);
        Assert.assertArrayEquals(bytes, decoded);
    }

    @Test
    public void encodeDecodeLongByteArrayWithThreeBytesLength() {
        byte[] bytes = new byte[256 * 256];

        byte[] encoded = RLP.encodeElement(bytes);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(4 + 256 * 256, encoded.length);
        Assert.assertEquals((byte)(183 + 3), encoded[0]);
        Assert.assertEquals((byte)0x01, encoded[1]);
        Assert.assertEquals((byte)0x00, encoded[2]);
        Assert.assertEquals((byte)0x00, encoded[3]);

        RLPElement element = RLP.decode2OneItem(encoded, 0);

        Assert.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assert.assertNotNull(decoded);
        Assert.assertArrayEquals(bytes, decoded);
    }

    @Test
    public void encodeDecodeLongByteArrayWithThreeBytesLengthUsingEncode() {
        byte[] bytes = new byte[256 * 256];

        byte[] encoded = RLP.encode(bytes);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(4 + 256 * 256, encoded.length);
        Assert.assertEquals((byte)(183 + 3), encoded[0]);
        Assert.assertEquals((byte)0x01, encoded[1]);
        Assert.assertEquals((byte)0x00, encoded[2]);
        Assert.assertEquals((byte)0x00, encoded[3]);

        RLPElement element = RLP.decode2OneItem(encoded, 0);

        Assert.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assert.assertNotNull(decoded);
        Assert.assertArrayEquals(bytes, decoded);
    }

    @Test
    public void encodeDecodeLongByteArrayWithThreeBytesLengthBorderCase() {
        byte[] bytes = new byte[256 * 256 * 256 - 1];

        byte[] encoded = RLP.encodeElement(bytes);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(4 + 256 * 256 * 256 - 1, encoded.length);
        Assert.assertEquals((byte)(183 + 3), encoded[0]);
        Assert.assertEquals((byte)0xff, encoded[1]);
        Assert.assertEquals((byte)0xff, encoded[2]);
        Assert.assertEquals((byte)0xff, encoded[3]);

        RLPElement element = RLP.decode2OneItem(encoded, 0);

        Assert.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assert.assertNotNull(decoded);
        Assert.assertArrayEquals(bytes, decoded);
    }

    @Test
    public void encodeDecodeLongByteArrayWithThreeBytesLengthBorderCaseUsingEncode() {
        byte[] bytes = new byte[256 * 256 * 256 - 1];

        byte[] encoded = RLP.encode(bytes);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(4 + 256 * 256 * 256 - 1, encoded.length);
        Assert.assertEquals((byte)(183 + 3), encoded[0]);
        Assert.assertEquals((byte)0xff, encoded[1]);
        Assert.assertEquals((byte)0xff, encoded[2]);
        Assert.assertEquals((byte)0xff, encoded[3]);

        RLPElement element = RLP.decode2OneItem(encoded, 0);

        Assert.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assert.assertNotNull(decoded);
        Assert.assertArrayEquals(bytes, decoded);
    }

    @Test
    public void encodeDecodeLongByteArrayWithFourBytesLength() {
        byte[] bytes = new byte[256 * 256 * 256];

        byte[] encoded = RLP.encodeElement(bytes);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(5 + 256 * 256 * 256, encoded.length);
        Assert.assertEquals((byte)(183 + 4), encoded[0]);
        Assert.assertEquals((byte)0x01, encoded[1]);
        Assert.assertEquals((byte)0x00, encoded[2]);
        Assert.assertEquals((byte)0x00, encoded[3]);
        Assert.assertEquals((byte)0x00, encoded[4]);

        RLPElement element = RLP.decode2OneItem(encoded, 0);

        Assert.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assert.assertNotNull(decoded);
        Assert.assertArrayEquals(bytes, decoded);
    }

    @Test
    public void encodeDecodeLongByteArrayWithFourBytesLengthUsingEncode() {
        byte[] bytes = new byte[256 * 256 * 256];

        byte[] encoded = RLP.encode(bytes);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(5 + 256 * 256 * 256, encoded.length);
        Assert.assertEquals((byte)(183 + 4), encoded[0]);
        Assert.assertEquals((byte)0x01, encoded[1]);
        Assert.assertEquals((byte)0x00, encoded[2]);
        Assert.assertEquals((byte)0x00, encoded[3]);
        Assert.assertEquals((byte)0x00, encoded[4]);

        RLPElement element = RLP.decode2OneItem(encoded, 0);

        Assert.assertNotNull(element);

        byte[] decoded = element.getRLPData();

        Assert.assertNotNull(decoded);
        Assert.assertArrayEquals(bytes, decoded);
    }

    @Test
    public void encodeDecodeBigIntegers() {
        for (int k = 0; k <= 1024; k++) {
            BigInteger value = BigInteger.valueOf(k);
            byte[] encoded = RLP.encodeBigInteger(value);
            BigInteger result = RLP.decodeBigInteger(encoded, 0);
            Assert.assertNotNull(result);
            Assert.assertEquals(value, result);
        }
    }

    @Test
    public void encodeDecodeEmptyList() {
        byte[] encoded = RLP.encodeList();

        Assert.assertNotNull(encoded);
        Assert.assertEquals(1, encoded.length);
        Assert.assertEquals((byte)192, encoded[0]);

        ArrayList<RLPElement> list = RLP.decode2(encoded);

        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assert.assertNotNull(list2);
        Assert.assertEquals(0, list2.size());
    }

    @Test
    public void encodeDecodeShortListWithShortBytes() {
        byte[] value1 = new byte[] { 0x01 };
        byte[] value2 = new byte[] { 0x02 };
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(3, encoded.length);
        Assert.assertEquals((byte)(192 + 2), encoded[0]);

        ArrayList<RLPElement> list = RLP.decode2(encoded);

        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assert.assertNotNull(list2);
        Assert.assertEquals(2, list2.size());
        Assert.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assert.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    public void encodeDecodeShortListWithTwoByteArrays() {
        byte[] value1 = new byte[] { 0x01, 0x02 };
        byte[] value2 = new byte[] { 0x03, 0x04 };
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(1 + 3 + 3, encoded.length);
        Assert.assertEquals((byte)(192 + 3 + 3), encoded[0]);

        ArrayList<RLPElement> list = RLP.decode2(encoded);

        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assert.assertNotNull(list2);
        Assert.assertEquals(2, list2.size());
        Assert.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assert.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    public void encodeDecodeShortListWithTwoByteArraysWithElementsLength55() {
        byte[] value1 = new byte[25];
        byte[] value2 = new byte[28];
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(1 + 1 + 25 + 1 + 28, encoded.length);
        Assert.assertEquals((byte)(192 + 55), encoded[0]);

        ArrayList<RLPElement> list = RLP.decode2(encoded);

        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assert.assertNotNull(list2);
        Assert.assertEquals(2, list2.size());
        Assert.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assert.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    public void encodeDecodeShortListWithTwoByteArraysWithElementsLength56() {
        byte[] value1 = new byte[26];
        byte[] value2 = new byte[28];
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(1 + 1 + 1 + 26 + 1 + 28, encoded.length);
        Assert.assertEquals((byte)(247 + 1), encoded[0]);
        Assert.assertEquals((byte)(56), encoded[1]);

        ArrayList<RLPElement> list = RLP.decode2(encoded);

        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assert.assertNotNull(list2);
        Assert.assertEquals(2, list2.size());
        Assert.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assert.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    public void encodeDecodeShortListWithTwoByteArraysWithOneByteLength() {
        byte[] value1 = new byte[125];
        byte[] value2 = new byte[126];
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(1 + 1 + 2 + 125 + 2 + 126, encoded.length);
        Assert.assertEquals((byte)(247 + 1), encoded[0]);
        Assert.assertEquals((byte)(255), encoded[1]);

        ArrayList<RLPElement> list = RLP.decode2(encoded);

        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assert.assertNotNull(list2);
        Assert.assertEquals(2, list2.size());
        Assert.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assert.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    public void encodeDecodeShortListWithTwoByteArraysWithTwoBytesLength() {
        byte[] value1 = new byte[126];
        byte[] value2 = new byte[126];
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(1 + 2 + 2 + 126 + 2 + 126, encoded.length);
        Assert.assertEquals((byte)(247 + 2), encoded[0]);
        Assert.assertEquals((byte)(1), encoded[1]);
        Assert.assertEquals((byte)(0), encoded[2]);

        ArrayList<RLPElement> list = RLP.decode2(encoded);

        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assert.assertNotNull(list2);
        Assert.assertEquals(2, list2.size());
        Assert.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assert.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    public void encodeDecodeShortListWithTwoByteArraysWithTwoBytesLengthBorderCase() {
        byte[] value1 = new byte[128 * 256 - 3 - 1];
        byte[] value2 = new byte[128 * 256 - 3];
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(1 + 2 + 3 + (128 * 256 - 3 - 1) + 3 + (128 * 256 - 3), encoded.length);
        Assert.assertEquals((byte)(247 + 2), encoded[0]);
        Assert.assertEquals((byte)(255), encoded[1]);
        Assert.assertEquals((byte)(255), encoded[2]);

        ArrayList<RLPElement> list = RLP.decode2(encoded);

        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assert.assertNotNull(list2);
        Assert.assertEquals(2, list2.size());
        Assert.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assert.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    public void encodeDecodeShortListWithTwoByteArraysWithThreeBytesLength() {
        byte[] value1 = new byte[128 * 256 - 3];
        byte[] value2 = new byte[128 * 256 - 3];
        byte[] element1 = RLP.encodeElement(value1);
        byte[] element2 = RLP.encodeElement(value2);
        byte[] encoded = RLP.encodeList(element1, element2);

        Assert.assertNotNull(encoded);
        Assert.assertEquals(1 + 3 + 3 + (128 * 256 - 3) + 3 + (128 * 256 - 3), encoded.length);
        Assert.assertEquals((byte)(247 + 3), encoded[0]);
        Assert.assertEquals((byte)(1), encoded[1]);
        Assert.assertEquals((byte)(0), encoded[2]);
        Assert.assertEquals((byte)(0), encoded[3]);

        ArrayList<RLPElement> list = RLP.decode2(encoded);

        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assert.assertNotNull(list2);
        Assert.assertEquals(2, list2.size());
        Assert.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assert.assertArrayEquals(value2, list2.get(1).getRLPData());
    }

    @Test
    public void invalidLengthWithZeroByteLength() {
        byte[] encoded = new byte[] { (byte)0x81 };

        try {
            RLP.decode2OneItem(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithZeroByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)0x81 };

        try {
            RLP.decode2(encoded);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithOneByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 1), 0x01 };

        try {
            RLP.decode2OneItem(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithOneByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 1), 0x01 };

        try {
            RLP.decode2(encoded);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithOneByteLengthBorderCase() {
        byte[] encoded = new byte[256];
        encoded[0] = (byte)(183 + 1);
        encoded[1] = (byte)0xff;

        try {
            RLP.decode2OneItem(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithOneByteLengthBorderCaseUsingDecode2() {
        byte[] encoded = new byte[256];
        encoded[0] = (byte)(183 + 1);
        encoded[1] = (byte)0xff;

        try {
            RLP.decode2(encoded);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithTwoByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 2), 0x01, 0x00 };

        try {
            RLP.decode2OneItem(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithTwoByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 2), 0x01, 0x00 };

        try {
            RLP.decode2(encoded);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithTwoByteLengthBorderCase() {
        byte[] encoded = new byte[1 + 2 + 256 * 256 - 2];
        encoded[0] = (byte)(183 + 2);
        encoded[1] = (byte)0xff;
        encoded[2] = (byte)0xff;

        try {
            RLP.decode2OneItem(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithTwoByteLengthBorderCaseUsingDecode2() {
        byte[] encoded = new byte[1 + 2 + 256 * 256 - 2];
        encoded[0] = (byte)(183 + 2);
        encoded[1] = (byte)0xff;
        encoded[2] = (byte)0xff;

        try {
            RLP.decode2(encoded);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithThreeByteLengthBorderCase() {
        byte[] encoded = new byte[1 + 3 + 256 * 256 * 256 - 2];
        encoded[0] = (byte)(183 + 3);
        encoded[1] = (byte)255;
        encoded[2] = (byte)255;
        encoded[3] = (byte)255;

        try {
            RLP.decode2OneItem(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithThreeByteLengthBorderCaseUsingDecode2() {
        byte[] encoded = new byte[1 + 3 + 256 * 256 * 256 - 2];
        encoded[0] = (byte)(183 + 3);
        encoded[1] = (byte)255;
        encoded[2] = (byte)255;
        encoded[3] = (byte)255;

        try {
            RLP.decode2(encoded);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithFourByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 4), 0x01, 0x00, 0x00, 0x00 };

        try {
            RLP.decode2OneItem(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidLengthWithFourByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 4), 0x01, 0x00, 0x00, 0x00 };

        try {
            RLP.decode2(encoded);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void invalidOneByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 1) };

        try {
            RLP.decode2OneItem(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    public void invalidOneByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 1) };

        try {
            RLP.decode2(encoded);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    public void invalidTwoByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 2), 0x01 };

        try {
            RLP.decode2OneItem(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    public void invalidTwoByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 2), 0x01 };

        try {
            RLP.decode2(encoded);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    public void invalidThreeByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 3), 0x01, 0x02 };

        try {
            RLP.decode2OneItem(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    public void invalidThreeByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 3), 0x01, 0x02 };

        try {
            RLP.decode2(encoded);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    public void invalidFourByteLength() {
        byte[] encoded = new byte[] { (byte)(183 + 4), 0x01, 0x02, 0x03 };

        try {
            RLP.decode2OneItem(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    public void invalidFourByteLengthUsingDecode2() {
        byte[] encoded = new byte[] { (byte)(183 + 4), 0x01, 0x02, 0x03 };

        try {
            RLP.decode2(encoded);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The length of the RLP item length can't possibly fit the data byte array", ex.getMessage());
        }
    }

    @Test
    public void lengthOfLengthOfMaxIntegerDoesntOverflow() {
        try {
            // Integer.MAX_VALUE
            byte[] encoded = new byte[] { (byte)(183 + 4), (byte)0x7f, (byte)0xff, (byte)0xff, (byte)0xff };
            RLP.decodeBigInteger(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The RLP byte array doesn't have enough space to hold an element with the specified length", ex.getMessage());
        }
    }

    @Test
    public void lengthOfLengthGreaterThanMaxIntegerOverflows() {
        try {
            // Integer.MAX_VALUE + 1
            byte[] encoded = new byte[] { (byte)(183 + 4), (byte)0x80, (byte)0xff, (byte)0xff, (byte)0xff };
            RLP.decodeBigInteger(encoded, 0);
            Assert.fail();
        }
        catch (RLPException ex) {
            Assert.assertEquals("The current implementation doesn't support lengths longer than Integer.MAX_VALUE because that is the largest number of elements an array can have", ex.getMessage());
        }
    }

    @Test
    public void encodeDecodeInteger() {
        for (int k = 0; k < 2048; k++) {
            Assert.assertEquals(k, RLP.decodeInt(RLP.encodeInt(k), 0));
        }
    }

    @Test
    public void encodeDecodeInteger128() {
        Assert.assertEquals(128, RLP.decodeInt(RLP.encodeInt(128), 0));
    }

    @Test
    @Ignore
    // Known issue, RLP.decodeInt should not be used in this case, to be reviewed
    public void encodeDecodeIntegerInList() {
        for (int k = 1; k < 2048; k++) {
            byte[] bytes = RLP.encodeList(RLP.encodeInt(k), new byte[0]);
            byte[] bytes2 = ((RLPList)(RLP.decode2(bytes).get(0))).get(0).getRLPData();
            Assert.assertEquals(k, RLP.decodeInt(bytes2, 0));
        }
    }

    @Test
    public void encodeDecodeIntegerInListUsingBigInteger() {
        for (int k = 1; k < 2048; k++) {
            byte[] bytes = RLP.encodeList(RLP.encodeInt(k), new byte[0]);
            byte[] bytes2 = ((RLPList)(RLP.decode2(bytes).get(0))).get(0).getRLPData();
            Assert.assertEquals(k, BigIntegers.fromUnsignedByteArray(bytes2).intValue());
        }
    }

    @Test
    public void encodeDecodeInteger0InList() {
        byte[] bytes = RLP.encodeList(RLP.encodeInt(0));
        byte[] bytes2 = ((RLPList)(RLP.decode2(bytes).get(0))).get(0).getRLPData();
        // known issue, the byte array is null
        Assert.assertNull(bytes2);
    }

    @Test
    @Ignore
    // Known issue, RLP.decodeInt should not be used in this case, to be reviewed
    public void encodeDecodeInteger128InList() {
        byte[] bytes = RLP.encodeList(RLP.encodeInt(128));
        byte[] bytes2 = ((RLPList)(RLP.decode2(bytes).get(0))).get(0).getRLPData();
        Assert.assertEquals(128, RLP.decodeInt(bytes2, 0));
    }

    @Test
    public void encodeDecodeInteger128InListUsingBigInteger() {
        byte[] bytes = RLP.encodeList(RLP.encodeInt(128));
        byte[] bytes2 = ((RLPList)(RLP.decode2(bytes).get(0))).get(0).getRLPData();
        Assert.assertEquals(128, BigIntegers.fromUnsignedByteArray(bytes2).intValue());
    }

    @Test
    @Ignore
    // Known issue, RLP.decodeInt should not be used in this case, to be reviewed
    public void encodeDecodeInteger238InList() {
        byte[] bytes = RLP.encodeList(RLP.encodeInt(238));
        byte[] bytes2 = ((RLPList)(RLP.decode2(bytes).get(0))).get(0).getRLPData();
        Assert.assertEquals(238, RLP.decodeInt(bytes2, 0));
    }

    @Test
    public void encodeDecodeInteger238InListUsingBigInteger() {
        byte[] bytes = RLP.encodeList(RLP.encodeInt(238));
        byte[] bytes2 = ((RLPList)(RLP.decode2(bytes).get(0))).get(0).getRLPData();
        Assert.assertEquals(238, BigIntegers.fromUnsignedByteArray(bytes2).intValue());
    }
}
