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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class MutableTrieCache implements MutableTrie {

    private final TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

    private MutableTrie trie;

    // We use a single cache to mark both changed elements (new node value or rent paid time) and removed elements.
    // null value means the element has been removed.
    
    /* #mish:  ByteArrayWrapper converts byte[] to an object with size and lexicographic comparison methods
     The cache is implemented as a nested hashmap. 
     * 1st/outer layer key is AccountKeyWrapper: extracts "account key" component (not storage, not code) as a bytearraywrapper
          see getAccountKeyWrapper
     * 2nd or inner map has just the key wrapper (as byteArraywrapper object)
          if the given key is a regular account key (unique size), then both wrappers are the same
     * Instead of base accountKey, the first key could also be the base for the storageRoot  
    */
    
    //#mish: this cache for node values is not used in implementation with rent. remove references after review    
    // private final Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> cache;

    // #mish: Use a combo-cache to track both value and storage rent timestamp 
    // use a ByteBuffer with node rent (8 bytes) + value
    private final Map<ByteArrayWrapper, Map<ByteArrayWrapper, byte[]>> comboCache; 

    // this logs recursive delete operations to be performed at commit time
    private final Set<ByteArrayWrapper> deleteRecursiveLog;

    public MutableTrieCache(MutableTrie parentTrie) {
        trie = parentTrie;   //#mish this is a mutableTrie, not a regular one
        //cache = new HashMap<>(); // cache for value updates and deletions via null value
        comboCache = new HashMap<>(); // combined cache for value and or storage rent updates
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

    // for value.. similar methods for getvaluehash and valuelength later
    @Override
    public byte[] get(byte[] key) {
        //#mish: prior to storage rent, the value stored in 'cache' (nested hashmap) was returned directly i.e. "identity()"
        // with storage rent paid. the node's cached value needs to be separated from rent timestamp
        
        //return  internalGet(key, trie::get, Function.identity()).orElse(null);
        return internalGet(key, trie::get, cachedBytes -> extractValue(cachedBytes)).orElse(null);  
    }

    // #mis: extract node value when stored together with node's rentLastPaidTime in nested hashmap
    private byte[] extractValue(byte[] data){
        ByteBuffer currData = ByteBuffer.wrap(data);
        long currLastRentPaidTime = currData.getLong(); // this is stored first
        byte[] currValue = new byte[data.length - 8]; // whatever is left over is actual node data
        currData.get(currValue);
        return currValue;
    }

    // #mish: gets value from cache (a nested hashMap).. if not cached then use trie.get()
    // modify original to add cache (value or rent) as arg
    private <T> Optional<T> internalGet(
            byte[] key,
            Function<byte[], T> trieRetriever,
            Function<byte[], T> cacheTransformer) {
        // convert key to bytearray object
        ByteArrayWrapper wrapper = new ByteArrayWrapper(key);
        // convert key to account key bytearray object (could be same as above)
        ByteArrayWrapper accountWrapper = getAccountWrapper(wrapper);
        // check if accountkey (for given key) is in cache (a nested hashMap)
        // the (unique or null) return from map.get() will be a inner map containing key-value pairs
        // with distinct keys (wrappers) but the same account wrapper. 
        Map<ByteArrayWrapper, byte[]> accountItems = comboCache.get(accountWrapper);
        // check if the account is marked for deletion
        boolean isDeletedAccount = deleteRecursiveLog.contains(accountWrapper);
        // account not in cache
        if (accountItems == null || !accountItems.containsKey(wrapper)) {
            if (isDeletedAccount) {
                return Optional.empty();
            }
            // uncached account
            return Optional.ofNullable(trieRetriever.apply(key)); //apply the trie method (get, getValueHash, getLastRentPaid etc)
        }
        // if account is in cache, get value for the specific wrapper
        byte[] cacheItem = accountItems.get(wrapper);
        if (cacheItem == null) {
            // deleted account key
            return Optional.empty();
        }
        // cached account key
        return Optional.ofNullable(cacheTransformer.apply(cacheItem)); //in case of value, no transformation needed, byte[] (i.e. identity)
    }

    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        byte[] accountStoragePrefixKey = trieKeyMapper.getAccountStoragePrefixKey(addr);
        ByteArrayWrapper accountWrapper = getAccountWrapper(new ByteArrayWrapper(accountStoragePrefixKey));

        boolean isDeletedAccount = deleteRecursiveLog.contains(accountWrapper);
        Map<ByteArrayWrapper, byte[]> accountItems = comboCache.get(accountWrapper);
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

    // This method returns a wrapper with the same content and size expected for an account key
    // when the key is from the same size than the original wrapper, it returns the same object
    private ByteArrayWrapper getAccountWrapper(ByteArrayWrapper originalWrapper) {
        byte[] key = originalWrapper.getData();
        int size = TrieKeyMapper.domainPrefix().length + TrieKeyMapper.ACCOUNT_KEY_SIZE + TrieKeyMapper.SECURE_KEY_SIZE;
        //System.out.println("size is " + size + "key len " + key.length);
        return key.length == size ? originalWrapper : new ByteArrayWrapper(Arrays.copyOf(key, size));
    }

    @Override
    public void put(byte[] key, byte[] value) {
        put(new ByteArrayWrapper(key), value);
    }

    // This method optimizes cache-to-cache transfers
    /** #mish: Once a node is in the cache, then its rent timestamp is preserved when calling this put(k,v)
    * - This is important, since the original trie::put(k,v) now points to trie::putwithrent(k,v,-1)
          so that would overwrite the rent timestamp with -1
    * - An important concern with this approach is that put(k,v) now require a get (to read and preserve timestamp) 
    * - Rent timestamps get updated only at the very end of transaction execution 
    * */ 
    @Override
    public void put(ByteArrayWrapper wrapper, byte[] value) {
        long newLastRentPaidTime = 0L; // initialized to 0.. we'll check for prior value in cache
        ByteArrayWrapper accountWrapper = getAccountWrapper(wrapper);
        Map<ByteArrayWrapper, byte[]> comboAccountMap = comboCache.computeIfAbsent(accountWrapper, k -> new HashMap<>());
        // with computeIfAbsent(), comboCache.get(accountWrapper) != null.. so only check for the inner Map 
        byte[] currentCachedData = comboCache.get(accountWrapper).get(wrapper);
        if (currentCachedData != null){
            // something already in cache for that wrapper/node.. grab that first. Since value is explicitly passed as argument,
            //  only cached rentPaidTime needs to be preserved. The cached (previous) value will be overwritten
            newLastRentPaidTime = ByteBuffer.wrap(currentCachedData).getLong();
        }    
        // now for the actual put (into the cache)
        if (value != null){
            ByteBuffer buffer = ByteBuffer.allocate(8 + value.length);    
            buffer.putLong(newLastRentPaidTime);
            buffer.put(value);
            comboAccountMap.put(wrapper, buffer.array());
        }else { // marked for deletion
            comboAccountMap.put(wrapper, value); // put the null value in the hashmap and discard rent paid time
        }
        
    }

    @Override
    public void put(String key, byte[] value) {
        byte[] keybytes = key.getBytes(StandardCharsets.UTF_8);
        put(keybytes, value);
    }

    // #mish: this allows updating value at the same time as rent
    // if updating rent alone, then pass current value
    @Override
    public void putWithRent(byte[] key, byte[] value, long newLastRentPaidTime) {
        putWithRent(new ByteArrayWrapper(key), value, newLastRentPaidTime);
    }  
    
    public void putWithRent(String key, byte[] value, long newLastRentPaidTime) {
        byte[] keybytes = key.getBytes(StandardCharsets.UTF_8);
        putWithRent(keybytes, value, newLastRentPaidTime);
    }

    public void putWithRent(ByteArrayWrapper wrapper, byte[] value, long newLastRentPaidTime){
        if (value == null){ // alternative is to add it to delete cache.. just staying close to orig imlementation
            ByteArrayWrapper accountWrapper = getAccountWrapper(wrapper);
            Map<ByteArrayWrapper, byte[]> accountMap = comboCache.computeIfAbsent(accountWrapper, k -> new HashMap<>());
            accountMap.put(wrapper, value);
        } else {
            ByteArrayWrapper accountWrapper = getAccountWrapper(wrapper);
            ByteBuffer buffer = ByteBuffer.allocate(8 + value.length);
            buffer.putLong(newLastRentPaidTime);
            buffer.put(value);
            Map<ByteArrayWrapper, byte[]> comboAccountMap = comboCache.computeIfAbsent(accountWrapper, k -> new HashMap<>());
            // Since node value and rentLastPaidTime are explicitly passed as arguments, 
            //  there is no need to check to see if some value or rent time already in the cache, okay to overwrite
            comboAccountMap.put(wrapper, buffer.array());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////
    // The semantic of implementations is special, and not the same of the MutableTrie
    // It is DELETE ON COMMIT, which means that changes are not applies until commit()
    // is called, and changes are applied last.
    ////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void deleteRecursive(byte[] key) {
        // Can there be wrongly unhandled interactions between put() and deleteRecurse()
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
        // cache.remove(wrap);
        comboCache.remove(wrap);
    }

    @Override
    public void commit() {
        // in case something was deleted and then put again, we first have to delete all the previous data
        deleteRecursiveLog.forEach(item -> trie.deleteRecursive(item.getData()));
        /*cache.forEach((accountKey, accountData) -> {
            if (accountData != null) {
                // cached account
                accountData.forEach((realKey, value) -> this.trie.put(realKey, value));
            }
        });
        */
        comboCache.forEach((accountKey, accountData) -> {
            if (accountData != null) {
                // cached account
                accountData.forEach((realKey, data) -> {
                    if (data == null){
                        this.trie.put(realKey.getData(), data);
                    } else{
                        ByteBuffer currData = ByteBuffer.wrap(data);
                        long currLastRentPaidTime = currData.getLong();
                        byte[] currValue = new byte[data.length - 8];
                        currData.get(currValue);
                        this.trie.putWithRent(realKey.getData(), currValue, currLastRentPaidTime);
                    }
                });
            }    

        });

        deleteRecursiveLog.clear();
        // cache.clear();
        comboCache.clear();
    }

    @Override
    public void save() {
        commit();
        trie.save();
    }

    @Override
    public void rollback() {
        // cache.clear();
        comboCache.clear();
        deleteRecursiveLog.clear();
    }

    @Override
    public Set<ByteArrayWrapper> collectKeys(int size) {
        Set<ByteArrayWrapper> parentSet = trie.collectKeys(size);

        // all cached items to be transferred to parent
        comboCache.forEach((accountKey, account) ->
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
        //if (!cache.isEmpty()) {
        //    throw new IllegalStateException();
        //}

        if (!comboCache.isEmpty()) {
            throw new IllegalStateException();
        }

        if (!deleteRecursiveLog.isEmpty()) {
            throw new IllegalStateException();
        }
    }

    @Override
    public Uint24 getValueLength(byte[] key) {
        return internalGet(key,  trie::getValueLength, cachedBytes -> new Uint24(extractValue(cachedBytes).length)).orElse(Uint24.ZERO);
    }

    @Override
    public Optional<Keccak256> getValueHash(byte[] key) {
        return internalGet(key,
                keyB -> trie.getValueHash(keyB).orElse(null),
                cachedBytes -> new Keccak256(Keccak256Helper.keccak256(extractValue(cachedBytes))));
    }

    public long getLastRentPaidTime(byte[] key) {
        return internalGet(key,  trie::getLastRentPaidTime, cachedBytes -> ByteBuffer.wrap(cachedBytes).getLong()).orElse(0L);
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
