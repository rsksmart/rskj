package co.rsk.net;

import co.rsk.core.commons.Keccak256;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 26/08/2017.
 */
public class StatusTest {
    @Test
    public void createWithOriginalArguments() {
        Keccak256 hash = HashUtil.randomSha3Hash();

        Status status = new Status(42, hash);

        Assert.assertEquals(42, status.getBestBlockNumber());
        Assert.assertNotNull(status.getBestBlockHash());
        Assert.assertEquals(hash, status.getBestBlockHash());
        Assert.assertNull(status.getBestBlockParentHash());
        Assert.assertNull(status.getTotalDifficulty());
    }

    @Test
    public void createWithCompleteArguments() {
        Keccak256 hash = HashUtil.randomSha3Hash();
        Keccak256 parentHash = HashUtil.randomSha3Hash();

        Status status = new Status(42, hash, parentHash, BigInteger.TEN);

        Assert.assertEquals(42, status.getBestBlockNumber());
        Assert.assertNotNull(status.getBestBlockHash());
        Assert.assertEquals(hash, status.getBestBlockHash());
        Assert.assertNotNull(status.getBestBlockParentHash());
        Assert.assertEquals(parentHash, status.getBestBlockParentHash());
        Assert.assertNotNull(status.getTotalDifficulty());
        Assert.assertEquals(BigInteger.TEN, status.getTotalDifficulty());
    }
}
