package co.rsk.trie;

import org.ethereum.datasource.HashMapDB;

public class TmpTrieStoreFactory {

    public static TrieStoreImpl newInstance() {
        return new TrieStoreImpl(new HashMapDB(),null);
    }
}
