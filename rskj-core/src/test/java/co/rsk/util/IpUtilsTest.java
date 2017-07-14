package co.rsk.util;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mario on 13/07/17.
 */
public class IpUtilsTest {

    private static final String IPV6_WITH_PORT = "[2001:db8:85a3:8d3:1319:8a2e:370:7348]:443";
    private static final String IPV6_NO_PORT = "[2001:db8:85a3:8d3:1319:8a2e:370:7348]";
    private static final String IPV6_INVALID = "2001:db8:85a3:8d3:1319:8a2e:370:7348";
    private static final String IPV4_WITH_PORT = "172.217.28.228:80";
    private static final String IPV4_NO_PORT = "172.217.28.228";

    @Test
    public void parseIPv6() {
        InetSocketAddress result = IpUtils.parseAddress(IPV6_WITH_PORT);
        Assert.assertNotNull(result);
    }

    @Test
    public void parseIPv6NoPort() {
        InetSocketAddress result = IpUtils.parseAddress(IPV6_NO_PORT);
        Assert.assertNull(result);
    }

    @Test
    public void parseIPv6InvalidFormat() {
        InetSocketAddress result = IpUtils.parseAddress(IPV6_INVALID);
        Assert.assertNull(result);
    }

    @Test
    public void parseIPv4() {
        InetSocketAddress result = IpUtils.parseAddress(IPV4_WITH_PORT);
        Assert.assertNotNull(result);
    }

    @Test
    public void parseIPv4NoPort() {
        InetSocketAddress result = IpUtils.parseAddress(IPV4_NO_PORT);
        Assert.assertNull(result);
    }

    @Test
    public void parseAddresses() {
        List<String> addresses = new ArrayList<>();
        addresses.add(IPV6_WITH_PORT);
        addresses.add(IPV6_NO_PORT);
        addresses.add(IPV6_INVALID);
        addresses.add(IPV4_WITH_PORT);
        addresses.add(IPV4_NO_PORT);
        List<InetSocketAddress> result = IpUtils.parseAddresses(addresses);
        Assert.assertTrue(CollectionUtils.isNotEmpty(result));
        Assert.assertEquals(2, CollectionUtils.size(result));
    }
}
