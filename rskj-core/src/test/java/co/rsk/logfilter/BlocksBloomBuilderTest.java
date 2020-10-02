package co.rsk.logfilter;

import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Blockchain;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 29/09/2020.
 */
public class BlocksBloomBuilderTest {
    @Test
    public void noBlocksBloomInProcessAtTheBeginning() {
        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, dataSource);
        BlocksBloomBuilder blocksBloomBuilder = new BlocksBloomBuilder(blocksBloomStore, null);

        Assert.assertNull(blocksBloomBuilder.getBlocksBloomInProcess());
    }

    @Test
    public void processFirstNewBlock() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomBuilder blocksBloomBuilder = new BlocksBloomBuilder(blocksBloomStore, world.getBlockStore());

        blocksBloomBuilder.processNewBlockNumber(4);

        BlocksBloom result = blocksBloomBuilder.getBlocksBloomInProcess();

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.fromBlock());
        Assert.assertEquals(2, result.toBlock());

        Assert.assertTrue(dataSource.keys().isEmpty());
    }

    @Test
    public void processFirstNewBlockInSecondRange() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomBuilder blocksBloomBuilder = new BlocksBloomBuilder(blocksBloomStore, world.getBlockStore());

        blocksBloomBuilder.processNewBlockNumber(7);

        BlocksBloom result = blocksBloomBuilder.getBlocksBloomInProcess();

        Assert.assertNotNull(result);
        Assert.assertEquals(4, result.fromBlock());
        Assert.assertEquals(5, result.toBlock());

        Assert.assertTrue(dataSource.keys().isEmpty());
    }

    @Test
    public void processFirstNewBlockInSecondRangeWithOnlyOneBlock() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomBuilder blocksBloomBuilder = new BlocksBloomBuilder(blocksBloomStore, world.getBlockStore());

        blocksBloomBuilder.processNewBlockNumber(6);

        BlocksBloom result = blocksBloomBuilder.getBlocksBloomInProcess();

        Assert.assertNotNull(result);
        Assert.assertEquals(4, result.fromBlock());
        Assert.assertEquals(4, result.toBlock());

        Assert.assertTrue(dataSource.keys().isEmpty());
    }

    @Test
    public void processBlocksInSecondRangeOnly() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomBuilder blocksBloomBuilder = new BlocksBloomBuilder(blocksBloomStore, world.getBlockStore());

        blocksBloomBuilder.processNewBlockNumber(6);
        blocksBloomBuilder.processNewBlockNumber(4);

        BlocksBloom result = blocksBloomBuilder.getBlocksBloomInProcess();

        Assert.assertNotNull(result);
        Assert.assertEquals(4, result.fromBlock());
        Assert.assertEquals(4, result.toBlock());

        Assert.assertTrue(dataSource.keys().isEmpty());
    }

    @Test
    public void processNewBlockTwice() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomBuilder blocksBloomBuilder = new BlocksBloomBuilder(blocksBloomStore, world.getBlockStore());

        blocksBloomBuilder.processNewBlockNumber(4);
        blocksBloomBuilder.processNewBlockNumber(4);

        BlocksBloom result = blocksBloomBuilder.getBlocksBloomInProcess();

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.fromBlock());
        Assert.assertEquals(2, result.toBlock());

        Assert.assertTrue(dataSource.keys().isEmpty());
    }

    @Test
    public void processBlocksToFillRange() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomBuilder blocksBloomBuilder = new BlocksBloomBuilder(blocksBloomStore, world.getBlockStore());

        blocksBloomBuilder.processNewBlockNumber(4);
        blocksBloomBuilder.processNewBlockNumber(5);

        BlocksBloom result = blocksBloomBuilder.getBlocksBloomInProcess();

        Assert.assertNull(result);

        Assert.assertFalse(dataSource.keys().isEmpty());
        Assert.assertEquals(1, dataSource.keys().size());
    }

    @Test
    public void processBlocksToFillRangeAndStartTheNextOne() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomBuilder blocksBloomBuilder = new BlocksBloomBuilder(blocksBloomStore, world.getBlockStore());

        blocksBloomBuilder.processNewBlockNumber(4);
        blocksBloomBuilder.processNewBlockNumber(6);

        BlocksBloom result = blocksBloomBuilder.getBlocksBloomInProcess();

        Assert.assertNotNull(result);
        Assert.assertEquals(4, result.fromBlock());
        Assert.assertEquals(4, result.toBlock());

        Assert.assertFalse(dataSource.keys().isEmpty());
        Assert.assertEquals(1, dataSource.keys().size());
    }
}
