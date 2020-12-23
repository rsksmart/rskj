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
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.*;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.AccountState;
import org.ethereum.core.RentTracker;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;

public class MutableRepository implements Repository {
    private static final Logger logger = LoggerFactory.getLogger("repository");
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final Keccak256 KECCAK_256_OF_EMPTY_ARRAY = new Keccak256(Keccak256Helper.keccak256(EMPTY_BYTE_ARRAY));
    private static final byte[] ONE_BYTE_ARRAY = new byte[] { 0x01 };

    private final TrieKeyMapper trieKeyMapper;
    private final MutableTrie mutableTrie;
    private final RentTracker rentTracker;
    private AccountInformationProvider infoProvider;

    public MutableRepository(TrieStore trieStore, Trie trie, RentTracker rentTracker) {
        this(new MutableTrieImpl(trieStore, trie),rentTracker);
    }

    public MutableRepository(TrieStore trieStore, Trie trie) {
        this(new MutableTrieImpl(trieStore, trie),null);
    }

    public MutableRepository(MutableTrie mutableTrie, RentTracker rentTracker) {
        this.trieKeyMapper = new TrieKeyMapper();
        this.mutableTrie = mutableTrie;
        this.rentTracker = rentTracker;
    }
    public MutableRepository(MutableTrie mutableTrie) {
        this.trieKeyMapper = new TrieKeyMapper();
        this.mutableTrie = mutableTrie;
        this.rentTracker = null;
    }


    public AccountInformationProvider getInfoProvider() {
        if (infoProvider==null)
            infoProvider = new AccountInformationProviderProxy(this);

        return infoProvider;
    }

    public RentTracker getRentTracker() {
        return rentTracker;
    }

    @Override
    public Trie getTrie() {
        return mutableTrie.getTrie();
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
        mutableTrie.put(prefix, ONE_BYTE_ARRAY);
        rentTracker.trackWriteRent(prefix,ONE_BYTE_ARRAY.length);
    }

    @Override
    public synchronized boolean isExist(RskAddress addr, boolean trackRent) {
        // Here we assume size != 0 means the account exists
        TrieNodeData nodedata = mutableTrie.getNodeData(trieKeyMapper.getAccountKey(addr));
        if (trackRent)
            rentTracker.trackReadRent(nodedata);
        return nodedata.getValueLength() > 0;
    }

    @Override
    public boolean isExist(RskAddress addr) {
        return isExist(addr,false);
    }

    @Override
    public synchronized AccountState getAccountState(RskAddress addr, boolean trackRent) {
        AccountState result = null;
        byte[] accountData = getAccountData(addr,trackRent);

        // If there is no account it returns null
        if (accountData != null && accountData.length != 0) {
            result = new AccountState(accountData);
        }
        return result;
    }

    @Override
    public AccountState getAccountState(RskAddress addr) {
        return getAccountState(addr,false);
    }

    @Override
    public long getAccountNodeLRPTime(RskAddress addr) {
        TrieNodeData nodedata = mutableTrie.getNodeData(trieKeyMapper.getAccountKey(addr));
        if (nodedata==null)
            return 0;
        return nodedata.getLastRentPaidTime();
    }

    @Override
    public synchronized void delete(RskAddress addr) {
        // TODO: No rent tracker on recursive deletes (should it?)
        mutableTrie.deleteRecursive(trieKeyMapper.getAccountKey(addr));
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
    public synchronized BigInteger getNonce(RskAddress addr,boolean trackRent) {
        // Why would getNonce create an Account in the repository? The semantic of a get()
        // is clear: do not change anything!
        AccountState account = getAccountState(addr,trackRent );
        if (account == null) {
            return BigInteger.ZERO;
        }

        return account.getNonce();
    }

    @Override
    public synchronized void saveCode(RskAddress addr, byte[] code) {
        byte[] key = trieKeyMapper.getCodeKey(addr);
        mutableTrie.put(key, code);
        rentTracker.trackWriteRent(key,code.length);

        if (code != null && code.length != 0 && !isExist(addr,true )) {
            createAccount(addr);
        }
    }

    @Override
    public synchronized int getCodeLength(RskAddress addr, boolean trackRent) {
        AccountState account = getAccountState(addr,trackRent );
        if (account == null || account.isHibernated()) {
            return 0;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);
        TrieNodeData nodedata = mutableTrie.getNodeData(key);
        if (trackRent)
            rentTracker.trackReadRent(nodedata);
        return nodedata.getValueLength();
    }

    @Override
    public int getCodeLength(RskAddress addr) {
        return getCodeLength(addr,false);
    }

    @Override
    public synchronized Keccak256 getCodeHashNonStandard(RskAddress addr, boolean trackRent) {

        if (!isExist(addr,trackRent )) {
            return Keccak256.ZERO_HASH;
        }

        if (!isContract(addr,trackRent)) {
            return KECCAK_256_OF_EMPTY_ARRAY;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);
        TrieNodeData nodedata = mutableTrie.getNodeData(key);
        if (trackRent)
            rentTracker.trackReadRent(nodedata);
        //Returning ZERO_HASH is the non standard implementation we had pre RSKIP169 implementation
        //and thus me must honor it.
        if (nodedata==null)
            return Keccak256.ZERO_HASH;

        Keccak256 valueHash = nodedata.getValueHash();

        return valueHash;
    }

    @Override
    public Keccak256 getCodeHashNonStandard(RskAddress addr) {
        return getCodeHashNonStandard(addr,false);
    }

    @Override
    public synchronized Keccak256 getCodeHashStandard(RskAddress addr, boolean trackRent) {

        if (!isExist(addr,trackRent )) {
            return Keccak256.ZERO_HASH;
        }

        if (!isContract(addr,trackRent)) {
            return KECCAK_256_OF_EMPTY_ARRAY;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);
        TrieNodeData nodedata = mutableTrie.getNodeData(key);

        if (trackRent)
            rentTracker.trackReadRent(nodedata);

        if (nodedata==null)
            return KECCAK_256_OF_EMPTY_ARRAY;

        Keccak256 valueHash = nodedata.getValueHash();

        return valueHash;
    }

    @Override
    public Keccak256 getCodeHashStandard(RskAddress addr) {
        return getCodeHashNonStandard(addr,false);
    }

    @Override
    public synchronized byte[] getCode(RskAddress addr,boolean trackRent) {
        if (!isExist(addr,trackRent )) {
            return EMPTY_BYTE_ARRAY;
        }

        AccountState account = getAccountState(addr,trackRent );
        if (account.isHibernated()) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);
        TrieNodeData nodedata = mutableTrie.getNodeData(key);
        if (trackRent)
            rentTracker.trackReadRent(nodedata);
        return nodedata.getValue();
    }

    @Override
    public boolean isContract(RskAddress addr,boolean trackRent) {
        byte[] prefix = trieKeyMapper.getAccountStoragePrefixKey(addr);
        TrieNodeData nodedata = mutableTrie.getNodeData(prefix);
        if (trackRent)
            rentTracker.trackReadRent(nodedata);

        // if nodedata != null, then nodedata.getValue() should never be null.
        return (nodedata!=null);
    }

    @Override
    public synchronized void addStorageRow(RskAddress addr, DataWord key, DataWord value) {
        // DataWords are stored stripping leading zeros.
        addStorageBytes(addr, key, value.getByteArrayForStorage());
    }

    @Override
    public synchronized void addStorageBytes(RskAddress addr, DataWord key, byte[] value) {
        // This should not happen in production because contracts are created before storage cells are added to them.
        // But it happens in Repository tests, that create only storage row cells.
        if (!isExist(addr,true )) {
            createAccount(addr);
            setupContract(addr);
        }

        byte[] triekey = trieKeyMapper.getAccountStorageKey(addr, key);

        // Special case: if the value is an empty vector, we pass "null" which commands the trie to remove the item.
        // Note that if the call comes from addStorageRow(), this method will already have replaced 0 by null, so the
        // conversion here only applies if this is called directly. If suppose this only occurs in tests, but it can
        // also occur in precompiled contracts that store data directly using this method.
        if (value == null || value.length == 0) {
            mutableTrie.put(triekey, null);
        } else {
            mutableTrie.put(triekey, value);
        }
    }

    @Override
    public synchronized DataWord getStorageValue(RskAddress addr, DataWord key,boolean trackRent) {
        byte[] triekey = trieKeyMapper.getAccountStorageKey(addr, key);
        TrieNodeData nodedata = mutableTrie.getNodeData(triekey);
        if (trackRent)
            rentTracker.trackReadRent(nodedata);

        if (nodedata == null) {
            return null;
        }
        byte[] value = nodedata.getValue();
        return DataWord.valueOf(value);
    }

    @Override
    public synchronized byte[] getStorageBytes(RskAddress addr, DataWord key,boolean trackRent) {
        byte[] triekey = trieKeyMapper.getAccountStorageKey(addr, key);
        TrieNodeData nodedata = mutableTrie.getNodeData(triekey);
        if (trackRent)
            rentTracker.trackReadRent(nodedata);
        if (nodedata==null)
            return null;
        return nodedata.getValue();
    }

    @Override
    public Coin getBalance(RskAddress addr) {
        return getBalance(addr,false);
    }

    @Nullable
    @Override
    public DataWord getStorageValue(RskAddress addr, DataWord key) {
        return getStorageValue(addr,key,false);
    }

    @Nullable
    @Override
    public byte[] getStorageBytes(RskAddress addr, DataWord key) {
        return getStorageBytes(addr,key,false);
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        // -1 b/c the first bit is implicit in the storage node
        return mutableTrie.getStorageKeys(addr);
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

    @Nullable
    @Override
    public byte[] getCode(RskAddress addr) {
        return getCode(addr,false);
    }

    @Override
    public boolean isContract(RskAddress addr) {
        return isContract(addr,false);
    }

    @Override
    public BigInteger getNonce(RskAddress addr) {
        return getNonce(addr,false);
    }

    @Override
    public synchronized Coin getBalance(RskAddress addr,boolean trackRent) {
        AccountState account = getAccountState(addr,trackRent );
        return (account == null) ? Coin.ZERO: account.getBalance();
    }

    @Override
    public synchronized Coin addBalance(RskAddress addr, Coin value) {
        AccountState account = getAccountStateOrCreateNew(addr);

        Coin result = account.addToBalance(value);
        updateAccountState(addr, account);

        return result;
    }

    @Override
    public synchronized Set<RskAddress> getAccountsKeys() {
        Set<RskAddress> result = new HashSet<>();
        //TODO(diegoll): this is needed when trie is a MutableTrieCache, check if makes sense to commit here
        mutableTrie.commit();
        Trie trie = mutableTrie.getTrie();
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
    public synchronized Repository startTracking(RentTracker newRentTracker) {
        // TODO: SDL Check if I need a child rentTracker or do something else here
        return new MutableRepository(new MutableTrieCache(mutableTrie),newRentTracker);
    }

    @Override
    public synchronized Repository startTracking() {
        // TODO: SDL Check if I need a child rentTracker or do something else here
        return new MutableRepository(new MutableTrieCache(mutableTrie),rentTracker);
    }
    @Override
    public void save() {
        mutableTrie.save();
    }

    @Override
    public synchronized void commit() {
        mutableTrie.commit();
    }

    @Override
    public synchronized void rollback() {
        mutableTrie.rollback();
    }

    @Override
    public synchronized byte[] getRoot() {
        mutableTrie.save();

        Keccak256 rootHash = mutableTrie.getHash();
        logger.trace("getting repository root hash {}", rootHash);
        return rootHash.getBytes();
    }

    @Override
    public synchronized void updateAccountState(RskAddress addr, final AccountState accountState) {
        byte[] accountKey = trieKeyMapper.getAccountKey(addr);
        byte[] value = accountState.getEncoded();
        mutableTrie.put(accountKey, value);
        rentTracker.trackWriteRent(accountKey,value.length);
    }

    @VisibleForTesting
    public byte[] getStorageStateRoot(RskAddress addr) {
        byte[] prefix = trieKeyMapper.getAccountStoragePrefixKey(addr);

        // The value should be ONE_BYTE_ARRAY, but we don't need to check nothing else could be there.
        Trie storageRootNode = mutableTrie.getTrie().find(prefix);
        if (storageRootNode == null) {
            return HashUtil.EMPTY_TRIE_HASH;
        }

        // Now it's a bit tricky what to return: if I return the storageRootNode hash then it's counting the "0x01"
        // value, so the try one gets will never match the trie one gets if creating the trie without any other data.
        // Unless the PDV trie is used. The best we can do is to return storageRootNode hash
        return storageRootNode.getHash().getBytes();
    }

    @Nonnull
    private synchronized AccountState getAccountStateOrCreateNew(RskAddress addr) {
        AccountState account = getAccountState(addr,true );
        if (account == null)
            return createAccount(addr);
        else
            return account;
            //return (account == null) ? createAccount(addr) : account;
    }

    private byte[] getAccountData(RskAddress addr, boolean trackRent) {

        TrieNodeData nodedata = mutableTrie.getNodeData(trieKeyMapper.getAccountKey(addr));
        if (trackRent)
            rentTracker.trackReadRent(nodedata);
        if (nodedata==null)
            return null;
        return nodedata.getValue();
    }
}
