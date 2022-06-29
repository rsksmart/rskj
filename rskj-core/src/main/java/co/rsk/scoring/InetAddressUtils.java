package co.rsk.scoring;

import javax.annotation.CheckForNull;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * InetAddressUtils has static methods
 * to perform operations on InetAddress and InetAddressBlock
 * given an String representation
 * <p>
 * Created by ajlopez on 15/07/2017.
 */
public final class InetAddressUtils {
    private InetAddressUtils() {}

    /**
     * Returns <tt>true</tt> if the specified texts represent an address with mask
     * ie "192.168.51.1/16" has a mask
     *    "192.168.51.1" has no mask
     *
     * @param   text    the address
     *
     * @return <tt>true</tt> or <tt>false</tt>
     */
    public static boolean hasMask(String text) {
        if (text == null) {
            return false;
        }

        String[] parts = text.split("/");

        return parts.length == 2 && parts[0].length() != 0 && parts[1].length() != 0;
    }

    /**
     * Convert a text representation to an InetAddress
     * It supports IPV4 and IPV6 formats
     *
     * @param   hostname    the address
     *
     * @return  the text converted to an InetAddress
     */
    public static InetAddress getAddressForBan(@CheckForNull String hostname) throws InvalidInetAddressException {
        if (hostname == null) {
            throw new InvalidInetAddressException("null address", null);
        }

        String name = hostname.trim();
        if (name.length() == 0) {
            throw new InvalidInetAddressException("empty address", null);
        }

        //TODO(mmarquez): should we validate address format ??
        try {
            InetAddress address = InetAddress.getByName(name);

            if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                throw new InvalidInetAddressException("local address: '" + name + "'", null);
            }

            return address;
        }
        catch (UnknownHostException ex) {
            throw new InvalidInetAddressException("unknown host: '" + name + "'", ex);
        }
    }

    /**
     * Convert a text representation to an InetAddressBlock
     * (@see InetAddressBlock)
     * It supports IPV4 and IPV6 formats
     * ie "192.168.51.1/16" is a valid text
     *
     * @param   text    the address with mask
     *
     * @return  the text converted to an InetAddressBlock
     * @throws  InvalidInetAddressException if the text is invalid
     */
    public static InetAddressCidrBlock parse(String text) throws InvalidInetAddressException {
        //TODO(mmarquez): should we validate address format ??
        String[] parts = text.split("/");

        InetAddress address;

        address = InetAddressUtils.getAddressForBan(parts[0]);

        int nbits;

        try {
            nbits = Integer.parseInt(parts[1]);
        }
        catch (NumberFormatException ex) {
            throw new InvalidInetAddressBlockException("Invalid mask", ex);
        }

        if (nbits <= 0 || nbits > address.getAddress().length * 8) {
            throw new InvalidInetAddressBlockException("Invalid mask", null);
        }

        return new InetAddressCidrBlock(address, nbits);
    }
}
