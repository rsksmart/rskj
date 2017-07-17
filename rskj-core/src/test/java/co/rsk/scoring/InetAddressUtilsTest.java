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
    public void getAddressFromIPV4() throws InvalidInetAddressException {
        InetAddress address = InetAddressUtils.getAddress("192.168.56.1");

        Assert.assertNotNull(address);

        byte[] bytes = address.getAddress();

        Assert.assertNotNull(bytes);
        Assert.assertEquals(4, bytes.length);
        Assert.assertArrayEquals(new byte[] { (byte)192, (byte)168, (byte)56, (byte)1}, bytes);
    }

    @Test
    public void getAddressFromIPV6() throws InvalidInetAddressException, UnknownHostException {
        InetAddress address = InetAddressUtils.getAddress("fe80::498a:7f0e:e63d:6b98");
        InetAddress expected = InetAddress.getByName("fe80::498a:7f0e:e63d:6b98");

        Assert.assertNotNull(address);

        byte[] bytes = address.getAddress();

        Assert.assertNotNull(bytes);
        Assert.assertEquals(16, bytes.length);
        Assert.assertArrayEquals(expected.getAddress(), bytes);
    }

    @Test
    public void getAddressFromNull() {
        try {
            InetAddressUtils.getAddress(null);
            Assert.fail();
        }
        catch (InvalidInetAddressException ex) {
            Assert.assertEquals("null address", ex.getMessage());
        }
    }

    @Test
    public void getAddressFromEmptyString() {
        try {
            InetAddressUtils.getAddress("");
            Assert.fail();
        }
        catch (InvalidInetAddressException ex) {
            Assert.assertEquals("empty address", ex.getMessage());
        }
    }

    @Test
    public void getAddressFromBlankString() {
        try {
            InetAddressUtils.getAddress("   ");
            Assert.fail();
        }
        catch (InvalidInetAddressException ex) {
            Assert.assertEquals("empty address", ex.getMessage());
        }
    }

    @Test
    public void getLocalAddress() {
        try {
            InetAddressUtils.getAddress("127.0.0.1");
            Assert.fail();
        }
        catch (InvalidInetAddressException ex) {
            Assert.assertEquals("local address: '127.0.0.1'", ex.getMessage());
        }
    }

    @Test
    public void getLocalHost() {
        try {
            InetAddressUtils.getAddress("localhost");
            Assert.fail();
        }
        catch (InvalidInetAddressException ex) {
            Assert.assertEquals("local address: 'localhost'", ex.getMessage());
        }
    }

    @Test
    public void getAnyLocalAddress() {
        try {
            InetAddressUtils.getAddress("0.0.0.0");
            Assert.fail();
        }
        catch (InvalidInetAddressException ex) {
            Assert.assertEquals("local address: '0.0.0.0'", ex.getMessage());
        }
    }

    @Test
    public void parseAddressBlock() throws InvalidInetAddressException, InvalidInetAddressBlockException, UnknownHostException {
        InetAddressBlock result = InetAddressUtils.parse("192.162.12.0/8");

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(InetAddress.getByName("192.162.12.0").getAddress(), result.getBytes());
        Assert.assertEquals((byte)0xff, result.getMask());
    }

    @Test
    public void parseAddressBlockWithNonNumericBits() throws InvalidInetAddressException {
        try {
            InetAddressUtils.parse("192.162.12.0/a");
            Assert.fail();
        }
        catch (InvalidInetAddressBlockException ex) {
            Assert.assertEquals("Invalid mask", ex.getMessage());
        }
    }

    @Test
    public void parseAddressBlockWithNegativeNumberOfBits() throws UnknownHostException, InvalidInetAddressException {
        try {
            InetAddressUtils.parse("192.162.12.0/-10");
            Assert.fail();
        }
        catch (InvalidInetAddressBlockException ex) {
            Assert.assertEquals("Invalid mask", ex.getMessage());
        }
    }

    @Test
    public void parseAddressBlockWithZeroBits() throws InvalidInetAddressException {
        try {
            InetAddressUtils.parse("192.162.12.0/0");
            Assert.fail();
        }
        catch (InvalidInetAddressBlockException ex) {
            Assert.assertEquals("Invalid mask", ex.getMessage());
        }
    }

    @Test
    public void parseAddressBlockWithTooBigNumberOfBits() throws InvalidInetAddressException {
        try {
            InetAddressUtils.parse("192.162.12.0/1000");
            Assert.fail();
        }
        catch (InvalidInetAddressBlockException ex) {
            Assert.assertEquals("Invalid mask", ex.getMessage());
        }
    }
}
