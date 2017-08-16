package co.rsk.util;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPItem;
import org.ethereum.util.RLPList;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;

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
        byte[] result = RLP.encodeElement(null);

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

    @Ignore
    @Test
    public void encodeDecodeBigIntegers() {
        for (int k = 1; k <= 1024; k++) {
            BigInteger value = BigInteger.valueOf(k);
            byte[] encoded = RLP.encodeBigInteger(value);
            BigInteger result =RLP.decodeBigInteger(encoded, 0);
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

        RLPList list = RLP.decode2(encoded);

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

        RLPList list = RLP.decode2(encoded);

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

        RLPList list = RLP.decode2(encoded);

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

        RLPList list = RLP.decode2(encoded);

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

        RLPList list = RLP.decode2(encoded);

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

        RLPList list = RLP.decode2(encoded);

        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());

        RLPList list2 = (RLPList) list.get(0);

        Assert.assertNotNull(list2);
        Assert.assertEquals(2, list2.size());
        Assert.assertArrayEquals(value1, list2.get(0).getRLPData());
        Assert.assertArrayEquals(value2, list2.get(1).getRLPData());
    }
}
