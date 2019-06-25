package co.rsk.logfilter;

import org.ethereum.core.Bloom;
import org.junit.Assert;
import org.junit.Test;

/** Created by ajlopez on 05/02/2019. */
public class BlocksBloomStoreTest {
    @Test
    public void getFirstNumberInRange() {
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0);

        Assert.assertEquals(0, blocksBloomStore.firstNumberInRange(0));
        Assert.assertEquals(0, blocksBloomStore.firstNumberInRange(1));
        Assert.assertEquals(
                blocksBloomStore.getNoBlocks(),
                blocksBloomStore.firstNumberInRange(blocksBloomStore.getNoBlocks()));
        Assert.assertEquals(
                blocksBloomStore.getNoBlocks(),
                blocksBloomStore.firstNumberInRange(blocksBloomStore.getNoBlocks() * 2 - 1));
    }

    @Test
    public void getLastNumberInRange() {
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0);

        Assert.assertEquals(
                blocksBloomStore.getNoBlocks() - 1, blocksBloomStore.lastNumberInRange(0));
        Assert.assertEquals(
                blocksBloomStore.getNoBlocks() - 1, blocksBloomStore.lastNumberInRange(1));
        Assert.assertEquals(
                blocksBloomStore.getNoBlocks() * 2 - 1,
                blocksBloomStore.lastNumberInRange(blocksBloomStore.getNoBlocks()));
        Assert.assertEquals(
                blocksBloomStore.getNoBlocks() * 2 - 1,
                blocksBloomStore.lastNumberInRange(blocksBloomStore.getNoBlocks() * 2 - 1));
    }

    @Test
    public void noBlocksBloom() {
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0);

        Assert.assertNull(blocksBloomStore.getBlocksBloomByNumber(0));
        Assert.assertNull(blocksBloomStore.getBlocksBloomByNumber(1));
        Assert.assertNull(
                blocksBloomStore.getBlocksBloomByNumber(blocksBloomStore.getNoBlocks() - 1));
        Assert.assertNull(blocksBloomStore.getBlocksBloomByNumber(blocksBloomStore.getNoBlocks()));
    }

    @Test
    public void setBlocksBloom() {
        BlocksBloom blocksBloom = new BlocksBloom();
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0);

        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks(), bloom1);
        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks() + 1, bloom2);

        blocksBloomStore.setBlocksBloom(blocksBloom);

        Assert.assertSame(
                blocksBloom,
                blocksBloomStore.getBlocksBloomByNumber(blocksBloomStore.getNoBlocks()));
    }
}
