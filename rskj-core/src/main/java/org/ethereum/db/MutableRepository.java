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

package org.ethereum.db;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.*;

public class MutableRepository implements Repository {
    private static final Logger logger = LoggerFactory.getLogger("repository");
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final byte[] ONE_BYTE_ARRAY = new byte[] { 0x01 };

    private final TrieKeyMapper trieKeyMapper;
    private MutableTrie trie;
    private boolean closed;

    public MutableRepository(Trie atrie) {
        this(new MutableTrieImpl(atrie));
    }

    public MutableRepository(MutableTrie mutableTrie) {
        this.trieKeyMapper = new TrieKeyMapper();
        this.trie = mutableTrie;
    }

    @Override
    public MutableTrie getMutableTrie() {
        return trie;
    }

    @Override
    public synchronized AccountState createAccount(RskAddress addr) {
        AccountState accountState = new AccountState();
        updateAccountState(addr, accountState);
        return accountState;
    }

    @Override
    public synchronized void setupContract(RskAddress addr) {
        byte[] prefix = trieKeyMapper.getAccountStoragePrefixKey(addr);
        this.trie.put(prefix, ONE_BYTE_ARRAY);
    }

    private byte[] getAccountData(RskAddress addr) {
        return this.trie.get(trieKeyMapper.getAccountKey(addr));
    }

    @Override
    public synchronized boolean isExist(RskAddress addr) {
        // Here we assume size !=0 means the account exists
        return this.trie.getValueLength(trieKeyMapper.getAccountKey(addr)) > 0;
    }

    @Override
    public synchronized AccountState getAccountState(RskAddress addr) {
        AccountState result = null;
        byte[] accountData = getAccountData(addr);

        // If there is no account it returns null
        if (accountData != null && accountData.length != 0) {
            result = new AccountState(accountData);
        }
        return result;
    }

    @Override
    public synchronized void delete(RskAddress addr) {
        this.trie.deleteRecursive(trieKeyMapper.getAccountKey(addr));
    }

    @Override
    public synchronized void hibernate(RskAddress addr) {
        AccountState account = getAccountStateOrCreateNew(addr);

        account.hibernate();
        updateAccountState(addr, account);
    }

    @Override
    public void setNonce(RskAddress addr,BigInteger nonce) {
        AccountState account = getAccountStateOrCreateNew(addr);

        account.setNonce(nonce);
        updateAccountState(addr, account);
    }

    @Override
    public synchronized BigInteger increaseNonce(RskAddress addr) {
        AccountState account = getAccountStateOrCreateNew(addr);

        account.incrementNonce();
        updateAccountState(addr, account);
        return account.getNonce();
    }

    @Override
    public synchronized BigInteger getNonce(RskAddress addr) {
        // Why would getNonce create an Account in the repository? The semantic of a get()
        // is clear: do not change anything!
        AccountState account = getAccountState(addr);
        if (account==null) {
            return BigInteger.ZERO;
        }
        return account.getNonce();
    }

    @Override
    public synchronized void saveCode(RskAddress addr, byte[] code) {
        byte[] key = trieKeyMapper.getCodeKey(addr);
        this.trie.put(key,code);

        boolean accountExists = isExist(addr);
        if (code == null || code.length==0) {
            if (!accountExists) {
                return;
            }
        }

        if (!accountExists) {
            createAccount(addr);
        }
    }

    @Override
    public synchronized byte[] getCodeHash(RskAddress addr) {
        AccountState  account = getAccountState(addr);
        if (account == null || account.isHibernated()) {
            return null;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);
        return this.trie.getValueHash(key);
    }

    @Override
    public synchronized int getCodeLength(RskAddress addr) {
        AccountState  account = getAccountState(addr);
        if (account == null || account.isHibernated()) {
            return 0;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);
        return this.trie.getValueLength(key);
    }


    @Override
    public synchronized byte[] getCode(RskAddress addr) {
        if (!isExist(addr)) {
            return EMPTY_BYTE_ARRAY;
        }

        AccountState  account = getAccountState(addr);

        if (account.isHibernated()) {
            return EMPTY_BYTE_ARRAY;
        }
        byte[] key = trieKeyMapper.getCodeKey(addr);
        return this.trie.get(key);
    }

    @Override
    public boolean isContract(RskAddress addr) {
        byte[] prefix = trieKeyMapper.getAccountStoragePrefixKey(addr);
        return this.trie.get(prefix) != null;
    }

    @Override
    public synchronized void addStorageRow(RskAddress addr, DataWord key, DataWord value) {
        // This is important: DataWords are stored stripping leading zeros.
        addStorageBytes(addr,key,value.getByteArrayForStorage());
    }

    @Override
    public byte[] getStorageStateRoot(RskAddress addr) {
        byte[] prefix = trieKeyMapper.getAccountStoragePrefixKey(addr);

        // The value should be ONE_BYTE_ARRAY, but we don't need to check
        // nothing else could be there. right?
        Trie storageRootNode = this.trie.getTrie().find(prefix);
        if (storageRootNode == null) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        // Now it's a bit tricky what to return: if I return the storageRootNode hash
        // then it's counting the "0x01" value, so the try one gets will never match the
        // trie one gets if creating the trie without any other data. Unless the PDV trie
        // is used. The best we can do is to return storageRootNode  hash
        return storageRootNode.getHash().getBytes();
    }

    @Override
    public boolean contractHasStorage(RskAddress addr) {
        // Having a storage root node allows us to do it simpler than this:
        // byte[] triekey = getAccountKey(addr);
        // return this.trie.getTrie().hasDataWithPrefix(triekey);
        // We can just ask for the state root node.
        byte[] prefix = trieKeyMapper.getAccountStoragePrefixKey(addr);

        // The value should be ONE_BYTE_ARRAY, but we don't need to check
        // nothing else could be there. right?
        return this.trie.get(prefix) != null;


    }

    @Override
    public synchronized void addStorageBytes(RskAddress addr, DataWord key, byte[] value) {
        // This should not happen in production because contracts are created
        // before storage cells are added to them.
        // But it happens in Repository tests, that create only storage row cells.
        if (!isExist(addr)) {
            createAccount(addr);
            setupContract(addr);
        }

        byte[] triekey = trieKeyMapper.getAccountStorageKey(addr, key.getData());

        // Special case: if the value is an empty vector, we pass "null" which
        // commands the trie to remove the item. Note that if the call comes
        // from addStorageRow(), this method will already have replaced 0 by null,
        // so the conversion here only applies if this is called directly.
        // If suppose this only occurs in tests, but it can also occur in precompiled
        // contracts that store data directly using this method.
        if (value == null || value.length == 0) {
            this.trie.put(triekey, null);
        } else {
            this.trie.put(triekey, value);
        }
    }

    // Returns null if the key doesn't exist
    @Override
    public synchronized DataWord getStorageValue(RskAddress addr, DataWord key) {
        byte[] triekey = trieKeyMapper.getAccountStorageKey(addr, key.getData());
        byte[] value = this.trie.get(triekey);
        if (value == null) {
            return null;
        }

        DataWord dw = DataWord.valueOf(value);
        // Creates a new copy to prevent external modification of cached values
        return dw;
    }

    @Override
    public synchronized byte[] getStorageBytes(RskAddress addr, DataWord key) {
        byte[] triekey = trieKeyMapper.getAccountStorageKey(addr, key.getData());
        return this.trie.get(triekey);
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        byte[] accountStorageKey = trieKeyMapper.getAccountStoragePrefixKey(addr);
        Trie storageTrie = this.trie.getTrie().find(accountStorageKey);

        if (storageTrie != null) {
            Iterator<Trie.IterationElement> storageIterator = storageTrie.getInOrderIterator();
            return new Iterator<DataWord>() {
                DataWord currentStorageKey;

                @Override
                public boolean hasNext() {
                    if (currentStorageKey != null) {
                        return true;
                    }
                    while (storageIterator.hasNext()) {
                        TrieKeySlice nodeKey = storageIterator.next().getNodeKey();
                        int nodeKeyLength = nodeKey.length();
                        if (nodeKeyLength == (TrieKeyMapper.STORAGE_PREFIX.length + TrieKeyMapper.SECURE_KEY_SIZE + DataWord.BYTES) * Byte.SIZE - 1) { // -1 b/c the first bit is implicit in the storage node
                            byte[] storageExpandedKeySuffix = nodeKey.slice(nodeKeyLength - DataWord.BYTES * Byte.SIZE, nodeKeyLength).encode();
                            currentStorageKey = DataWord.valueOf(storageExpandedKeySuffix);
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public DataWord next() {
                    DataWord next = currentStorageKey;
                    currentStorageKey = null;
                    return next;
                }
            };
        }
        return Collections.emptyIterator();
    }

    @Override
    public int getStorageKeysCount(RskAddress addr) {
        // FIXME(diegoll): I think it's kind of insane to iterate the whole tree looking for storage keys for this address
        //  I think we can keep a counter for the keys, using the find function for detecting duplicates and so on
        int storageKeysCount = 0;
        Iterator<DataWord> keysIterator = getStorageKeys(addr);
        for(;keysIterator.hasNext(); keysIterator.next()) {
            storageKeysCount ++;
        }
        return storageKeysCount;
    }

    @Override
    public synchronized Coin getBalance(RskAddress addr) {
        AccountState account = getAccountState(addr);
        //return (account == null) ? new Coin.ZERO : account.getBalance();
        return (account == null) ? new Coin(BigInteger.ZERO): account.getBalance();
    }

    @Override
    public synchronized Coin addBalance(RskAddress addr, Coin value) {
        AccountState account = getAccountStateOrCreateNew(addr);

        Coin result = account.addToBalance(value);
        updateAccountState(addr, account);

        return result;
    }

    // This method should be used only for testing. It will retrieve the
    // account keys only in testing mode.
    @Override
    public synchronized Set<RskAddress> getAccountsKeys() {
        Set<RskAddress> result = new HashSet<>();
        this.trie.commit(); //TODO(diegoll): this is needed when trie is a MutableTrieCache, check if makes sense to commit here
        Trie trie = this.trie.getTrie();
        Iterator<Trie.IterationElement> preOrderIterator = trie.getPreOrderIterator();
        while (preOrderIterator.hasNext()) {
            TrieKeySlice nodeKey = preOrderIterator.next().getNodeKey();
            int nodeKeyLength = nodeKey.length();
            if (nodeKeyLength == (1 + TrieKeyMapper.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES) * Byte.SIZE) {
                byte[] address = nodeKey.slice(nodeKeyLength - RskAddress.LENGTH_IN_BYTES * Byte.SIZE, nodeKeyLength).encode();
                result.add(new RskAddress(address));
            }
        }
        return result;
    }

    // To start tracking, a new repository is created, with a MutableTrieCache in the middle
    @Override
    public synchronized Repository startTracking() {
        return new MutableRepository(new MutableTrieCache(this.getMutableTrie()));
    }

    @Override
    public synchronized void flush() {
        this.trie.save();
        this.trie.flush();
    }

    @Override
    public synchronized void flushNoReconnect() {
        this.flush();
    }

    @Override
    public void save() {
        this.trie.save();
    }

    @Override
    public synchronized void commit() {
        this.trie.commit();
    }

    @Override
    public synchronized void rollback() {
        this.trie.rollback();
    }

    @Override
    public synchronized void syncToRoot(byte[] root) {
        this.trie = this.trie.getSnapshotTo(new Keccak256(root));
    }

    @Override
    public void syncTo(Trie root) {
        this.trie = new MutableTrieImpl(root);
    }

    @Override
    public synchronized boolean isClosed() {
        return this.closed;
    }

    @Override
    public synchronized void close() {
        this.closed = true;
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void updateBatch(Map<RskAddress, AccountState> stateCache) {
        logger.debug("updatingBatch: stateCache.size: {}", stateCache.size());

        for (Map.Entry<RskAddress, AccountState> entry : stateCache.entrySet()) {
            RskAddress addr = entry.getKey();
            AccountState accountState = entry.getValue();

            if (accountState.isDeleted()) {
                delete(addr);
                logger.debug("delete: [{}]", addr);
            } else {
                updateAccountState(addr, accountState);
            }
        }
        stateCache.clear();
    }

    @Override
    public void updateBatchDetails(Map<RskAddress, ContractDetails> cacheDetails) {
        // Note: ContractDetails is only compatible with DataWord sized elements in storage!
        for (Map.Entry<RskAddress, ContractDetails> entry : cacheDetails.entrySet()) {
            RskAddress addr = entry.getKey();
            ContractDetails details = entry.getValue();
            updateContractDetails(addr,details);
        }
    }

    @Override
    public synchronized byte[] getRoot() {
        if (this.trie.hasStore()) {
            this.trie.save();
        }

        byte[] rootHash = this.trie.getHash().getBytes();
        logger.trace("getting repository root hash {}", Hex.toHexString(rootHash));
        return rootHash;
    }

    // What's the difference between startTracking() and getSnapshotTo() ?
    // getSnapshotTo() does not create a new cache layer. It just gives you
    // a view of the same Repository under another root. This means that if you
    // save data, that data will pass though ?? Yes.

    // A snapshot is a RepositoryTracker object but it's not a cache,
    // because the repository created is a MutableRepository, and
    // not a RepositoryTrack
    @Override
    public synchronized Repository getSnapshotTo(byte[] root) {
        MutableTrie atrie = this.trie.getSnapshotTo(new Keccak256(root));
        return new MutableRepository(atrie.getTrie());
    }

    @Override
    public synchronized void updateContractDetails(RskAddress addr, final ContractDetails contractDetails){
        // Don't let a storage key live without an accountstate
        if (!isExist(addr)) {
            createAccount(addr); // if not exists
        }

        Map<DataWord, byte[]> storage = contractDetails.getStorage();
        for (Map.Entry<DataWord , byte[]> entry : storage.entrySet()) {
            addStorageBytes(addr,entry.getKey(),entry.getValue());
        }

        saveCode(addr, contractDetails.getCode());
    }

    @Override
    public synchronized void updateAccountState(RskAddress addr, final AccountState accountState) {
        byte[] accountKey = trieKeyMapper.getAccountKey(addr);
        this.trie.put(accountKey, accountState.getEncoded());
    }

    @Nonnull
    private synchronized AccountState getAccountStateOrCreateNew(RskAddress addr) {
        AccountState account = getAccountState(addr);
        return (account == null) ? createAccount(addr) : account;
    }
}
