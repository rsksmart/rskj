package co.rsk.logfilter;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 05/02/2019.
 */
public class BlocksBloomStoreTest {
    @Test
    public void getFirstNumberInRange() {
        Assert.assertEquals(0, BlocksBloomStore.firstNumberInRange(0));
        Assert.assertEquals(0, BlocksBloomStore.firstNumberInRange(1));
        Assert.assertEquals(BlocksBloomStore.NO_BLOCKS, BlocksBloomStore.firstNumberInRange(BlocksBloomStore.NO_BLOCKS));
        Assert.assertEquals(BlocksBloomStore.NO_BLOCKS, BlocksBloomStore.firstNumberInRange(BlocksBloomStore.NO_BLOCKS * 2 - 1));
    }

    @Test
    public void getLastNumberInRange() {
        Assert.assertEquals(BlocksBloomStore.NO_BLOCKS - 1, BlocksBloomStore.lastNumberInRange(0));
        Assert.assertEquals(BlocksBloomStore.NO_BLOCKS - 1, BlocksBloomStore.lastNumberInRange(1));
        Assert.assertEquals(BlocksBloomStore.NO_BLOCKS * 2 - 1, BlocksBloomStore.lastNumberInRange(BlocksBloomStore.NO_BLOCKS));
        Assert.assertEquals(BlocksBloomStore.NO_BLOCKS * 2 - 1, BlocksBloomStore.lastNumberInRange(BlocksBloomStore.NO_BLOCKS * 2 - 1));
    }

    @Test
    public void noBlocksBloom() {
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore();

        Assert.assertNull(blocksBloomStore.getBlocksBloomByNumber(0));
        Assert.assertNull(blocksBloomStore.getBlocksBloomByNumber(1));
        Assert.assertNull(blocksBloomStore.getBlocksBloomByNumber(BlocksBloomStore.NO_BLOCKS - 1));
        Assert.assertNull(blocksBloomStore.getBlocksBloomByNumber(BlocksBloomStore.NO_BLOCKS));
    }
}
