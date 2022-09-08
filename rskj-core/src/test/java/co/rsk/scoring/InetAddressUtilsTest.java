package co.rsk.scoring;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by ajlopez on 15/07/2017.
 */
public class InetAddressUtilsTest {
    @Test
    public void hasMask() {
        Assertions.assertFalse(InetAddressUtils.hasMask(null));
        Assertions.assertFalse(InetAddressUtils.hasMask("/"));
        Assertions.assertFalse(InetAddressUtils.hasMask("1234/"));
        Assertions.assertFalse(InetAddressUtils.hasMask("/1234"));
        Assertions.assertFalse(InetAddressUtils.hasMask("1234/1234/1234"));
        Assertions.assertFalse(InetAddressUtils.hasMask("1234//1234"));

        Assertions.assertTrue(InetAddressUtils.hasMask("1234/1234"));
    }

    @Test
    public void getAddressFromIPV4() throws InvalidInetAddressException {
        InetAddress address = InetAddressUtils.getAddressForBan("192.168.56.1");

        Assertions.assertNotNull(address);

        byte[] bytes = address.getAddress();

        Assertions.assertNotNull(bytes);
        Assertions.assertEquals(4, bytes.length);
        Assertions.assertArrayEquals(new byte[] { (byte)192, (byte)168, (byte)56, (byte)1}, bytes);
    }

    @Test
    public void getAddressFromIPV6() throws InvalidInetAddressException, UnknownHostException {
        InetAddress address = InetAddressUtils.getAddressForBan("fe80::498a:7f0e:e63d:6b98");
        InetAddress expected = InetAddress.getByName("fe80::498a:7f0e:e63d:6b98");

        Assertions.assertNotNull(address);

        byte[] bytes = address.getAddress();

        Assertions.assertNotNull(bytes);
        Assertions.assertEquals(16, bytes.length);
        Assertions.assertArrayEquals(expected.getAddress(), bytes);
    }

    @Test
    public void getAddressFromNull() {
        try {
            InetAddressUtils.getAddressForBan(null);
            Assertions.fail();
        }
        catch (InvalidInetAddressException ex) {
            Assertions.assertEquals("null address", ex.getMessage());
        }
    }

    @Test
    public void getAddressFromEmptyString() {
        try {
            InetAddressUtils.getAddressForBan("");
            Assertions.fail();
        }
        catch (InvalidInetAddressException ex) {
            Assertions.assertEquals("empty address", ex.getMessage());
        }
    }

    @Test
    public void getAddressFromBlankString() {
        try {
            InetAddressUtils.getAddressForBan("   ");
            Assertions.fail();
        }
        catch (InvalidInetAddressException ex) {
            Assertions.assertEquals("empty address", ex.getMessage());
        }
    }

    @Test
    public void getLocalAddress() {
        try {
            InetAddressUtils.getAddressForBan("127.0.0.1");
            Assertions.fail();
        }
        catch (InvalidInetAddressException ex) {
            Assertions.assertEquals("local address: '127.0.0.1'", ex.getMessage());
        }
    }

    @Test
    public void getLocalHost() {
        try {
            InetAddressUtils.getAddressForBan("localhost");
            Assertions.fail();
        }
        catch (InvalidInetAddressException ex) {
            Assertions.assertEquals("local address: 'localhost'", ex.getMessage());
        }
    }

    @Test
    public void getAnyLocalAddress() {
        try {
            InetAddressUtils.getAddressForBan("0.0.0.0");
            Assertions.fail();
        }
        catch (InvalidInetAddressException ex) {
            Assertions.assertEquals("local address: '0.0.0.0'", ex.getMessage());
        }
    }

    @Test
    public void parseAddressBlock() throws InvalidInetAddressException, InvalidInetAddressBlockException, UnknownHostException {
        InetAddressCidrBlock result = InetAddressUtils.parse("192.162.12.0/24");

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(InetAddress.getByName("192.162.12.0").getAddress(), result.getBytes());
        Assertions.assertEquals((byte)0x00, result.getMask());
    }

    @Test
    public void parseAddressBlockWithNonNumericBits() throws InvalidInetAddressException {
        try {
            InetAddressUtils.parse("192.162.12.0/a");
            Assertions.fail();
        }
        catch (InvalidInetAddressBlockException ex) {
            Assertions.assertEquals("Invalid mask", ex.getMessage());
        }
    }

    @Test
    public void parseAddressBlockWithNegativeNumberOfBits() throws UnknownHostException, InvalidInetAddressException {
        try {
            InetAddressUtils.parse("192.162.12.0/-10");
            Assertions.fail();
        }
        catch (InvalidInetAddressBlockException ex) {
            Assertions.assertEquals("Invalid mask", ex.getMessage());
        }
    }

    @Test
    public void parseAddressBlockWithZeroBits() throws InvalidInetAddressException {
        try {
            InetAddressUtils.parse("192.162.12.0/0");
            Assertions.fail();
        }
        catch (InvalidInetAddressBlockException ex) {
            Assertions.assertEquals("Invalid mask", ex.getMessage());
        }
    }

    @Test
    public void parseAddressBlockWithTooBigNumberOfBits() throws InvalidInetAddressException {
        try {
            InetAddressUtils.parse("192.162.12.0/1000");
            Assertions.fail();
        }
        catch (InvalidInetAddressBlockException ex) {
            Assertions.assertEquals("Invalid mask", ex.getMessage());
        }
    }
}
