package co.rsk.scoring;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by ajlopez on 15/07/2017.
 */
public class InetAddressUtils {
    public static boolean hasMask(String text) {
        if (text == null)
            return false;

        String[] parts = text.split("/");

        if (parts.length != 2 || parts[0].length() == 0 || parts[1].length() == 0)
            return false;

        return true;
    }

    public static InetAddress getAddress(String name) throws UnknownHostException {
        return InetAddress.getByName(name);
    }

    private InetAddressUtils() {}
}
