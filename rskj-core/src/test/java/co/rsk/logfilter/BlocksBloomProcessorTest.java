package co.rsk.logfilter;

import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Blockchain;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 29/09/2020.
 */
public class BlocksBloomProcessorTest {
    @Test
    public void noBlocksBloomInProcessAtTheBeginning() {
        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, dataSource);
        BlocksBloomProcessor blocksBloomProcessor = new BlocksBloomProcessor(blocksBloomStore, null);

        Assertions.assertNull(blocksBloomProcessor.getBlocksBloomInProcess());
    }

    @Test
    public void processFirstNewBlock() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomProcessor blocksBloomProcessor = new BlocksBloomProcessor(blocksBloomStore, world.getBlockStore());

        blocksBloomProcessor.processNewBlockNumber(4);

        BlocksBloom result = blocksBloomProcessor.getBlocksBloomInProcess();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.fromBlock());
        Assertions.assertEquals(2, result.toBlock());

        Assertions.assertTrue(dataSource.keys().isEmpty());
    }

    @Test
    public void avoidProcessWithNoEnoughConfirmations() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 100, dataSource);
        BlocksBloomProcessor blocksBloomProcessor = new BlocksBloomProcessor(blocksBloomStore, world.getBlockStore());

        blocksBloomProcessor.processNewBlockNumber(4);

        Assertions.assertNull(blocksBloomProcessor.getBlocksBloomInProcess());
    }

    @Test
    public void avoidProcessNegativeBlockNumber() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 4, dataSource);
        BlocksBloomProcessor blocksBloomProcessor = new BlocksBloomProcessor(blocksBloomStore, world.getBlockStore());

        blocksBloomProcessor.processNewBlockNumber(-2);

        Assertions.assertNull(blocksBloomProcessor.getBlocksBloomInProcess());
    }

    @Test
    public void processFirstNewBlockInSecondRange() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomProcessor blocksBloomProcessor = new BlocksBloomProcessor(blocksBloomStore, world.getBlockStore());

        blocksBloomProcessor.processNewBlockNumber(7);

        BlocksBloom result = blocksBloomProcessor.getBlocksBloomInProcess();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.fromBlock());
        Assertions.assertEquals(5, result.toBlock());

        Assertions.assertTrue(dataSource.keys().isEmpty());
    }

    @Test
    public void processFirstNewBlockInSecondRangeWithOnlyOneBlock() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomProcessor blocksBloomProcessor = new BlocksBloomProcessor(blocksBloomStore, world.getBlockStore());

        blocksBloomProcessor.processNewBlockNumber(6);

        BlocksBloom result = blocksBloomProcessor.getBlocksBloomInProcess();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.fromBlock());
        Assertions.assertEquals(4, result.toBlock());

        Assertions.assertTrue(dataSource.keys().isEmpty());
    }

    @Test
    public void processBlocksInSecondRangeOnly() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomProcessor blocksBloomProcessor = new BlocksBloomProcessor(blocksBloomStore, world.getBlockStore());

        blocksBloomProcessor.processNewBlockNumber(6);
        blocksBloomProcessor.processNewBlockNumber(4);

        BlocksBloom result = blocksBloomProcessor.getBlocksBloomInProcess();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.fromBlock());
        Assertions.assertEquals(4, result.toBlock());

        Assertions.assertTrue(dataSource.keys().isEmpty());
    }

    @Test
    public void processNewBlockTwice() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomProcessor blocksBloomProcessor = new BlocksBloomProcessor(blocksBloomStore, world.getBlockStore());

        blocksBloomProcessor.processNewBlockNumber(4);
        blocksBloomProcessor.processNewBlockNumber(4);

        BlocksBloom result = blocksBloomProcessor.getBlocksBloomInProcess();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.fromBlock());
        Assertions.assertEquals(2, result.toBlock());

        Assertions.assertTrue(dataSource.keys().isEmpty());
    }

    @Test
    public void processBlocksToFillRange() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomProcessor blocksBloomProcessor = new BlocksBloomProcessor(blocksBloomStore, world.getBlockStore());

        blocksBloomProcessor.processNewBlockNumber(4);
        blocksBloomProcessor.processNewBlockNumber(5);

        BlocksBloom result = blocksBloomProcessor.getBlocksBloomInProcess();

        Assertions.assertNull(result);

        Assertions.assertFalse(dataSource.keys().isEmpty());
        Assertions.assertEquals(1, dataSource.keys().size());
    }

    @Test
    public void processBlocksToFillRangeAndStartTheNextOne() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomProcessor blocksBloomProcessor = new BlocksBloomProcessor(blocksBloomStore, world.getBlockStore());

        blocksBloomProcessor.processNewBlockNumber(4);
        blocksBloomProcessor.processNewBlockNumber(6);

        BlocksBloom result = blocksBloomProcessor.getBlocksBloomInProcess();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.fromBlock());
        Assertions.assertEquals(4, result.toBlock());

        Assertions.assertFalse(dataSource.keys().isEmpty());
        Assertions.assertEquals(1, dataSource.keys().size());
    }
}
