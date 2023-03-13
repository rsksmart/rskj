package co.rsk.logfilter;

import org.ethereum.core.Bloom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 20/01/2019.
 */
class BlocksBloomTest {

    @Test
    void createBlocksBloom() {
        BlocksBloom blocksBloom = BlocksBloom.createEmpty();

        Assertions.assertEquals(0, blocksBloom.size());

        byte[] bytes = new byte[Bloom.BLOOM_BYTES];

        Assertions.assertArrayEquals(bytes, blocksBloom.getBloom().getData());
    }

    @Test
    void addBlockToBlocksBloom() {
        BlocksBloom blocksBloom = BlocksBloom.createEmpty();
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
    void addBlockZeroToBlocksBloom() {
        BlocksBloom blocksBloom = BlocksBloom.createEmpty();
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
    void addThreeBlocksToBlocksBloom() {
        BlocksBloom blocksBloom = BlocksBloom.createEmpty();
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;
        byte[] bytes3 = new byte[Bloom.BLOOM_BYTES];
        bytes3[2] = 0x20;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);
        Bloom bloom3 = new Bloom(bytes3);

        blocksBloom.addBlockBloom(1, bloom1);
        blocksBloom.addBlockBloom(2, bloom2);
        blocksBloom.addBlockBloom(3, bloom3);

        Assertions.assertEquals(3, blocksBloom.size());
        Assertions.assertEquals(1, blocksBloom.fromBlock());
        Assertions.assertEquals(3, blocksBloom.toBlock());

        bloom1.or(bloom2);
        bloom1.or(bloom3);

        Assertions.assertArrayEquals(bloom1.getData(), blocksBloom.getBloom().getData());
    }

    @Test
    void addThreeBlocksToBlocksBloomBackwards() {
        BlocksBloom blocksBloom = BlocksBloom.createEmptyWithBackwardsAddition();
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;
        byte[] bytes3 = new byte[Bloom.BLOOM_BYTES];
        bytes3[2] = 0x20;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);
        Bloom bloom3 = new Bloom(bytes3);

        blocksBloom.addBlockBloom(3, bloom3);
        blocksBloom.addBlockBloom(2, bloom2);
        blocksBloom.addBlockBloom(1, bloom1);

        Assertions.assertEquals(3, blocksBloom.size());
        Assertions.assertEquals(1, blocksBloom.fromBlock());
        Assertions.assertEquals(3, blocksBloom.toBlock());

        bloom3.or(bloom2);
        bloom3.or(bloom1);

        Assertions.assertArrayEquals(bloom3.getData(), blocksBloom.getBloom().getData());
    }

    @Test
    void backwardsAndForwardBloomEquals() {
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;
        byte[] bytes3 = new byte[Bloom.BLOOM_BYTES];
        bytes3[2] = 0x20;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);
        Bloom bloom3 = new Bloom(bytes3);

        BlocksBloom blocksBloomBackwards = BlocksBloom.createEmptyWithBackwardsAddition();
        blocksBloomBackwards.addBlockBloom(3, bloom3);
        blocksBloomBackwards.addBlockBloom(2, bloom2);
        blocksBloomBackwards.addBlockBloom(1, bloom1);

        BlocksBloom blocksBloomForward = BlocksBloom.createEmpty();
        blocksBloomForward.addBlockBloom(1, bloom1);
        blocksBloomForward.addBlockBloom(2, bloom2);
        blocksBloomForward.addBlockBloom(3, bloom3);

        Assertions.assertEquals(blocksBloomForward.size(), blocksBloomBackwards.size());
        Assertions.assertEquals(blocksBloomForward.fromBlock(), blocksBloomBackwards.fromBlock());
        Assertions.assertEquals(blocksBloomForward.toBlock(), blocksBloomBackwards.toBlock());
        Assertions.assertEquals(blocksBloomForward.getBloom(), blocksBloomBackwards.getBloom());

        bloom1.or(bloom2);
        bloom1.or(bloom3);
        Assertions.assertArrayEquals(bloom1.getData(), blocksBloomBackwards.getBloom().getData());
        Assertions.assertArrayEquals(bloom1.getData(), blocksBloomForward.getBloom().getData());

        bloom3.or(bloom2);
        bloom3.or(bloom1);
        Assertions.assertArrayEquals(bloom3.getData(), blocksBloomBackwards.getBloom().getData());
        Assertions.assertArrayEquals(bloom3.getData(), blocksBloomForward.getBloom().getData());
    }

    @Test
    void addTwoNonConsecutiveBlocksToBlocksBloom() {
        BlocksBloom blocksBloom = BlocksBloom.createEmpty();
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        blocksBloom.addBlockBloom(1, bloom1);
        Exception exceptionGap = Assertions.assertThrows(UnsupportedOperationException.class, () -> blocksBloom.addBlockBloom(3, bloom2));
        Assertions.assertEquals("Block out of sequence", exceptionGap.getMessage());

        Exception exceptionSame = Assertions.assertThrows(UnsupportedOperationException.class, () -> blocksBloom.addBlockBloom(1, bloom2));
        Assertions.assertEquals("Block out of sequence", exceptionSame.getMessage());
    }

    @Test
    void addTwoNonConsecutiveBlocksToBlocksBloomBackwards() {
        BlocksBloom blocksBloom = BlocksBloom.createEmpty();
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        blocksBloom.addBlockBloom(3, bloom1);
        Exception exceptionGap = Assertions.assertThrows(UnsupportedOperationException.class, () -> blocksBloom.addBlockBloom(1, bloom2));
        Assertions.assertEquals("Block out of sequence", exceptionGap.getMessage());

        Exception exceptionSame = Assertions.assertThrows(UnsupportedOperationException.class, () -> blocksBloom.addBlockBloom(3, bloom2));
        Assertions.assertEquals("Block out of sequence", exceptionSame.getMessage());
    }

    @Test
    void matchesBloom() {
        BlocksBloom blocksBloom = BlocksBloom.createEmpty();

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
    void matchesBloomReversed() {
        BlocksBloom blocksBloom = BlocksBloom.createEmptyWithBackwardsAddition();

        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        blocksBloom.addBlockBloom(2, bloom2);
        blocksBloom.addBlockBloom(1, bloom1);

        Assertions.assertTrue(blocksBloom.matches(bloom1));
        Assertions.assertTrue(blocksBloom.matches(bloom2));

        bloom1.or(bloom2);

        Assertions.assertTrue(blocksBloom.matches(bloom1));
    }

    @Test
    void doesNotMatchBloom() {
        BlocksBloom blocksBloom = BlocksBloom.createEmpty();

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

    @Test
    void doesNotMatchBloomReversed() {
        BlocksBloom blocksBloom = BlocksBloom.createEmptyWithBackwardsAddition();

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
