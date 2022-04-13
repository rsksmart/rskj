package co.rsk.scoring;

import com.google.common.annotations.VisibleForTesting;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * InetAddressBlock represents a range of InetAddress
 * it has a number of bits to ignore
 * <p>
 * Created by ajlopez on 11/07/2017.
 */
public class InetAddressBlock {
    private final String description;
    private final byte[] bytes;
    private final int mask;
    private final int subnet;

    /**
     * Creates an InetAddressBlock given an address and the number of cidr to ignore
     *
     * @param address the address
     * @param cidr    the cidr bits used to define a subnet
     */
    public InetAddressBlock(InetAddress address, int cidr) {
        this.description = address.getHostAddress() + "/" + cidr;
        this.bytes = address.getAddress();
        this.mask = calculateMask(cidr);
        int addressAsInt = getAddressAsInt(this.bytes);
        this.subnet = addressAsInt & mask;
    }

    /**
     * Returns if a given address is included or not in the address block
     *
     * @param address the address to check
     * @return <tt>true</tt> if the address belongs to the address range
     */
    public boolean contains(InetAddress address) {
        int addressAsInt = getAddressAsInt(address.getAddress());
        return (addressAsInt & this.mask) == subnet;
    }

    /**
     * Returns the string representation of the address block
     *
     * @return the string description of this block
     * ie "192.168.51.1/16"
     */
    public String getDescription() {
        return this.description;
    }

    @Override
    public int hashCode() {
        int result = 0;

        for (int k = 0; k < this.bytes.length; k++) {
            result *= 17;
            result += this.bytes[k];
        }

        result *= 17;
        result += this.mask;

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof InetAddressBlock)) {
            return false;
        }

        InetAddressBlock block = (InetAddressBlock) obj;

        return block.mask == this.mask && Arrays.equals(block.bytes, this.bytes);
    }

    @VisibleForTesting
    public byte[] getBytes() {
        return this.bytes.clone();
    }

    @VisibleForTesting
    public int getMask() {
        return this.mask;
    }

    private int calculateMask(int cidr) {
        return 0xffffffff << (32 - cidr);
    }

    private int getAddressAsInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }
}
