package co.rsk.crypto;

import org.junit.Assert;
import org.junit.Test;

public class Keccak256Compare {

    @Test
    public void testKeccak256Compare() {
        // Configurable from environment to ease constant-time property check
        // in the future.
        Keccak256 hash_orig = new Keccak256(System.getenv("KECCAK256_ORIG"));
        Keccak256 hash_eq = new Keccak256(System.getenv("KECCAK256_EQ"));
        Keccak256 hash_lt = new Keccak256(System.getenv("KECCAK256_LT"));
        Keccak256 hash_gt = new Keccak256(System.getenv("KECCAK256_GT"));
        Assert.assertEquals(hash_orig, hash_eq);
        Assert.assertTrue(hash_orig > hash_lt, 1);
        Assert.assertTrue(hash_orig < hash_gt, -1);
        // TODO: check constant-time property
    }
}
