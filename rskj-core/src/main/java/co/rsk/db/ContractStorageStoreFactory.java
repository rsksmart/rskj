package co.rsk.db;

import co.rsk.trie.TrieStore;
import static org.ethereum.util.ByteUtil.toHexString;

/**
 * Created by Angel on 11/6/2018.
 */
public class ContractStorageStoreFactory {
    private TrieStore.Pool pool;

    public ContractStorageStoreFactory(TrieStore.Pool pool) {
        this.pool = pool;
    }

    public TrieStore getTrieStore(byte[] address) {
        return this.pool.getInstanceFor(getStorageNameForAddress(address));
    }

    private static String getUnifiedStorageName() {
        return "contracts-storage";
    }

    private static String getStorageNameForAddress(byte[] address) {
        return "details-storage/" + toHexString(address);
    }
}
