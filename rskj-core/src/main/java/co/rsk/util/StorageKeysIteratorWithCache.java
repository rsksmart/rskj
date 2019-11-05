package co.rsk.util;

import co.rsk.core.RskAddress;
import co.rsk.trie.TrieKeySlice;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * An iterator class to allow iterating on keys stored in the Trie, taking into account the changes in cache.
 * Such an iterator is returned by the getStorageKeys() method
 */
public class StorageKeysIteratorWithCache  implements Iterator<DataWord> {
    private final Iterator<DataWord> keysIterator;
    private final Map<ByteArrayWrapper, byte[]> accountItems;
    private final RskAddress address;
    private final int storageKeyOffset = (
            TrieKeyMapper.domainPrefix().length +
                    TrieKeyMapper.SECURE_ACCOUNT_KEY_SIZE +
                    TrieKeyMapper.storagePrefix().length +
                    TrieKeyMapper.SECURE_KEY_SIZE)
            * Byte.SIZE;
    private final TrieKeyMapper trieKeyMapper;
    private DataWord currentStorageKey;
    private Iterator<Map.Entry<ByteArrayWrapper, byte[]>> accountIterator;

    public StorageKeysIteratorWithCache(
            Iterator<DataWord> keysIterator,
            Map<ByteArrayWrapper, byte[]> accountItems,
            RskAddress addr,
            TrieKeyMapper trieKeyMapper) {
        this.keysIterator = keysIterator;
        this.accountItems = new HashMap<>(accountItems);
        this.address = addr;
        this.trieKeyMapper = trieKeyMapper;
    }

    @Override
    public boolean hasNext() {
        if (currentStorageKey != null) {
            return true;
        }

        while (keysIterator.hasNext()) {
            DataWord item = keysIterator.next();
            ByteArrayWrapper fullKey = getCompleteKey(item);
            if (accountItems.containsKey(fullKey)) {
                byte[] value = accountItems.remove(fullKey);
                if (value == null){
                    continue;
                }
            }
            currentStorageKey = item;
            return true;
        }

        if (accountIterator == null) {
            accountIterator = accountItems.entrySet().iterator();
        }

        while (accountIterator.hasNext()) {
            Map.Entry<ByteArrayWrapper, byte[]> entry = accountIterator.next();
            byte[] key = entry.getKey().getData();
            if (entry.getValue() != null && key.length * Byte.SIZE > storageKeyOffset) {
                // cached account key
                currentStorageKey = getPartialKey(key);
                return true;
            }
        }

        return false;
    }

    private DataWord getPartialKey(byte[] key) {
        TrieKeySlice nodeKey = TrieKeySlice.fromKey(key);
        byte[] storageExpandedKeySuffix = nodeKey.slice(storageKeyOffset, nodeKey.length()).encode();
        return DataWord.valueOf(storageExpandedKeySuffix);
    }

    private ByteArrayWrapper getCompleteKey(DataWord subkey) {
        byte[] secureKeyPrefix = trieKeyMapper.getAccountStorageKey(address, subkey);
        return new ByteArrayWrapper(secureKeyPrefix);
    }

    @Override
    public DataWord next() {
        if (currentStorageKey == null && !hasNext()) {
            throw new NoSuchElementException();
        }

        DataWord next = currentStorageKey;
        currentStorageKey = null;
        return next;
    }
}
