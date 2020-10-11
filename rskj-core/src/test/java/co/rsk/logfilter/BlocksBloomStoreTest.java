package co.rsk.logfilter;

import org.ethereum.core.Bloom;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 05/02/2019.
 */
public class BlocksBloomStoreTest {
    @Test
    public void getFirstNumberInRange() {
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, null);

        Assert.assertEquals(0, blocksBloomStore.firstNumberInRange(0));
        Assert.assertEquals(0, blocksBloomStore.firstNumberInRange(1));
        Assert.assertEquals(blocksBloomStore.getNoBlocks(), blocksBloomStore.firstNumberInRange(blocksBloomStore.getNoBlocks()));
        Assert.assertEquals(blocksBloomStore.getNoBlocks(), blocksBloomStore.firstNumberInRange(blocksBloomStore.getNoBlocks() * 2 - 1));
    }

    @Test
    public void getLastNumberInRange() {
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, null);

        Assert.assertEquals(blocksBloomStore.getNoBlocks() - 1, blocksBloomStore.lastNumberInRange(0));
        Assert.assertEquals(blocksBloomStore.getNoBlocks() - 1, blocksBloomStore.lastNumberInRange(1));
        Assert.assertEquals(blocksBloomStore.getNoBlocks() * 2 - 1, blocksBloomStore.lastNumberInRange(blocksBloomStore.getNoBlocks()));
        Assert.assertEquals(blocksBloomStore.getNoBlocks() * 2 - 1, blocksBloomStore.lastNumberInRange(blocksBloomStore.getNoBlocks() * 2 - 1));
    }

    @Test
    public void noBlocksBloom() {
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, null);

        Assert.assertNull(blocksBloomStore.getBlocksBloomByNumber(0));
        Assert.assertNull(blocksBloomStore.getBlocksBloomByNumber(1));
        Assert.assertNull(blocksBloomStore.getBlocksBloomByNumber(blocksBloomStore.getNoBlocks() - 1));
        Assert.assertNull(blocksBloomStore.getBlocksBloomByNumber(blocksBloomStore.getNoBlocks()));
    }

    @Test
    public void hasBlockNumberZero() {
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, new HashMapDB());

        Assert.assertFalse(blocksBloomStore.hasBlockNumber(0));

        BlocksBloom blocksBloom = new BlocksBloom();
        Bloom bloom1 = new Bloom();
        Bloom bloom2 = new Bloom();

        blocksBloom.addBlockBloom(0, bloom1);
        blocksBloom.addBlockBloom(1, bloom2);

        blocksBloomStore.addBlocksBloomCache(blocksBloom);

        Assert.assertTrue(blocksBloomStore.hasBlockNumber(0));
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

        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, null);

        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks(), bloom1);
        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks() + 1, bloom2);

        blocksBloomStore.addBlocksBloomCache(blocksBloom);

        Assert.assertSame(blocksBloom, blocksBloomStore.getBlocksBloomByNumber(blocksBloomStore.getNoBlocks()));
    }

    @Test
    public void setBlocksBloomUsingDataSource() {
        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloom blocksBloom = new BlocksBloom();
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, dataSource);

        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks(), bloom1);
        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks() + 1, bloom2);

        blocksBloomStore.addBlocksBloomCache(blocksBloom);

        Assert.assertSame(blocksBloom, blocksBloomStore.getBlocksBloomByNumber(blocksBloomStore.getNoBlocks()));

        Assert.assertNotNull(dataSource.get(BlocksBloomStore.longToKey(blocksBloomStore.getNoBlocks())));

        BlocksBloom result = BlocksBloomEncoder.decode(dataSource.get(BlocksBloomStore.longToKey(blocksBloomStore.getNoBlocks())));

        Assert.assertNotNull(result);
        Assert.assertEquals(blocksBloom.fromBlock(), result.fromBlock());
        Assert.assertEquals(blocksBloom.toBlock(), result.toBlock());
        Assert.assertArrayEquals(blocksBloom.getBloom().getData(), result.getBloom().getData());

        BlocksBloomStore blocksBloomStore2 = new BlocksBloomStore(64, 0, dataSource);

        BlocksBloom result2 = blocksBloomStore2.getBlocksBloomByNumber(blocksBloom.fromBlock());

        Assert.assertNotNull(result2);
        Assert.assertEquals(blocksBloom.fromBlock(), result2.fromBlock());
        Assert.assertEquals(blocksBloom.toBlock(), result2.toBlock());
        Assert.assertArrayEquals(blocksBloom.getBloom().getData(), result2.getBloom().getData());
    }
}
