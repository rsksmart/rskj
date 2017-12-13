package co.rsk.util;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mario on 13/07/17.
 */
public class IpUtils {
    private static final Logger logger = LoggerFactory.getLogger(IpUtils.class);

    private static final String IPV6_INPUT_FORMAT = "^\\[(.*)\\]:([0-9]{1,})";
    private static final String IPV4_INPUT_FORMAT = "^(.*):([0-9]{1,})";
    private static final Pattern ipv6Pattern = Pattern.compile(IPV6_INPUT_FORMAT);
    private static final Pattern ipv4Pattern = Pattern.compile(IPV4_INPUT_FORMAT);


    public static InetSocketAddress parseAddress(String address) {
        if(StringUtils.isBlank(address)) {
            return null;
        }
        
        Matcher matcher = ipv6Pattern.matcher(address);
        if(matcher.matches()) {
            return parseMatch(matcher);
        }

        matcher = ipv4Pattern.matcher(address);
        if(StringUtils.countMatches(address, ":") == 1 && matcher.matches()) {
            return parseMatch(matcher);
        }

        logger.debug("Invalid address: {}. For ipv6 use de convention [address]:port. For ipv4 address:port", address);
        return null;
    }

    public static List<InetSocketAddress> parseAddresses(List<String> addresses) {
        List<InetSocketAddress> result = new ArrayList<>();
        if(CollectionUtils.isNotEmpty(addresses)) {
            for(String a : addresses) {
                InetSocketAddress res = parseAddress(a);
                if (res != null) {
                    result.add(res);
                }
            }
        }
        return result;
    }

    private static InetSocketAddress parseMatch(Matcher matcher) {
        return new InetSocketAddress(matcher.group(1), Integer.valueOf(matcher.group(2)));
    }
}
