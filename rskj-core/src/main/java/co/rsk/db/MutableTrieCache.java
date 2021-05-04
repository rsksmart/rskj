/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class MutableTrieCache implements MutableTrie {

    private final TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

    private MutableTrie trie;
    // We use a single cache to mark both changed elements and removed elements.
    // null value means the element has been removed.
    private final Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> cache;

    // this logs recursive delete operations to be performed at commit time
    private final Set<ByteArrayWrapper> deleteRecursiveLog;

    public MutableTrieCache(MutableTrie parentTrie) {
        trie = parentTrie;
        cache = new HashMap<>();
        deleteRecursiveLog = new HashSet<>();
    }

    @Override
    public Trie getTrie() {
        assertNoCache();
        return trie.getTrie();
    }

    @Override
    public Keccak256 getHash() {
        assertNoCache();
        return trie.getHash();
    }

    @Override
    public byte[] get(byte[] key) {
        return internalGet(key, trie::get, Function.identity()).orElse(null);
    }

    private <T> Optional<T> internalGet(
            byte[] key,
            Function<byte[], T> trieRetriever,
            Function<byte[], T> cacheTransformer) {
        ByteArrayWrapper wrapper = new ByteArrayWrapper(key);
        ByteArrayWrapper accountWrapper = getAccountWrapper(wrapper);

        Map<ByteArrayWrapper, byte[]> accountItems = cache.get(accountWrapper);
        boolean isDeletedAccount = deleteRecursiveLog.contains(accountWrapper);
        if (accountItems == null || !accountItems.containsKey(wrapper)) {
            if (isDeletedAccount) {
                return Optional.empty();
            }
            // uncached account
            return Optional.ofNullable(trieRetriever.apply(key));
        }

        byte[] cacheItem = accountItems.get(wrapper);
        if (cacheItem == null) {
            // deleted account key
            return Optional.empty();
        }

        // cached account key
        return Optional.ofNullable(cacheTransformer.apply(cacheItem));
    }

    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        byte[] accountStoragePrefixKey = trieKeyMapper.getAccountStoragePrefixKey(addr);
        ByteArrayWrapper accountWrapper = getAccountWrapper(new ByteArrayWrapper(accountStoragePrefixKey));

        boolean isDeletedAccount = deleteRecursiveLog.contains(accountWrapper);
        Map<ByteArrayWrapper, byte[]> accountItems = cache.get(accountWrapper);
        if (accountItems == null && isDeletedAccount) {
            return Collections.emptyIterator();
        }

        if (isDeletedAccount) {
            // lower level is deleted, return cached items
            return new StorageKeysIterator(Collections.emptyIterator(), accountItems, addr, trieKeyMapper);
        }

        Iterator<DataWord> storageKeys = trie.getStorageKeys(addr);
        if (accountItems == null) {
            // uncached account
            return storageKeys;
        }

        return new StorageKeysIterator(storageKeys, accountItems, addr, trieKeyMapper);
    }

    @Override
    public List<Trie> getNodes(byte[] key) {
        return trie.getNodes(key);
    }

    // This method returns a wrapper with the same content and size expected for a account key
    // when the key is from the same size than the original wrapper, it returns the same object
    private ByteArrayWrapper getAccountWrapper(ByteArrayWrapper originalWrapper) {
        byte[] key = originalWrapper.getData();
        int size = TrieKeyMapper.domainPrefix().length + TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.SECURE_KEY_SIZE;
        return key.length == size ? originalWrapper : new ByteArrayWrapper(Arrays.copyOf(key, size));
    }

    @Override
    public void put(byte[] key, byte[] value) {
        put(new ByteArrayWrapper(key), value);
    }

    // This method optimizes cache-to-cache transfers
    @Override
    public void put(ByteArrayWrapper wrapper, byte[] value) {
        // If value==null, do we have the choice to either store it
        // in cache with null or in deleteCache. Here we have the choice to
        // to add it to cache with null value or to deleteCache.
        ByteArrayWrapper accountWrapper = getAccountWrapper(wrapper);
        Map<ByteArrayWrapper, byte[]> accountMap = cache.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        accountMap.put(wrapper, value);
    }

    @Override
    public void put(String key, byte[] value) {
        byte[] keybytes = key.getBytes(StandardCharsets.UTF_8);
        put(keybytes, value);
    }

    ////////////////////////////////////////////////////////////////////////////////////
    // The semantic of implementations is special, and not the same of the MutableTrie
    // It is DELETE ON COMMIT, which means that changes are not applies until commit()
    // is called, and changes are applied last.
    ////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void deleteRecursive(byte[] key) {
        // Can there be wrongly unhandled interactions interactions between put() and deleteRecurse()
        // In theory, yes. In practice, never.
        // Suppose that a contract X calls a contract S.
        // Contract S calls itself with CALL.
        // Contract S suicides with SUICIDE opcode.
        // This causes a return to prev contract.
        // But the SUICIDE DOES NOT cause the storage keys to be removed YET.
        // Now parent contract S is still running, and it then can create a new storage cell
        // with SSTORE. This will be stored in the cache as a put(). The cache later receives a
        // deleteRecursive, BUT NEVER IN THE OTHER order.
        // See TransactionExecutor.finalization(), when it iterates the list with getDeleteAccounts().forEach()
        ByteArrayWrapper wrap = new ByteArrayWrapper(key);
        deleteRecursiveLog.add(wrap);
        cache.remove(wrap);
    }

    @Override
    public void commit() {
        // in case something was deleted and then put again, we first have to delete all the previous data
        deleteRecursiveLog.forEach(item -> trie.deleteRecursive(item.getData()));
        cache.forEach((accountKey, accountData) -> {
            if (accountData != null) {
                // cached account
                accountData.forEach((realKey, value) -> this.trie.put(realKey, value));
            }
        });

        deleteRecursiveLog.clear();
        cache.clear();
    }

    @Override
    public void save() {
        commit();
        trie.save();
    }

    @Override
    public void rollback() {
        cache.clear();
        deleteRecursiveLog.clear();
    }

    @Override
    public Set<ByteArrayWrapper> collectKeys(int size) {
        Set<ByteArrayWrapper> parentSet = trie.collectKeys(size);

        // all cached items to be transferred to parent
        cache.forEach((accountKey, account) ->
              account.forEach((realKey, value) -> {
                  if (size == Integer.MAX_VALUE || realKey.getData().length == size) {
                      if (this.get(realKey.getData()) == null) {
                          parentSet.remove(realKey);
                      } else {
                          parentSet.add(realKey);
                      }
                  }
              })
        );
        return parentSet;
    }

    private void assertNoCache() {
        if (!cache.isEmpty()) {
            throw new IllegalStateException();
        }

        if (!deleteRecursiveLog.isEmpty()) {
            throw new IllegalStateException();
        }
    }

    @Override
    public Uint24 getValueLength(byte[] key) {
        return internalGet(key, trie::getValueLength, cachedBytes -> new Uint24(cachedBytes.length)).orElse(Uint24.ZERO);
    }

    @Override
    public Optional<Keccak256> getValueHash(byte[] key) {
        return internalGet(key,
                keyB -> trie.getValueHash(keyB).orElse(null),
                cachedBytes -> new Keccak256(Keccak256Helper.keccak256(cachedBytes)));
    }

    private static class StorageKeysIterator implements Iterator<DataWord> {
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

        StorageKeysIterator(
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
}
