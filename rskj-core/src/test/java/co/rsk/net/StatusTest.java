package co.rsk.net;

import org.ethereum.crypto.HashUtil;
import org.ethereum.util.HashUtilTest;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Random;

/**
 * Created by ajlopez on 26/08/2017.
 */
public class StatusTest {
    @Test
    public void createWithOriginalArguments() {
        byte[] hash = HashUtil.randomHash();

        Status status = new Status(42, hash);

        Assert.assertEquals(42, status.getBestBlockNumber());
        Assert.assertNotNull(status.getBestBlockHash());
        Assert.assertArrayEquals(hash, status.getBestBlockHash());
        Assert.assertNull(status.getBestBlockParentHash());
        Assert.assertNull(status.getTotalDifficulty());
    }

    @Test
    public void createWithCompleteArguments() {
        byte[] hash = HashUtil.randomHash();
        byte[] parentHash = HashUtil.randomHash();

        Status status = new Status(42, hash, parentHash, BigInteger.TEN);

        Assert.assertEquals(42, status.getBestBlockNumber());
        Assert.assertNotNull(status.getBestBlockHash());
        Assert.assertArrayEquals(hash, status.getBestBlockHash());
        Assert.assertNotNull(status.getBestBlockParentHash());
        Assert.assertArrayEquals(parentHash, status.getBestBlockParentHash());
        Assert.assertNotNull(status.getTotalDifficulty());
        Assert.assertEquals(BigInteger.TEN, status.getTotalDifficulty());
    }
}
