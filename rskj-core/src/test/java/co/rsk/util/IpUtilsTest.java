package co.rsk.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mario on 13/07/17.
 */
class IpUtilsTest {

    private static final String IPV6_WITH_PORT = "[2001:db8:85a3:8d3:1319:8a2e:370:7348]:443";
    private static final String IPV6_NO_PORT = "[2001:db8:85a3:8d3:1319:8a2e:370:7348]";
    private static final String IPV6_INVALID = "2001:db8:85a3:8d3:1319:8a2e:370:7348";
    private static final String IPV4_WITH_PORT = "172.217.28.228:80";
    private static final String IPV4_NO_PORT = "172.217.28.228";
    private static final String HOSTNAME_WITH_PORT = "localhost:456";

    @Test
    void parseIPv6() {
        InetSocketAddress result = IpUtils.parseAddress(IPV6_WITH_PORT);
        Assertions.assertNotNull(result);
    }

    @Test
    void parseIPv6NoPort() {
        InetSocketAddress result = IpUtils.parseAddress(IPV6_NO_PORT);
        Assertions.assertNull(result);
    }

    @Test
    void parseIPv6InvalidFormat() {
        InetSocketAddress result = IpUtils.parseAddress(IPV6_INVALID);
        Assertions.assertNull(result);
    }

    @Test
    void parseIPv4() {
        InetSocketAddress result = IpUtils.parseAddress(IPV4_WITH_PORT);
        Assertions.assertNotNull(result);
    }

    @Test
    void parseIPv4NoPort() {
        InetSocketAddress result = IpUtils.parseAddress(IPV4_NO_PORT);
        Assertions.assertNull(result);
    }

    @Test
    void parseHostnameWithPort() {
        InetSocketAddress result = IpUtils.parseAddress(HOSTNAME_WITH_PORT);
        Assertions.assertNotNull(result);
    }

    @Test
    void parsePortAboveMaximum() {
        InetSocketAddress result = IpUtils.parseAddress("localhost:65536");
        Assertions.assertNull(result);
    }

    @Test
    void parsePortLargerThanInteger() {
        InetSocketAddress result = IpUtils.parseAddress("localhost:999999999999999999999");
        Assertions.assertNull(result);
    }

    @Test
    void parsePortAtIntegerMax() {
        // 2147483647 fits in an int but is far above the valid 0-65535 port range
        InetSocketAddress result = IpUtils.parseAddress("localhost:2147483647");
        Assertions.assertNull(result);
    }

    @Test
    void parsePortJustAboveIntegerMax() {
        // 2147483648 = Integer.MAX_VALUE + 1, overflows Integer.valueOf
        InetSocketAddress result = IpUtils.parseAddress("localhost:2147483648");
        Assertions.assertNull(result);
    }

    @Test
    void parseNegativePort() {
        InetSocketAddress result = IpUtils.parseAddress("localhost:-1");
        Assertions.assertNull(result);
    }

    @Test
    void parsePortZero() {
        InetSocketAddress result = IpUtils.parseAddress(IPV4_NO_PORT + ":0");
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.getPort());
    }

    @Test
    void parseMaxValidPort() {
        InetSocketAddress result = IpUtils.parseAddress(IPV4_NO_PORT + ":65535");
        Assertions.assertNotNull(result);
        Assertions.assertEquals(65535, result.getPort());
    }

    @Test
    void parseAddressesSkipsInvalidEntriesWithoutFailing() {
        // A single malformed entry must not abort parsing of the whole list
        List<String> addresses = new ArrayList<>();
        addresses.add(IPV4_WITH_PORT);                    // good
        addresses.add("localhost:65536");                 // port out of range -> skipped
        addresses.add(IPV6_WITH_PORT);                    // good
        addresses.add("localhost:999999999999999999999"); // int overflow -> skipped
        addresses.add("garbage-no-colon");                // malformed -> skipped
        List<InetSocketAddress> result = IpUtils.parseAddresses(addresses);
        Assertions.assertEquals(2, result.size());
    }

    @Test
    void parseAddresses() {
        List<String> addresses = new ArrayList<>();
        addresses.add(IPV6_WITH_PORT);
        addresses.add(IPV6_NO_PORT);
        addresses.add(IPV6_INVALID);
        addresses.add(IPV4_WITH_PORT);
        addresses.add(IPV4_NO_PORT);
        List<InetSocketAddress> result = IpUtils.parseAddresses(addresses);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(2, result.size());
    }
}
