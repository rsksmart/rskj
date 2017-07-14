package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

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

        Assert.assertTrue(parser.hasMask("1234/1234"));
    }
}
