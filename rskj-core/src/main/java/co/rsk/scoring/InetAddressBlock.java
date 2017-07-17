package co.rsk.scoring;

import com.google.common.annotations.VisibleForTesting;

import java.net.InetAddress;
import java.util.Arrays;

/**
 * Created by ajlopez on 11/07/2017.
 */
public class InetAddressBlock {
    private String description;
    private byte[] bytes;
    private int nbytes;
    private byte mask;

    public InetAddressBlock(InetAddress address, int bits) {
        this.description = address.getHostAddress() + "/" + bits;
        this.bytes = address.getAddress();
        this.nbytes = this.bytes.length - (bits + 7) / 8;
        this.mask = (byte)(0xff << (bits % 8));
    }

    public boolean contains(InetAddress address) {
        byte[] addressBytes = address.getAddress();

        if (addressBytes.length != this.bytes.length)
            return false;

        int k;

        for (k = 0; k < this.nbytes; k++)
            if (addressBytes[k] != this.bytes[k])
                return false;

        if (this.mask != (byte)0xff)
            return (addressBytes[k] & this.mask) == (this.bytes[k] & this.mask);

        return true;
    }

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
        if (obj == null)
            return false;

        if (!(obj instanceof InetAddressBlock))
            return false;

        InetAddressBlock block = (InetAddressBlock)obj;

        return block.mask == this.mask && Arrays.equals(block.bytes, this.bytes);
    }

    @VisibleForTesting
    public byte[] getBytes() {
        return this.bytes;
    }

    @VisibleForTesting
    public byte getMask() {
        return this.mask;
    }
}
