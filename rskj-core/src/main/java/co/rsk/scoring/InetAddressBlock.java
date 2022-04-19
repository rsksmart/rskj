package co.rsk.scoring;

import com.google.common.annotations.VisibleForTesting;

import java.net.InetAddress;
import java.util.Arrays;

/**
 * InetAddressBlock represents a range of InetAddress
 * it has a number of bits to ignore
 * <p>
 * Created by ajlopez on 11/07/2017.
 */
// TODO when InetAddressBlock==subnet gets clarified, update this code-base accordingly, probably some fields and methods will no longer be needed and other will need updates (equals, hashcode...)
public class InetAddressBlock {
    private final String description;
    private final byte[] bytes;
    private final int nbytes;
    private final byte mask;

    // new algorithm data
    private final int subnetMask;
    private final int maskBytes;

    /**
     * Creates an InetAddressBlock given an address and the number of bits to ignore
     *
     * @param address the address
     * @param bits    the numbers of bits to ignore
     */
    public InetAddressBlock(InetAddress address, int bits) {
        this.description = address.getHostAddress() + "/" + bits;
        this.bytes = address.getAddress();
        this.nbytes = this.bytes.length - (bits + 7) / 8;
        this.mask = (byte) (0xff << (bits % 8));

        // new algorithm data
        this.subnetMask = (byte) (0xFF00 >> (bits & 0x07));
        this.maskBytes = bits / 8;
    }

    /**
     * Returns if a given address is included or not in the address block
     *
     * @param address the address to check
     * @return <tt>true</tt> if the address belongs to the address range
     */
    public boolean contains(InetAddress address) {
        byte[] addressBytes = address.getAddress();

        if (addressBytes.length != this.bytes.length) {
            return false;
        }

        int k;

        for (k = 0; k < this.nbytes; k++) {
            if (addressBytes[k] != this.bytes[k]) {
                return false;
            }
        }

        if (this.mask != (byte) 0xff) {
            return (addressBytes[k] & this.mask) == (this.bytes[k] & this.mask);
        }

        return true;
    }

    /**
     * Returns if a given address is included or not in the subnet defined by the address block
     *
     * @param address the address to check
     * @return <tt>true</tt> if the address belongs to the subnet defined by the address block, false otherwise
     */
    public boolean subnetContains(InetAddress address) {
        byte[] bytesToCheck = address.getAddress();

        if (bytesToCheck.length != this.bytes.length) {
            return false;
        }

        for (int i = 0; i < this.maskBytes; i++) {
            if (bytesToCheck[i] != this.bytes[i]) {
                return false;
            }
        }

        if (this.subnetMask != 0) {
            return (bytesToCheck[this.maskBytes] & this.subnetMask) == (this.bytes[this.maskBytes] & this.subnetMask);
        }

        return true;
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

        for (byte aByte : this.bytes) {
            result *= 17;
            result += aByte;
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
    public byte getMask() {
        return this.mask;
    }
}
