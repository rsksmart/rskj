package co.rsk.net;

import co.rsk.crypto.Sha3Hash;
import org.ethereum.core.Block;
import org.ethereum.db.ByteArrayWrapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by ajlopez on 17/06/2017.
 */
public class BlockCache extends LinkedHashMap<Sha3Hash, Block> {
    private int cacheSize;

    public BlockCache(int cacheSize) {
        super(cacheSize, 0.75f, true);
        this.cacheSize = cacheSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<Sha3Hash, Block> eldest) {
        return size() > this.cacheSize;
    }

    public void removeBlock(Block block) {
        this.remove(block.getHash());
    }

    public void addBlock(Block block) {
        this.put(block.getHash(), block);
    }

    public Block getBlockByHash(Sha3Hash hash) {
        return this.get(hash);
    }
}
