package co.rsk.logfilter;

import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Blockchain;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.util.TestInjectorUtil;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 29/09/2020.
 */
class BlocksBloomServiceTest {

    @BeforeEach
    public void setUp() {
        TestInjectorUtil.initEmpty();
    }

    @Test
    void processFirstRange() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        CompositeEthereumListener emitter = new CompositeEthereumListener();
        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomService blocksBloomService = new BlocksBloomService(emitter, blocksBloomStore, world.getBlockStore());

        blocksBloomService.processNewBlock(4);
        blocksBloomService.processNewBlock(6);

        Assertions.assertFalse(dataSource.keys().isEmpty());
        Assertions.assertEquals(1, dataSource.keys().size());

        Assertions.assertNotNull(dataSource.get(longToKey(0)));
    }

    @Test
    void processFirstRangeUsingEmitter() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        CompositeEthereumListener emitter = new CompositeEthereumListener();
        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomService blocksBloomService = new BlocksBloomService(emitter, blocksBloomStore, world.getBlockStore());

        blocksBloomService.start();
        emitter.onBlock(blockchain.getBlockByNumber(4), null);
        emitter.onBlock(blockchain.getBlockByNumber(6), null);
        blocksBloomService.stop();

        Assertions.assertFalse(dataSource.keys().isEmpty());
        Assertions.assertEquals(1, dataSource.keys().size());

        Assertions.assertNotNull(dataSource.get(longToKey(0)));
    }

    @Test
    void processFirstAndSecondRangeUsingEmitter() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 10, false, false);

        CompositeEthereumListener emitter = new CompositeEthereumListener();
        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomService blocksBloomService = new BlocksBloomService(emitter, blocksBloomStore, world.getBlockStore());

        blocksBloomService.start();
        emitter.onBlock(blockchain.getBlockByNumber(4), null);
        emitter.onBlock(blockchain.getBlockByNumber(6), null);
        emitter.onBlock(blockchain.getBlockByNumber(9), null);
        blocksBloomService.stop();

        Assertions.assertFalse(dataSource.keys().isEmpty());
        Assertions.assertEquals(2, dataSource.keys().size());

        Assertions.assertNotNull(dataSource.get(longToKey(0)));
        Assertions.assertNotNull(dataSource.get(longToKey(4)));
    }

    @Test
    void processOnlySecondRangeUsingEmitter() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 10, false, false);

        CompositeEthereumListener emitter = new CompositeEthereumListener();
        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomService blocksBloomService = new BlocksBloomService(emitter, blocksBloomStore, world.getBlockStore());

        blocksBloomService.start();
        emitter.onBlock(blockchain.getBlockByNumber(9), null);
        blocksBloomService.stop();

        Assertions.assertFalse(dataSource.keys().isEmpty());
        Assertions.assertEquals(1, dataSource.keys().size());

        Assertions.assertNull(dataSource.get(longToKey(0)));
        Assertions.assertNotNull(dataSource.get(longToKey(4)));
    }

    private static byte[] longToKey(long value) {
        if (value == 0) {
            return new byte[0];
        }

        return DataWord.valueOf(value).getByteArrayForStorage();
    }
}
