package co.rsk.util;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPItem;
import org.junit.Assert;
import org.junit.Test;

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
    public void encodeSingleBytes() {
        for (int k = 0; k < 128; k++) {
            byte[] bytes = new byte[1];
            bytes[0] = (byte)k;

            byte[] encoded = RLP.encodeElement(bytes);

            Assert.assertNotNull(encoded);
            Assert.assertEquals(1, encoded.length);

            RLPElement element = RLP.decode2OneItem(encoded, 0);

            Assert.assertNotNull(element);

            byte[] decoded = element.getRLPData();

            Assert.assertNotNull(decoded);
            Assert.assertEquals(1, decoded.length);
            Assert.assertEquals(bytes[0], decoded[0]);
        }
    }
}
