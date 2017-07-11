package co.rsk.scoring;

import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import java.net.InetAddress;
import java.util.Arrays;

/**
 * Created by ajlopez on 11/07/2017.
 */
public class InetAddressMask {
    private byte[] bytes;
    private int nbytes;
    private int bits;

    public InetAddressMask(InetAddress address, int bits) {
        this.bytes = address.getAddress();
        this.bits = bits;
        this.nbytes = this.bytes.length - this.bits / 8;
    }

    public boolean contains(InetAddress address) {
        byte[] addressBytes = address.getAddress();

        if (addressBytes.length != this.bytes.length)
            return false;

        for (int k = 0; k < this.nbytes; k++)
            if (addressBytes[k] != this.bytes[k])
                return false;

        return true;
    }
}
