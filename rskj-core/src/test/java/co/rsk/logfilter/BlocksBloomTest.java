package co.rsk.logfilter;

import org.ethereum.core.Bloom;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Created by ajlopez on 20/01/2019.
 */
public class BlocksBloomTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void createBlocksBloom() {
        BlocksBloom blocksBloom = new BlocksBloom();

        Assert.assertEquals(0, blocksBloom.size());

        byte[] bytes = new byte[Bloom.BLOOM_BYTES];

        Assert.assertArrayEquals(bytes, blocksBloom.getBloom().getData());
    }

    @Test
    public void addBlockToBlocksBloom() {
        BlocksBloom blocksBloom = new BlocksBloom();
        byte[] bytes = new byte[Bloom.BLOOM_BYTES];
        bytes[0] = 0x01;
        Bloom bloom = new Bloom(bytes);

        blocksBloom.addBlockBloom(1, bloom);

        Assert.assertTrue(blocksBloom.hasBlockBloom(1));
        Assert.assertFalse(blocksBloom.hasBlockBloom(2));
        Assert.assertEquals(1, blocksBloom.size());
        Assert.assertEquals(1, blocksBloom.fromBlock());
        Assert.assertEquals(1, blocksBloom.toBlock());

        Assert.assertArrayEquals(bytes, blocksBloom.getBloom().getData());
    }

    @Test
    public void addBlockZeroToBlocksBloom() {
        BlocksBloom blocksBloom = new BlocksBloom();
        byte[] bytes = new byte[Bloom.BLOOM_BYTES];
        bytes[0] = 0x01;
        Bloom bloom = new Bloom(bytes);

        blocksBloom.addBlockBloom(0, bloom);

        Assert.assertEquals(1, blocksBloom.size());
        Assert.assertEquals(0, blocksBloom.fromBlock());
        Assert.assertEquals(0, blocksBloom.toBlock());

        Assert.assertArrayEquals(bytes, blocksBloom.getBloom().getData());
    }

    @Test
    public void addTwoBlocksToBlocksBloom() {
        BlocksBloom blocksBloom = new BlocksBloom();
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        blocksBloom.addBlockBloom(1, bloom1);
        blocksBloom.addBlockBloom(2, bloom2);

        Assert.assertEquals(2, blocksBloom.size());
        Assert.assertEquals(1, blocksBloom.fromBlock());
        Assert.assertEquals(2, blocksBloom.toBlock());

        bloom1.or(bloom2);

        Assert.assertArrayEquals(bloom1.getData(), blocksBloom.getBloom().getData());
    }

    @Test
    public void addTwoNonConsecutiveBlocksToBlocksBloom() {
        BlocksBloom blocksBloom = new BlocksBloom();
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        exception.expect(UnsupportedOperationException.class);
        exception.expectMessage("Block out of sequence");

        blocksBloom.addBlockBloom(1, bloom1);
        blocksBloom.addBlockBloom(3, bloom2);
    }

    @Test
    public void matchesBloom() {
        BlocksBloom blocksBloom = new BlocksBloom();

        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        blocksBloom.addBlockBloom(1, bloom1);
        blocksBloom.addBlockBloom(2, bloom2);

        Assert.assertTrue(blocksBloom.matches(bloom1));
        Assert.assertTrue(blocksBloom.matches(bloom2));

        bloom1.or(bloom2);

        Assert.assertTrue(blocksBloom.matches(bloom1));
    }

    @Test
    public void doesNotMatchBloom() {
        BlocksBloom blocksBloom = new BlocksBloom();

        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        Assert.assertFalse(blocksBloom.matches(bloom1));
        Assert.assertFalse(blocksBloom.matches(bloom2));

        bloom1.or(bloom2);

        Assert.assertFalse(blocksBloom.matches(bloom1));
    }
}
