package co.rsk.scoring;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by ajlopez on 10/07/2017.
 */
public class InetAddressTable {
    private Set<InetAddress> addresses = new HashSet<>();

    public void addAddress(InetAddress address) {
        this.addresses.add(address);
    }

    public boolean contains(InetAddress address) {
        return this.addresses.contains(address);
    }
}
