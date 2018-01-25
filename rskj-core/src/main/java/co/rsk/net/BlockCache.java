package co.rsk.net;

import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by ajlopez on 17/06/2017.
 */
public class BlockCache extends LinkedHashMap<Keccak256, Block> {
    private int cacheSize;

    public BlockCache(int cacheSize) {
        super(cacheSize, 0.75f, true);
        this.cacheSize = cacheSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<Keccak256, Block> eldest) {
        return size() > this.cacheSize;
    }

    public void removeBlock(Block block) {
        this.remove(block.getHash());
    }

    public void addBlock(Block block) {
        this.put(block.getHash(), block);
    }

    public Block getBlockByHash(Keccak256 hash) {
        return this.get(hash);
    }
}
