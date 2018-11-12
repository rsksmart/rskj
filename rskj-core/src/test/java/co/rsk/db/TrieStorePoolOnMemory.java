package co.rsk.db;

import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;

import java.util.HashMap;
import java.util.Map;

public class TrieStorePoolOnMemory implements TrieStore.Pool {

    private final StoreProvider storeProvider;
    Map<String, TrieStore> pool = new HashMap<>();

    public TrieStorePoolOnMemory() {
        this (HashMapDB::new);
    }

    public TrieStorePoolOnMemory(StoreProvider storeProvider) {
        this.storeProvider = storeProvider;
    }

    @Override
    public TrieStore getInstanceFor(String name) {
        return pool.computeIfAbsent(name, trieStoreName -> new TrieStoreImpl(storeProvider.getInstance()));
    }

    @Override
    public boolean existsInstanceFor(String name) {
        return pool.containsKey(name);
    }

    public void closeInstanceFor(String name) {

    }

    @Override
    public void destroyInstanceFor(String name) {
        if (!pool.containsKey(name)) {
            return;
        }

        pool.remove(name);
    }

    public interface StoreProvider {
        KeyValueDataSource getInstance();
    }
}
