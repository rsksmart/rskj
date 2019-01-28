package co.rsk.logfilter;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 28/01/2019.
 */
public class BloomFilterTest {
    @Test
    public void createBloomFilter() {
        BloomFilter bloomFilter = new BloomFilter();

        Assert.assertEquals(BloomFilter.BLOOM_BITS, bloomFilter.size());
    }
}
