package co.rsk.scoring;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InetAddressTable has a set of net addresses and blocks
 * It is used by scoring manager to keep the banned addresses
 * @see PeerScoringManager
 * <p>
 * Created by ajlopez on 10/07/2017.
 */
public class InetAddressTable {
    private final Set<InetAddress> addresses = ConcurrentHashMap.newKeySet();
    private final Set<InetAddressCidrBlock> blocks = ConcurrentHashMap.newKeySet();

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
    public void addAddressBlock(InetAddressCidrBlock addressBlock) {
        this.blocks.add(addressBlock);
    }

    /**
     * Removes an address block from the address block set
     *
     * @param addressBlock   the address block to remove
     */
    public void removeAddressBlock(InetAddressCidrBlock addressBlock) {
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
        if (this.addresses.contains(address)) {
            return true;
        }

        if (this.blocks.isEmpty()) {
            return false;
        }

        //TODO(mmarquez): we need to check if this is thread safe
        InetAddressCidrBlock[] bs = this.blocks.toArray(new InetAddressCidrBlock[0]);
        for (InetAddressCidrBlock mask : bs) {
            if (mask.contains(address)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the list of InetAddress
     *
     * @return  the list of known addresses
     */
    public List<InetAddress> getAddressList() {
        if (this.addresses.isEmpty()) {
            return new ArrayList<>();
        }

        //TODO(mmarquez): we need to check if this is thread safe
        InetAddress[] as = this.addresses.toArray(new InetAddress[0]);
        List<InetAddress> list = new ArrayList<>(as.length);

        for (InetAddress inetAddress : as) {
            list.add(inetAddress);
        }

        return list;
    }

    /**
     * Returns the list of known address blocks
     *
     * @return  the list of known address blocks
     */
    public List<InetAddressCidrBlock> getAddressBlockList() {
        if (this.blocks.isEmpty()) {
            return new ArrayList<>();
        }

        //TODO(mmarquez): we need to check if this is thread safe
        InetAddressCidrBlock[] bs = this.blocks.toArray(new InetAddressCidrBlock[0]);
        List<InetAddressCidrBlock> list = new ArrayList<>(bs.length);

        for (InetAddressCidrBlock inetAddressBlock : bs) {
            list.add(inetAddressBlock);
        }

        return list;
    }
}
