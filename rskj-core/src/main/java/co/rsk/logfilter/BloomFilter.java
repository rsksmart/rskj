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
        BloomFilter result = new BloomFilter();

        byte[] resultBytes = result.getBytes();
        byte[] bytes = this.getBytes();
        byte[] bytes2 = bloomFilter.getBytes();

        for (int k = 0; k < BLOOM_BYTES; k++) {
            resultBytes[k] = (byte) (bytes[k] | bytes2[k]);
        }

        return result;
    }
}
