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

    @Test
    public void orEmptyBloomFiters() {
        BloomFilter bloomFilter1 = new BloomFilter();
        BloomFilter bloomFilter2 = new BloomFilter();

        BloomFilter result = bloomFilter1.or(bloomFilter2);

        Assert.assertNotNull(result);

        for (int k = 0; k < BloomFilter.BLOOM_BITS; k++) {
            Assert.assertFalse(result.get(k));
        }
    }

    @Test
    public void orBloomFiltersWithSameBitOn() {
        BloomFilter bloomFilter1 = new BloomFilter();
        BloomFilter bloomFilter2 = new BloomFilter();

        bloomFilter1.set(42);
        bloomFilter2.set(42);

        BloomFilter result = bloomFilter1.or(bloomFilter2);

        Assert.assertNotNull(result);

        for (int k = 0; k < BloomFilter.BLOOM_BITS; k++) {
            if (k == 42) {
                Assert.assertTrue(result.get(k));
            }
            else {
                Assert.assertFalse(result.get(k));
            }
        }
    }

    @Test
    public void orBloomFiltersWithDifferentBitsOn() {
        BloomFilter bloomFilter1 = new BloomFilter();
        BloomFilter bloomFilter2 = new BloomFilter();

        bloomFilter1.set(42);
        bloomFilter2.set(3);

        BloomFilter result = bloomFilter1.or(bloomFilter2);

        Assert.assertNotNull(result);

        for (int k = 0; k < BloomFilter.BLOOM_BITS; k++) {
            if (k == 42 || k == 3) {
                Assert.assertTrue(result.get(k));
            }
            else {
                Assert.assertFalse(result.get(k));
            }
        }
    }
}
