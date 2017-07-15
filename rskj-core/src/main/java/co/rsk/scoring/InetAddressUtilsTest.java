package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by ajlopez on 15/07/2017.
 */
public class InetAddressUtilsTest {
    @Test
    public void hasMask() {
        Assert.assertFalse(InetAddressUtils.hasMask(null));
        Assert.assertFalse(InetAddressUtils.hasMask("/"));
        Assert.assertFalse(InetAddressUtils.hasMask("1234/"));
        Assert.assertFalse(InetAddressUtils.hasMask("/1234"));
        Assert.assertFalse(InetAddressUtils.hasMask("1234/1234/1234"));
        Assert.assertFalse(InetAddressUtils.hasMask("1234//1234"));

        Assert.assertTrue(InetAddressUtils.hasMask("1234/1234"));
    }

    @Test
    public void getAddressFromIPV4() throws UnknownHostException {
        InetAddress address = InetAddressUtils.getAddress("192.168.56.1");

        Assert.assertNotNull(address);

        byte[] bytes = address.getAddress();

        Assert.assertNotNull(bytes);
        Assert.assertEquals(4, bytes.length);
        Assert.assertArrayEquals(new byte[] { (byte)192, (byte)168, (byte)56, (byte)1}, bytes);
    }

    @Test
    public void getAddressFromIPV6() throws UnknownHostException {
        InetAddress address = InetAddressUtils.getAddress("fe80::498a:7f0e:e63d:6b98");
        InetAddress expected = InetAddress.getByName("fe80::498a:7f0e:e63d:6b98");

        Assert.assertNotNull(address);

        byte[] bytes = address.getAddress();

        Assert.assertNotNull(bytes);
        Assert.assertEquals(16, bytes.length);
        Assert.assertArrayEquals(expected.getAddress(), bytes);
    }
}
