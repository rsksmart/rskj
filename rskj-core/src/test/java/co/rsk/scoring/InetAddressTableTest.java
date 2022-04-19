package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Created by ajlopez on 10/07/2017.
 */
public class InetAddressTableTest {
    private static Random random = new Random();

    @Test
    public void doesNotContainsNewIPV4Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();

        Assert.assertFalse(table.contains(generateIPAddressV4()));
    }

    @Test
    public void doesNotContainsNewIPV6Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();

        Assert.assertFalse(table.contains(generateIPAddressV6()));
    }

    @Test
    public void addIPV4Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV4();

        table.addAddress(address);
        Assert.assertTrue(table.contains(address));
    }

    @Test
    public void containExactAddressForMask32() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();

        InetAddressTable table = new InetAddressTable();
        InetAddressBlock addressBlock = new InetAddressBlock(address, 32);

        table.addAddressBlock(addressBlock);
        Assert.assertTrue(table.contains(address));
    }

    @Test
    public void doesNotContainRandomAddressForMask32() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddressBlock addressBlock = new InetAddressBlock(generateIPAddressV4(), 32);

        table.addAddressBlock(addressBlock);
        Assert.assertFalse(table.contains(generateIPAddressV4()));
    }

    @Test
    public void addAndRemoveIPV4Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV4();

        table.addAddress(address);
        Assert.assertTrue(table.contains(address));
        table.removeAddress(address);
        Assert.assertFalse(table.contains(address));
    }

    @Test
    public void addIPV6Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV6();

        table.addAddress(address);
        Assert.assertTrue(table.contains(address));
    }

    @Test
    public void addAndRemoveIPV6Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV6();

        table.addAddress(address);
        Assert.assertTrue(table.contains(address));
        table.removeAddress(address);
        Assert.assertFalse(table.contains(address));
    }

    @Test
    public void addAddressTwice() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV4();

        table.addAddress(address);
        table.addAddress(address);
        Assert.assertTrue(table.contains(address));
    }

    @Test
    public void removeUnknownAddress() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV4();

        table.removeAddress(address);
        Assert.assertFalse(table.contains(address));
    }

    @Test
    public void addAddressMask() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = InetAddress.getByName("192.168.0.100");
        InetAddress address2 = InetAddress.getByName("192.122.122.122");
        InetAddress address3 = InetAddress.getByName("193.0.0.1");

        table.addAddressBlock(new InetAddressBlock(address, 8));

        Assert.assertTrue(table.contains(address));
        Assert.assertTrue(table.contains(address2));
        Assert.assertFalse(table.contains(address3));

        table.addAddress(address3);

        Assert.assertTrue(table.contains(address3));
    }

    @Test
    public void addAndRemoveAddressMask() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = InetAddress.getByName("192.168.0.100");
        InetAddress address2 = InetAddress.getByName("192.122.122.122");
        InetAddress address3 = InetAddress.getByName("193.0.0.1");

        table.addAddressBlock(new InetAddressBlock(address, 8));

        Assert.assertTrue(table.contains(address));
        Assert.assertTrue(table.contains(address2));
        Assert.assertFalse(table.contains(address3));

        table.removeAddressBlock(new InetAddressBlock(address, 8));

        Assert.assertFalse(table.contains(address));
        Assert.assertFalse(table.contains(address2));
        Assert.assertFalse(table.contains(address3));
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
}
