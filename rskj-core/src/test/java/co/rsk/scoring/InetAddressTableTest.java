package co.rsk.scoring;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Created by ajlopez on 10/07/2017.
 */
class InetAddressTableTest {
    private static Random random = new Random();

    @Test
    void doesNotContainsNewIPV4Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();

        Assertions.assertFalse(table.contains(generateIPAddressV4()));
    }

    @Test
    void doesNotContainsNewIPV6Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();

        Assertions.assertFalse(table.contains(generateIPAddressV6()));
    }

    @Test
    void addIPV4Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV4();

        table.addAddress(address);
        Assertions.assertTrue(table.contains(address));
    }

    @Test
    void containExactAddressForMask32() throws UnknownHostException {
        InetAddress address = generateIPAddressV4();

        InetAddressTable table = new InetAddressTable();
        InetAddressCidrBlock addressBlock = new InetAddressCidrBlock(address, 32);

        table.addAddressBlock(addressBlock);
        Assertions.assertTrue(table.contains(address));
    }

    @Test
    void doesNotContainRandomAddressForMask32() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddressCidrBlock addressBlock = new InetAddressCidrBlock(generateIPAddressV4(), 32);

        table.addAddressBlock(addressBlock);
        Assertions.assertFalse(table.contains(generateIPAddressV4()));
    }

    @Test
    void addAndRemoveIPV4Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV4();

        table.addAddress(address);
        Assertions.assertTrue(table.contains(address));
        table.removeAddress(address);
        Assertions.assertFalse(table.contains(address));
    }

    @Test
    void addIPV6Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV6();

        table.addAddress(address);
        Assertions.assertTrue(table.contains(address));
    }

    @Test
    void addAndRemoveIPV6Address() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV6();

        table.addAddress(address);
        Assertions.assertTrue(table.contains(address));
        table.removeAddress(address);
        Assertions.assertFalse(table.contains(address));
    }

    @Test
    void addAddressTwice() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV4();

        table.addAddress(address);
        table.addAddress(address);
        Assertions.assertTrue(table.contains(address));
    }

    @Test
    void removeUnknownAddress() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = generateIPAddressV4();

        table.removeAddress(address);
        Assertions.assertFalse(table.contains(address));
    }

    @Test
    void addAddressMask() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = InetAddress.getByName("192.168.0.100");
        InetAddress address2 = InetAddress.getByName("192.122.122.122");
        InetAddress address3 = InetAddress.getByName("193.0.0.1");

        table.addAddressBlock(new InetAddressCidrBlock(address, 8));

        Assertions.assertTrue(table.contains(address));
        Assertions.assertTrue(table.contains(address2));
        Assertions.assertFalse(table.contains(address3));

        table.addAddress(address3);

        Assertions.assertTrue(table.contains(address3));
    }

    @Test
    void addAndRemoveAddressMask() throws UnknownHostException {
        InetAddressTable table = new InetAddressTable();
        InetAddress address = InetAddress.getByName("192.168.0.100");
        InetAddress address2 = InetAddress.getByName("192.122.122.122");
        InetAddress address3 = InetAddress.getByName("193.0.0.1");

        table.addAddressBlock(new InetAddressCidrBlock(address, 8));

        Assertions.assertTrue(table.contains(address));
        Assertions.assertTrue(table.contains(address2));
        Assertions.assertFalse(table.contains(address3));

        table.removeAddressBlock(new InetAddressCidrBlock(address, 8));

        Assertions.assertFalse(table.contains(address));
        Assertions.assertFalse(table.contains(address2));
        Assertions.assertFalse(table.contains(address3));
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
