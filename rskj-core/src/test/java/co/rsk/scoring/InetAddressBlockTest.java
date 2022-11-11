package co.rsk.scoring;

import org.ethereum.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by ajlopez on 11/07/2017.
 */
class InetAddressBlockTest {

    @Test
    void recognizeIPV4AddressMask8Bits() throws UnknownHostException {
        InetAddress address = TestUtils.randomIpAddressV4("address");
        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assertions.assertTrue(mask.contains(address));
    }

    @Test
    void containsIPV4() throws UnknownHostException {
        InetAddress address = TestUtils.randomIpAddressV4("address");
        InetAddress address2 = alterByte(address, 3);

        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assertions.assertTrue(mask.contains(address2));
    }

    @Test
    void doesNotContainIPV4WithAlteredByte() throws UnknownHostException {
        InetAddress address = TestUtils.randomIpAddressV4("address");
        InetAddress address2 = alterByte(address, 2);

        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assertions.assertFalse(mask.contains(address2));
    }

    @Test
    void doesNotContainIPV6() throws UnknownHostException {
        InetAddress address = TestUtils.randomIpAddressV4("address");
        InetAddress address2 = TestUtils.randomIpAddressV6("address2");

        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assertions.assertFalse(mask.contains(address2));
    }

    @Test
    void using16BitsMask() throws UnknownHostException {
        InetAddress address = TestUtils.randomIpAddressV4("address");
        InetAddress address2 = alterByte(address, 2);

        InetAddressBlock mask = new InetAddressBlock(address, 16);

        Assertions.assertTrue(mask.contains(address2));
    }

    @Test
    void usingIPV4With9BitsMask() throws UnknownHostException {
        InetAddress address = TestUtils.randomIpAddressV4("address");
        byte[] bytes = address.getAddress();
        bytes[2] ^= 1;
        InetAddress address2 = InetAddress.getByAddress(bytes);
        bytes[2] ^= 2;
        InetAddress address3 = InetAddress.getByAddress(bytes);

        InetAddressBlock mask = new InetAddressBlock(address, 9);

        Assertions.assertTrue(mask.contains(address2));
        Assertions.assertFalse(mask.contains(address3));
    }

    @Test
    void usingIPV6With9BitsMask() throws UnknownHostException {
        InetAddress address = TestUtils.randomIpAddressV6("v6");
        byte[] bytes = address.getAddress();
        bytes[14] ^= 1;
        InetAddress address2 = InetAddress.getByAddress(bytes);
        bytes[14] ^= 2;
        InetAddress address3 = InetAddress.getByAddress(bytes);

        InetAddressBlock mask = new InetAddressBlock(address, 9);

        Assertions.assertTrue(mask.contains(address2));
        Assertions.assertFalse(mask.contains(address3));
    }

    @Test
    void usingIPV4With18BitsMask() throws UnknownHostException {
        InetAddress address = TestUtils.randomIpAddressV4("address");
        byte[] bytes = address.getAddress();
        bytes[1] ^= 2;
        InetAddress address2 = InetAddress.getByAddress(bytes);
        bytes[1] ^= 4;
        InetAddress address3 = InetAddress.getByAddress(bytes);

        InetAddressBlock mask = new InetAddressBlock(address, 18);

        Assertions.assertTrue(mask.contains(address2));
        Assertions.assertFalse(mask.contains(address3));
    }

    @Test
    void usingIPV6With18BitsMask() throws UnknownHostException {
        InetAddress address = TestUtils.randomIpAddressV6("v6");
        byte[] bytes = address.getAddress();
        bytes[13] ^= 2;
        InetAddress address2 = InetAddress.getByAddress(bytes);
        bytes[13] ^= 4;
        InetAddress address3 = InetAddress.getByAddress(bytes);

        InetAddressBlock mask = new InetAddressBlock(address, 18);

        Assertions.assertTrue(mask.contains(address2));
        Assertions.assertFalse(mask.contains(address3));
    }

    @Test
    void doesNotContainIPV4() throws UnknownHostException {
        InetAddress address = TestUtils.randomIpAddressV6("v6");
        InetAddress address2 = TestUtils.randomIpAddressV4("address");

        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assertions.assertFalse(mask.contains(address2));
    }

    @Test
    void equals() throws UnknownHostException {
        InetAddress address1 = TestUtils.randomIpAddressV4("address");
        InetAddress address2 = alterByte(address1, 0);
        InetAddress address3 = TestUtils.randomIpAddressV6("v6");

        InetAddressBlock block1 = new InetAddressBlock(address1, 8);
        InetAddressBlock block2 = new InetAddressBlock(address2, 9);
        InetAddressBlock block3 = new InetAddressBlock(address1, 1);
        InetAddressBlock block4 = new InetAddressBlock(address1, 8);
        InetAddressBlock block5 = new InetAddressBlock(address3, 8);

        Assertions.assertEquals(block1, block1);
        Assertions.assertEquals(block2, block2);
        Assertions.assertEquals(block3, block3);
        Assertions.assertEquals(block4, block4);
        Assertions.assertEquals(block5, block5);

        Assertions.assertEquals(block1, block4);
        Assertions.assertEquals(block4, block1);

        Assertions.assertNotEquals(block1, block2);
        Assertions.assertNotEquals(block1, block3);
        Assertions.assertNotEquals(block1, block5);

        Assertions.assertNotEquals(null, block1);
        Assertions.assertNotEquals("block", block1);

        Assertions.assertEquals(block1.hashCode(), block4.hashCode());
    }
    
    private static InetAddress alterByte(InetAddress address, int nbyte) throws UnknownHostException {
        byte[] bytes = address.getAddress();

        bytes[nbyte]++;

        return InetAddress.getByAddress(bytes);
    }
}
