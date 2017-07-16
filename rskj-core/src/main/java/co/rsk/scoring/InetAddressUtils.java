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

    public static InetAddress getAddress(String name) throws InvalidInetAddressException {
        if (name == null)
            throw new InvalidInetAddressException("null address", null);

        if (name.trim().length() == 0)
            throw new InvalidInetAddressException("empty address", null);

        try {
            InetAddress address = InetAddress.getByName(name);

            if (address.isLoopbackAddress() || address.isAnyLocalAddress())
                throw new InvalidInetAddressException("local address: '" + name + "'", null);

            return address;
        }
        catch (UnknownHostException ex) {
            throw new InvalidInetAddressException("unknown host: '" + name + "'", ex);
        }
    }

    public static InetAddressBlock parse(String text) throws InetAddressBlockParserException {
        String[] parts = text.split("/");

        InetAddress address;

        try {
            address = InetAddress.getByName(parts[0]);
        }
        catch (UnknownHostException ex) {
            throw new InetAddressBlockParserException("Unknown host", ex);
        }

        int nbits;

        try {
            nbits = Integer.parseInt(parts[1]);
        }
        catch (NumberFormatException ex) {
            throw new InetAddressBlockParserException("Invalid mask", ex);
        }

        if (nbits <= 0 || nbits > address.getAddress().length * 8)
            throw new InetAddressBlockParserException("Invalid mask", null);

        return new InetAddressBlock(address, nbits);
    }

    private InetAddressUtils() {}
}
