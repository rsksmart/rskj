package co.rsk.util;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPItem;
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
}
