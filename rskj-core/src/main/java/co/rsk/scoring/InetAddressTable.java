package co.rsk.scoring;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by ajlopez on 10/07/2017.
 */
public class InetAddressTable {
    private Set<InetAddress> addresses = new HashSet<>();
    private Set<InetAddressMask> masks = new HashSet<>();

    public void addAddress(InetAddress address) {
        this.addresses.add(address);
    }

    public void addAddressMask(InetAddress address, int nbits) {
        this.masks.add(new InetAddressMask(address, nbits));
    }

    public void clearAddressMasks() {
        this.masks.clear();
    }

    public boolean contains(InetAddress address) {
        if (this.addresses.contains(address))
            return true;

        for (InetAddressMask mask : this.masks)
            if (mask.contains(address))
                return true;

        return false;
    }
}
