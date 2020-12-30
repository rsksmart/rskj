package co.rsk.db;

import co.rsk.crypto.Keccak256;
import co.rsk.trie.TrieNodeData;
import org.ethereum.crypto.Keccak256Helper;

public class TrieNodeDataFromCache implements TrieNodeData {
    byte[] cacheData;
    long timestamp;
    
    public TrieNodeDataFromCache(byte[] cacheData) {
        this.cacheData = cacheData;
    }
    public TrieNodeDataFromCache(byte[] cacheData,long timestamp) {
        this.cacheData = cacheData;
        this.timestamp = timestamp;
    }

    @Override
    public int getValueLength() {
        return cacheData.length;
    }

    @Override
    public byte[] getValue() {
        return cacheData;
    }

    @Override
    public long getChildrenSize() {
        return 0;
    }

    @Override
    public boolean isNew() {
        return true;
    }

    @Override
    public long getLastRentPaidTime() {
        return timestamp;
    }

    @Override
    public Keccak256 getValueHash() {
        return new Keccak256(Keccak256Helper.keccak256(cacheData));
    }
}
