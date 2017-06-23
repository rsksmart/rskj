package co.rsk.net;

import org.ethereum.core.Block;
import org.ethereum.db.ByteArrayWrapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by ajlopez on 17/06/2017.
 */
public class BlockCache extends LinkedHashMap<ByteArrayWrapper, Block> {
    private int cacheSize;

    public BlockCache(int cacheSize) {
        super(cacheSize, 0.75f, true);
        this.cacheSize = cacheSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<ByteArrayWrapper, Block> eldest) {
        return size() > this.cacheSize;
    }

    public void removeBlock(Block block) {
        ByteArrayWrapper key = new ByteArrayWrapper(block.getHash());

        this.remove(key);
    }

    public void addBlock(Block block) {
        ByteArrayWrapper key = new ByteArrayWrapper(block.getHash());

        this.put(key, block);
    }

    public Block getBlockByHash(byte[] hash) {
        ByteArrayWrapper key = new ByteArrayWrapper(hash);

        return this.get(key);
    }
}
