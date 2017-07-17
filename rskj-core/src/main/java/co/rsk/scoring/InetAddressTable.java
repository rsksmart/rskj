package co.rsk.scoring;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by ajlopez on 10/07/2017.
 */
public class InetAddressTable {
    private Set<InetAddress> addresses = new HashSet<>();
    private Set<InetAddressBlock> blocks = new HashSet<>();

    public void addAddress(InetAddress address) {
        this.addresses.add(address);
    }

    public void removeAddress(InetAddress address) { this.addresses.remove(address); }

    public void addAddressBlock(InetAddressBlock addressBlock) {
        this.blocks.add(addressBlock);
    }

    public void removeAddressBlock(InetAddressBlock addressBlock) {
        this.blocks.remove(addressBlock);
    }

    public void clearAddressBlocks() {
        this.blocks.clear();
    }

    public boolean contains(InetAddress address) {
        if (this.addresses.contains(address))
            return true;

        for (InetAddressBlock mask : this.blocks)
            if (mask.contains(address))
                return true;

        return false;
    }

    public List<InetAddress> getAddressList() {
        List<InetAddress> list = new ArrayList<>();

        list.addAll(this.addresses);

        return list;
    }

    public List<InetAddressBlock> getAddressBlockList() {
        List<InetAddressBlock> list = new ArrayList<>();

        list.addAll(this.blocks);

        return list;
    }
}
