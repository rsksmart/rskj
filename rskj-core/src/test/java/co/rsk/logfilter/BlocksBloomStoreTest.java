package co.rsk.logfilter;

import org.ethereum.core.Bloom;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

/**
 * Created by ajlopez on 05/02/2019.
 */
class BlocksBloomStoreTest {
    @Test
    void getFirstNumberInRange() {
        KeyValueDataSource dataSource = mock(KeyValueDataSource.class);
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, dataSource);

        Assertions.assertEquals(0, blocksBloomStore.firstNumberInRange(0));
        Assertions.assertEquals(0, blocksBloomStore.firstNumberInRange(1));
        Assertions.assertEquals(blocksBloomStore.getNoBlocks(), blocksBloomStore.firstNumberInRange(blocksBloomStore.getNoBlocks()));
        Assertions.assertEquals(blocksBloomStore.getNoBlocks(), blocksBloomStore.firstNumberInRange(blocksBloomStore.getNoBlocks() * 2L - 1));
    }

    @Test
    void getLastNumberInRange() {
        KeyValueDataSource dataSource = mock(KeyValueDataSource.class);
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, dataSource);

        Assertions.assertEquals(blocksBloomStore.getNoBlocks() - 1, blocksBloomStore.lastNumberInRange(0));
        Assertions.assertEquals(blocksBloomStore.getNoBlocks() - 1, blocksBloomStore.lastNumberInRange(1));
        Assertions.assertEquals(blocksBloomStore.getNoBlocks() * 2L - 1, blocksBloomStore.lastNumberInRange(blocksBloomStore.getNoBlocks()));
        Assertions.assertEquals(blocksBloomStore.getNoBlocks() * 2L - 1, blocksBloomStore.lastNumberInRange(blocksBloomStore.getNoBlocks() * 2L - 1));
    }

    @Test
    void noBlocksBloom() {
        KeyValueDataSource dataSource = mock(KeyValueDataSource.class);
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, dataSource);

        Assertions.assertNull(blocksBloomStore.getBlocksBloomByNumber(0));
        Assertions.assertNull(blocksBloomStore.getBlocksBloomByNumber(1));
        Assertions.assertNull(blocksBloomStore.getBlocksBloomByNumber(blocksBloomStore.getNoBlocks() - 1));
        Assertions.assertNull(blocksBloomStore.getBlocksBloomByNumber(blocksBloomStore.getNoBlocks()));
    }

    @Test
    void hasBlockNumberZero() {
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, new HashMapDB());

        Assertions.assertFalse(blocksBloomStore.hasBlockNumber(0));

        BlocksBloom blocksBloom = BlocksBloom.createEmpty();
        Bloom bloom1 = new Bloom();
        Bloom bloom2 = new Bloom();

        blocksBloom.addBlockBloom(0, bloom1);
        blocksBloom.addBlockBloom(1, bloom2);

        blocksBloomStore.addBlocksBloom(blocksBloom);

        Assertions.assertTrue(blocksBloomStore.hasBlockNumber(0));
    }

    @Test
    void hasBlockNumberInStore() {
        KeyValueDataSource internalStore = new HashMapDB();
        Bloom bloom = new Bloom();
        BlocksBloom blocksBloom = BlocksBloom.createEmpty();
        blocksBloom.addBlockBloom(64, bloom);
        blocksBloom.addBlockBloom(65, bloom);

        internalStore.put(BlocksBloomStore.longToKey(64), BlocksBloomEncoder.encode(blocksBloom));

        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, internalStore);

        Assertions.assertFalse(blocksBloomStore.hasBlockNumber(0));
        Assertions.assertTrue(blocksBloomStore.hasBlockNumber(64));
        Assertions.assertTrue(blocksBloomStore.hasBlockNumber(65));
    }

    @Test
    void addBlocksBloom() {
        BlocksBloom blocksBloom = BlocksBloom.createEmpty();
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, new HashMapDB());

        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks(), bloom1);
        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks() + 1, bloom2);

        addBlocksBloomCommon(blocksBloomStore, blocksBloom);
    }

    @Test
    void addBlocksBloomReversed() {
        BlocksBloom blocksBloom = BlocksBloom.createEmptyWithBackwardsAddition();
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, new HashMapDB());

        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks() + 1, bloom2);
        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks(), bloom1);

        addBlocksBloomCommon(blocksBloomStore, blocksBloom);
    }

    private void addBlocksBloomCommon(BlocksBloomStore blocksBloomStore, BlocksBloom blocksBloom) {
        blocksBloomStore.addBlocksBloom(blocksBloom);

        BlocksBloom actualBlocksBloom = blocksBloomStore.getBlocksBloomByNumber(blocksBloomStore.getNoBlocks());

        Assertions.assertEquals(blocksBloom.fromBlock(), actualBlocksBloom.fromBlock());
        Assertions.assertEquals(blocksBloom.toBlock(), actualBlocksBloom.toBlock());
        Assertions.assertEquals(blocksBloom.getBloom(), actualBlocksBloom.getBloom());
    }

    @Test
    void addBlocksBloomUsingDataSource() {
        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloom blocksBloom = BlocksBloom.createEmpty();
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, dataSource);

        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks(), bloom1);
        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks() + 1, bloom2);

        addBlocksBloomUsingDataSourceCommon(blocksBloomStore, blocksBloom, dataSource);
    }

    @Test
    void addBlocksBloomUsingDataSourceBackwards() {
        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloom blocksBloom = BlocksBloom.createEmptyWithBackwardsAddition();
        byte[] bytes1 = new byte[Bloom.BLOOM_BYTES];
        bytes1[0] = 0x01;
        byte[] bytes2 = new byte[Bloom.BLOOM_BYTES];
        bytes2[1] = 0x10;

        Bloom bloom1 = new Bloom(bytes1);
        Bloom bloom2 = new Bloom(bytes2);

        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, dataSource);

        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks() + 1, bloom2);
        blocksBloom.addBlockBloom(blocksBloomStore.getNoBlocks(), bloom1);

        addBlocksBloomUsingDataSourceCommon(blocksBloomStore, blocksBloom, dataSource);
    }

    private void addBlocksBloomUsingDataSourceCommon(BlocksBloomStore blocksBloomStore, BlocksBloom blocksBloom, KeyValueDataSource dataSource) {
        blocksBloomStore.addBlocksBloom(blocksBloom);

        BlocksBloom actualBlocksBloom = blocksBloomStore.getBlocksBloomByNumber(blocksBloomStore.getNoBlocks());

        Assertions.assertEquals(blocksBloom.fromBlock(), actualBlocksBloom.fromBlock());
        Assertions.assertEquals(blocksBloom.toBlock(), actualBlocksBloom.toBlock());
        Assertions.assertEquals(blocksBloom.getBloom(), actualBlocksBloom.getBloom());

        Assertions.assertNotNull(dataSource.get(BlocksBloomStore.longToKey(blocksBloomStore.getNoBlocks())));

        BlocksBloom result = BlocksBloomEncoder.decode(dataSource.get(BlocksBloomStore.longToKey(blocksBloomStore.getNoBlocks())));

        Assertions.assertNotNull(result);
        Assertions.assertEquals(blocksBloom.fromBlock(), result.fromBlock());
        Assertions.assertEquals(blocksBloom.toBlock(), result.toBlock());
        Assertions.assertArrayEquals(blocksBloom.getBloom().getData(), result.getBloom().getData());

        BlocksBloomStore blocksBloomStore2 = new BlocksBloomStore(64, 0, dataSource);

        BlocksBloom result2 = blocksBloomStore2.getBlocksBloomByNumber(blocksBloom.fromBlock());

        Assertions.assertNotNull(result2);
        Assertions.assertEquals(blocksBloom.fromBlock(), result2.fromBlock());
        Assertions.assertEquals(blocksBloom.toBlock(), result2.toBlock());
        Assertions.assertArrayEquals(blocksBloom.getBloom().getData(), result2.getBloom().getData());
    }
}
