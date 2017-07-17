package co.rsk.scoring;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * InetAddressTable has a set of net addresses and blocks
 * It is used by scoring manager to keep the banned addresses
 * @see PeerScoringManager
 * <p>
 * Created by ajlopez on 10/07/2017.
 */
public class InetAddressTable {
    private Set<InetAddress> addresses = new HashSet<>();
    private Set<InetAddressBlock> blocks = new HashSet<>();

    /**
     * Adds an address into the address set
     *
     * @param address   the address to add
     */
    public void addAddress(InetAddress address) {
        this.addresses.add(address);
    }

    /**
     * Removes an address from the address set
     *
     * @param address   the address to remove
     */
    public void removeAddress(InetAddress address) { this.addresses.remove(address); }

    /**
     * Adds an address block into the address block set
     *
     * @param addressBlock   the address block to add
     */
    public void addAddressBlock(InetAddressBlock addressBlock) {
        this.blocks.add(addressBlock);
    }

    /**
     * Removes an address block from the address block set
     *
     * @param addressBlock   the address block to remove
     */
    public void removeAddressBlock(InetAddressBlock addressBlock) {
        this.blocks.remove(addressBlock);
    }

    /**
     * Checks if the given address is contained in the address set
     * or it is contained into an address block
     *
     * @param address   the address to check
     * @return  <tt>true</tt> if the address is in the address set
     * or is contained in some address block
     */
    public boolean contains(InetAddress address) {
        if (this.addresses.contains(address))
            return true;

        for (InetAddressBlock mask : this.blocks)
            if (mask.contains(address))
                return true;

        return false;
    }

    /**
     * Returns the list of InetAddress
     *
     * @return  the list of known addresses
     */
    public List<InetAddress> getAddressList() {
        List<InetAddress> list = new ArrayList<>();

        list.addAll(this.addresses);

        return list;
    }

    /**
     * Returns the list of known address blocks
     *
     * @return  the list of known address blocks
     */
    public List<InetAddressBlock> getAddressBlockList() {
        List<InetAddressBlock> list = new ArrayList<>();

        list.addAll(this.blocks);

        return list;
    }
}
