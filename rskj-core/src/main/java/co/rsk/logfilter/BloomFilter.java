package co.rsk.logfilter;

import co.rsk.vm.BitSet;

/**
 * Created by ajlopez on 28/01/2019.
 */
public class BloomFilter extends BitSet {
    public static final int BLOOM_BITS = 256;

    public BloomFilter() {
        super(BLOOM_BITS);
    }
}
