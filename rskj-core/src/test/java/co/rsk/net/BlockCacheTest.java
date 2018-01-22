package co.rsk.net;

import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.core.Block;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class BlockCacheTest {

    private BlockGenerator blockGenerator;

    @Before
    public void init() {
        this.blockGenerator = new BlockGenerator();
    }

    @Test
    public void testAddRemoveAndGet() {
        Block block = this.blockGenerator.createChildBlock(this.blockGenerator.getGenesisBlock());
        BlockCache cache = new BlockCache(10);
        cache.addBlock(block);
        Assert.assertEquals(1, cache.size());
        Assert.assertEquals(block.getHash(), cache.getBlockByHash(block.getHash()).getHash());
        cache.removeBlock(block);
        Assert.assertEquals(0, cache.size());
        Assert.assertEquals(null, cache.getBlockByHash(block.getHash()));
    }

    @Test
    public void testRemoveEldestEntry() {
        List<Block> blocks = this.blockGenerator.getBlockChain(this.blockGenerator.getGenesisBlock(), 11);
        BlockCache cache = new BlockCache(10);
        for (int i = 0; i < blocks.size();i++) {
            cache.addBlock(blocks.get(i));
        }
        Assert.assertEquals(null, cache.getBlockByHash(blocks.get(0).getHash()));
        for (int i = 1; i < blocks.size();i++) {
            Assert.assertEquals(blocks.get(i), cache.getBlockByHash(blocks.get(i).getHash()));
        }
    }
}
