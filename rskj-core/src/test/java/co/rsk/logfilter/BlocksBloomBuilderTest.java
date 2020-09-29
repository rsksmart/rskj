package co.rsk.logfilter;

import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 29/09/2019.
 */
public class BlocksBloomBuilderTest {
    @Test
    public void noBlocksBloomInProcessAtTheBeginning() {
        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(64, 0, null);
        BlocksBloomBuilder blocksBloomBuilder = new BlocksBloomBuilder(blocksBloomStore, null);

        Assert.assertNull(blocksBloomBuilder.getBlocksBloomInProcess());
    }

    @Test
    public void processFirstNewBlock() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, null);
        BlocksBloomBuilder blocksBloomBuilder = new BlocksBloomBuilder(blocksBloomStore, world.getBlockStore());

        blocksBloomBuilder.processNewBlockNumber(4);

        BlocksBloom result = blocksBloomBuilder.getBlocksBloomInProcess();

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.fromBlock());
        Assert.assertEquals(2, result.toBlock());
    }

    @Test
    public void processNewBlockTwice() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, 8, false, false);

        BlocksBloomStore blocksBloomStore = new BlocksBloomStore(4, 2, null);
        BlocksBloomBuilder blocksBloomBuilder = new BlocksBloomBuilder(blocksBloomStore, world.getBlockStore());

        blocksBloomBuilder.processNewBlockNumber(4);
        blocksBloomBuilder.processNewBlockNumber(4);

        BlocksBloom result = blocksBloomBuilder.getBlocksBloomInProcess();

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.fromBlock());
        Assert.assertEquals(2, result.toBlock());
    }
}
