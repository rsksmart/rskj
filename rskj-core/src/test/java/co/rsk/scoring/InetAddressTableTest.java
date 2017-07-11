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
    public void addIPV6Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV6();

        table.addAddress(address);
        Assert.assertTrue(table.contains(address));
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
    public void addAddressMask() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV4();

        table.addAddressMask(address, 8);

        Assert.assertTrue(table.contains(address));
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
