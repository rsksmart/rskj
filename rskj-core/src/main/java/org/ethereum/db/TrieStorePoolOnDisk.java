package org.ethereum.db;

import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;

import org.ethereum.datasource.DataSourcePool;

/**
 * Created by ajlopez on 06/11/2018.
 */
public class TrieStorePoolOnDisk implements TrieStore.Pool {
    private String databaseDir;

    public TrieStorePoolOnDisk(String databaseDir) {
        this.databaseDir = databaseDir;
    }

    public TrieStore getInstanceFor(String name) {
        return new TrieStoreImpl(DataSourcePool.levelDbByName(name, this.databaseDir));
    }

    public boolean existsInstanceFor(String name) {
        return DataSourcePool.levelDbExists(name, this.databaseDir);
    }

    public void destroyInstanceFor(String name) {
        DataSourcePool.levelDbDestroy(name, this.databaseDir);
    }

    public void closeInstanceFor(String name) {
        DataSourcePool.closeDataSource(name);
    }
}
