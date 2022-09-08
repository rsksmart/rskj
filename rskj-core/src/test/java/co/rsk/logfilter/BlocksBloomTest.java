package co.rsk.logfilter;

import org.ethereum.core.Bloom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 20/01/2019.
 */
public class BlocksBloomTest {

    @Test
    public void createBlocksBloom() {
        BlocksBloom blocksBloom = new BlocksBloom();

        Assertions.assertEquals(0, blocksBloom.size());

        byte[] bytes = new byte[Bloom.BLOOM_BYTES];

        Assertions.assertArrayEquals(bytes, blocksBloom.getBloom().getData());
    }

    @Test
    public void addBlockToBlocksBloom() {
        BlocksBloom blocksBloom = new BlocksBloom();
        byte[] bytes = new byte[Bloom.BLOOM_BYTES];
        bytes[0] = 0x01;
        Bloom bloom = new Bloom(bytes);

        blocksBloom.addBlockBloom(1, bloom);

        Assertions.assertTrue(blocksBloom.hasBlockBloom(1));
        Assertions.assertFalse(blocksBloom.hasBlockBloom(2));
        Assertions.assertEquals(1, blocksBloom.size());
        Assertions.assertEquals(1, blocksBloom.fromBlock());
        Assertions.assertEquals(1, blocksBloom.toBlock());

        Assertions.assertArrayEquals(bytes, blocksBloom.getBloom().getData());
    }

    @Test
    public void addBlockZeroToBlocksBloom() {
        BlocksBloom blocksBloom = new BlocksBloom();
        byte[] bytes = new byte[Bloom.BLOOM_BYTES];
        bytes[0] = 0x01;
        Bloom bloom = new Bloom(bytes);

        blocksBloom.addBlockBloom(0, bloom);

        Assertions.assertEquals(1, blocksBloom.size());
        Assertions.assertEquals(0, blocksBloom.fromBlock());
        Assertions.assertEquals(0, blocksBloom.toBlock());

        Assertions.assertArrayEquals(bytes, blocksBloom.getBloom().getData());
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

        Assertions.assertEquals(2, blocksBloom.size());
        Assertions.assertEquals(1, blocksBloom.fromBlock());
        Assertions.assertEquals(2, blocksBloom.toBlock());

        bloom1.or(bloom2);

        Assertions.assertArrayEquals(bloom1.getData(), blocksBloom.getBloom().getData());
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

        Exception exception = Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            blocksBloom.addBlockBloom(1, bloom1);
            blocksBloom.addBlockBloom(3, bloom2);
        });

        Assertions.assertEquals("Block out of sequence", exception.getMessage());
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

        Assertions.assertTrue(blocksBloom.matches(bloom1));
        Assertions.assertTrue(blocksBloom.matches(bloom2));

        bloom1.or(bloom2);

        Assertions.assertTrue(blocksBloom.matches(bloom1));
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

        Assertions.assertFalse(blocksBloom.matches(bloom1));
        Assertions.assertFalse(blocksBloom.matches(bloom2));

        bloom1.or(bloom2);

        Assertions.assertFalse(blocksBloom.matches(bloom1));
    }
}
