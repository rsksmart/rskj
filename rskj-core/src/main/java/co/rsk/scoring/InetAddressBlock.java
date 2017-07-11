package co.rsk.scoring;

import java.net.InetAddress;

/**
 * Created by ajlopez on 11/07/2017.
 */
public class InetAddressBlock {
    private byte[] bytes;
    private int nbytes;
    private byte mask;

    public InetAddressBlock(InetAddress address, int bits) {
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
}
