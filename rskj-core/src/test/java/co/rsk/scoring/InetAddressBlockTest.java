package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Created by ajlopez on 11/07/2017.
 */
public class InetAddressBlockTest {
    private static Random random = new Random();

    @Test
    public void recognizeIPV4AddressMask8Bits() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assert.assertTrue(mask.contains(address));
    }

    @Test
    public void containsIPV4() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddress address2 = alterByte(address, 3);

        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assert.assertTrue(mask.contains(address2));
    }

    @Test
    public void doesNotContainIPV4WithAlteredByte() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddress address2 = alterByte(address, 2);

        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assert.assertFalse(mask.contains(address2));
    }

    @Test
    public void doesNotContainIPV6() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddress address2 = generateIPAddressV6();

        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assert.assertFalse(mask.contains(address2));
    }

    @Test
    public void using16BitsMask() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddress address2 = alterByte(address, 2);

        InetAddressBlock mask = new InetAddressBlock(address, 16);

        Assert.assertTrue(mask.contains(address2));
    }

    @Test
    public void usingIPV4With9BitsMask() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        byte[] bytes = address.getAddress();
        bytes[2] ^= 1;
        InetAddress address2 = InetAddress.getByAddress(bytes);
        bytes[2] ^= 2;
        InetAddress address3 = InetAddress.getByAddress(bytes);

        InetAddressBlock mask = new InetAddressBlock(address, 9);

        Assert.assertTrue(mask.contains(address2));
        Assert.assertFalse(mask.contains(address3));
    }

    @Test
    public void usingIPV6With9BitsMask() throws UnknownHostException {
        InetAddress address = generateIPAddressV6();
        byte[] bytes = address.getAddress();
        bytes[14] ^= 1;
        InetAddress address2 = InetAddress.getByAddress(bytes);
        bytes[14] ^= 2;
        InetAddress address3 = InetAddress.getByAddress(bytes);

        InetAddressBlock mask = new InetAddressBlock(address, 9);

        Assert.assertTrue(mask.contains(address2));
        Assert.assertFalse(mask.contains(address3));
    }

    @Test
    public void usingIPV4With18BitsMask() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        byte[] bytes = address.getAddress();
        bytes[1] ^= 2;
        InetAddress address2 = InetAddress.getByAddress(bytes);
        bytes[1] ^= 4;
        InetAddress address3 = InetAddress.getByAddress(bytes);

        InetAddressBlock mask = new InetAddressBlock(address, 18);

        Assert.assertTrue(mask.contains(address2));
        Assert.assertFalse(mask.contains(address3));
    }

    @Test
    public void usingIPV6With18BitsMask() throws UnknownHostException {
        InetAddress address = generateIPAddressV6();
        byte[] bytes = address.getAddress();
        bytes[13] ^= 2;
        InetAddress address2 = InetAddress.getByAddress(bytes);
        bytes[13] ^= 4;
        InetAddress address3 = InetAddress.getByAddress(bytes);

        InetAddressBlock mask = new InetAddressBlock(address, 18);

        Assert.assertTrue(mask.contains(address2));
        Assert.assertFalse(mask.contains(address3));
    }

    @Test
    public void doesNotContainIPV4() throws UnknownHostException {
        InetAddress address = generateIPAddressV6();
        InetAddress address2 = generateIPAddressV4();

        InetAddressBlock mask = new InetAddressBlock(address, 8);

        Assert.assertFalse(mask.contains(address2));
    }

    private static InetAddress generateIPAddressV4() throws UnknownHostException {
        byte[] bytes = new byte[4];

        random.nextBytes(bytes);

        return InetAddress.getByAddress(bytes);
    }

    private static InetAddress generateIPAddressV6() throws UnknownHostException {
        byte[] bytes = new byte[16];

        random.nextBytes(bytes);

        return InetAddress.getByAddress(bytes);
    }

    private static InetAddress alterByte(InetAddress address, int nbyte) throws UnknownHostException {
        byte[] bytes = address.getAddress();

        bytes[nbyte]++;

        return InetAddress.getByAddress(bytes);
    }
}
