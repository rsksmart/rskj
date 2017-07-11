package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Created by ajlopez on 11/07/2017.
 */
public class InetAddressMaskTest {
    private static Random random = new Random();

    @Test
    public void recognizeIPV4AddressMask8Bits() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddressMask mask = new InetAddressMask(address, 8);

        Assert.assertTrue(mask.contains(address));
    }

    @Test
    public void containsIPV4() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddress address2 = alterByte(address, 3);

        InetAddressMask mask = new InetAddressMask(address, 8);

        Assert.assertTrue(mask.contains(address2));
    }

    @Test
    public void doesNotcontainIPV4() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddress address2 = alterByte(address, 2);

        InetAddressMask mask = new InetAddressMask(address, 8);

        Assert.assertFalse(mask.contains(address2));
    }

    @Test
    public void doesNotContainIPV6() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();
        InetAddress address2 = generateIPAddressV6();

        InetAddressMask mask = new InetAddressMask(address, 8);

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
