package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

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
}
