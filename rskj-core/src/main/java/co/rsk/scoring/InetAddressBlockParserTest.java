package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by ajlopez on 14/07/2017.
 */
public class InetAddressBlockParserTest {
    @Test
    public void hasMask() {
        InetAddressBlockParser parser = new InetAddressBlockParser();

        Assert.assertFalse(parser.hasMask(null));
        Assert.assertFalse(parser.hasMask("/"));
        Assert.assertFalse(parser.hasMask("1234/"));
        Assert.assertFalse(parser.hasMask("/1234"));
        Assert.assertFalse(parser.hasMask("1234/1234/1234"));
        Assert.assertFalse(parser.hasMask("1234//1234"));

        Assert.assertTrue(parser.hasMask("1234/1234"));
    }

    @Test
    public void parseAddressBlock() throws UnknownHostException, InetAddressBlockParserException {
        InetAddressBlockParser parser = new InetAddressBlockParser();

        InetAddressBlock result = parser.parse("192.162.12.0/8");

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(InetAddress.getByName("192.162.12.0").getAddress(), result.getBytes());
        Assert.assertEquals((byte)0xff, result.getMask());
    }

    @Test
    public void parseAddressBlockWithNonNumericBits() throws UnknownHostException {
        InetAddressBlockParser parser = new InetAddressBlockParser();

        try {
            parser.parse("192.162.12.0/a");
            Assert.fail();
        }
        catch (InetAddressBlockParserException ex) {
            Assert.assertEquals("Invalid mask", ex.getMessage());
        }
    }

    @Test
    public void parseAddressBlockWithNegativeNumberOfBits() throws UnknownHostException {
        InetAddressBlockParser parser = new InetAddressBlockParser();

        try {
            parser.parse("192.162.12.0/-10");
            Assert.fail();
        }
        catch (InetAddressBlockParserException ex) {
            Assert.assertEquals("Invalid mask", ex.getMessage());
        }
    }

    @Test
    public void parseAddressBlockWithZeroBits() throws UnknownHostException {
        InetAddressBlockParser parser = new InetAddressBlockParser();

        try {
            parser.parse("192.162.12.0/0");
            Assert.fail();
        }
        catch (InetAddressBlockParserException ex) {
            Assert.assertEquals("Invalid mask", ex.getMessage());
        }
    }

    @Test
    public void parseAddressBlockWithTooBigNumberOfBits() throws UnknownHostException {
        InetAddressBlockParser parser = new InetAddressBlockParser();

        try {
            parser.parse("192.162.12.0/1000");
            Assert.fail();
        }
        catch (InetAddressBlockParserException ex) {
            Assert.assertEquals("Invalid mask", ex.getMessage());
        }
    }
}
