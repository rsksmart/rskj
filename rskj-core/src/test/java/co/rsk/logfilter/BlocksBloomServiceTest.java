package co.rsk.logfilter;

import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Blockchain;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 29/09/2020.
 */
public class BlocksBloomServiceTest {
    @Test
    public void processFirstRange() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        CompositeEthereumListener emitter = new CompositeEthereumListener();
        KeyValueDataSource dataSource = new HashMapDB();
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, dataSource);
        BlocksBloomService blocksBloomService = new BlocksBloomService(emitter, blocksBloomStore, world.getBlockStore());

        blocksBloomService.processNewBlock(4);
        blocksBloomService.processNewBlock(6);

        Assert.assertFalse(dataSource.keys().isEmpty());
        Assert.assertEquals(1, dataSource.keys().size());

        Assert.assertNotNull(dataSource.get(longToKey(0)));
    }

    @Test
    public void processFirstRangeUsingEmitter() {
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

        Assert.assertFalse(dataSource.keys().isEmpty());
        Assert.assertEquals(1, dataSource.keys().size());

        Assert.assertNotNull(dataSource.get(longToKey(0)));
    }

    @Test
    public void processFirstAndSecondRangeUsingEmitter() {
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

        Assert.assertFalse(dataSource.keys().isEmpty());
        Assert.assertEquals(2, dataSource.keys().size());

        Assert.assertNotNull(dataSource.get(longToKey(0)));
        Assert.assertNotNull(dataSource.get(longToKey(4)));
    }

    @Test
    public void processOnlySecondRangeUsingEmitter() {
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

        Assert.assertFalse(dataSource.keys().isEmpty());
        Assert.assertEquals(1, dataSource.keys().size());

        Assert.assertNull(dataSource.get(longToKey(0)));
        Assert.assertNotNull(dataSource.get(longToKey(4)));
    }

    private static byte[] longToKey(long value) {
        if (value == 0) {
            return new byte[0];
        }

        return DataWord.valueOf(value).getByteArrayForStorage();
    }
}
