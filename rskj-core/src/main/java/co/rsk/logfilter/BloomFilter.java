package co.rsk.logfilter;

import co.rsk.vm.BitSet;

/**
 * Created by ajlopez on 28/01/2019.
 */
public class BloomFilter extends BitSet {
    public static final int BLOOM_BITS = 256;
    public static final int BLOOM_BYTES = BLOOM_BITS / 8;

    public BloomFilter() {
        super(BLOOM_BITS);
    }

    public BloomFilter or(BloomFilter bloomFilter) {
        return this.or(bloomFilter.getBytes());
    }

    public BloomFilter or(byte[] operandBytes) {
        BloomFilter result = new BloomFilter();

        byte[] resultBytes = result.getBytes();
        byte[] bytes = this.getBytes();

        for (int k = 0; k < BLOOM_BYTES; k++) {
            resultBytes[k] = (byte) (bytes[k] | operandBytes[k]);
        }

        return result;
    }
}
