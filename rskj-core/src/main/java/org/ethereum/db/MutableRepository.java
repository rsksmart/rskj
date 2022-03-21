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
import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import co.rsk.trie.TrieStore;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static org.ethereum.db.OperationType.*;

// todo(fedejinich) Currently MutableRepository has the capability of tracking trie involved nodes,
//  this is useful for storage rent and parallel txs processing.
//  NOTE: tracking capability might be extracted into a MutableRepositoryTracked
public class MutableRepository implements Repository {
    private static final Logger logger = LoggerFactory.getLogger("repository");
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final Keccak256 KECCAK_256_OF_EMPTY_ARRAY = new Keccak256(Keccak256Helper.keccak256(EMPTY_BYTE_ARRAY));
    private static final byte[] ONE_BYTE_ARRAY = new byte[] { 0x01 };

    protected final TrieKeyMapper trieKeyMapper;
    protected final MutableTrie mutableTrie;

    // todo(fedejinich) ALL THIS MEMBERS MIGHT BE MOVED TO MutableRepositoryTracked
    // enables node tracking feature
    private final boolean enableTracking;
    // a set to track all the used trie-value-containing nodes in this repository (and its children repositories)
    protected final Set<TrackedNode> trackedNodes; // todo(fedejinich) this might be moved to MutableRepositoryTracked
    // a list of nodes tracked nodes that were rolled back (due to revert or OOG)
    protected final List<TrackedNode> rollbackNodes; // todo(fedejinich) this might be moved to MutableRepositoryTracked
    // parent repository to commit tracked nodes
    protected final MutableRepository parentRepository; // todo(fedejinich) this might be moved to MutableRepositoryTracked
    // this contains the hash of the ongoing tracked transaction
    protected String trackedTransactionHash = "NO_TRANSACTION_HASH";  // todo(fedejinich) this might be moved to MutableRepositoryTracked

    // default constructor
    protected MutableRepository(MutableTrie mutableTrie, MutableRepository parentRepository,
                                Set<TrackedNode> trackedNodes, List<TrackedNode> rollbackNodes,
                                boolean enableTracking) {
        this.trieKeyMapper = new TrieKeyMapper();
        this.mutableTrie = mutableTrie;
        this.parentRepository = parentRepository;
        this.trackedNodes = trackedNodes;
        this.rollbackNodes = rollbackNodes;
        this.enableTracking = enableTracking;
    }

    // creates a new repository (tracking disabled)
    public MutableRepository(MutableTrie mutableTrie) {
        this(mutableTrie,  null, Collections.emptySet(), Collections.emptyList(), false);
    }

    // another way to create a repository (tracking disabled)
    public MutableRepository(TrieStore trieStore, Trie trie) {
        this(new MutableTrieImpl(trieStore, trie), null, new HashSet<>(), new ArrayList<>(), false);
    }

    // useful for key tracking, it connects between repositories. the root Repository will contain a null parentRepository
    protected MutableRepository(MutableTrie mutableTrie, MutableRepository parentRepository, boolean enableTracking) {
        this(mutableTrie, parentRepository, new HashSet<>(), new ArrayList<>(), enableTracking);
    }

    // creates a tracked repository, all the child repositories (created with startTracking()) will also be tracked
    public static MutableRepository trackedRepository(MutableTrie mutableTrieCache) {
        return new MutableRepository(mutableTrieCache, null, true);
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
        internalPut(prefix, ONE_BYTE_ARRAY);
    }

    @Override
    public synchronized boolean isExist(RskAddress addr) {
        // Here we assume size != 0 means the account exists
        return internalGetValueLength(trieKeyMapper.getAccountKey(addr))
                .compareTo(Uint24.ZERO) > 0;
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
        internalDeleteRecursive(trieKeyMapper.getAccountKey(addr));
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
        if (account == null) {
            return BigInteger.ZERO;
        }

        return account.getNonce();
    }

    @Override
    public synchronized void saveCode(RskAddress addr, byte[] code) {
        byte[] key = trieKeyMapper.getCodeKey(addr);
        internalPut(key, code);

        if (code != null && code.length != 0 && !isExist(addr)) {
            createAccount(addr);
        }
    }

    @Override
    public synchronized int getCodeLength(RskAddress addr) {
        AccountState account = getAccountState(addr);
        if (account == null || account.isHibernated()) {
            return 0;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);
        return internalGetValueLength(key).intValue();
    }

    @Override
    public synchronized Keccak256 getCodeHashNonStandard(RskAddress addr) {

        if (!isExist(addr)) {
            return Keccak256.ZERO_HASH;
        }

        if (!isContract(addr)) {
            return KECCAK_256_OF_EMPTY_ARRAY;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);
        Optional<Keccak256> valueHash = internalGetValueHash(key);

        //Returning ZERO_HASH is the non standard implementation we had pre RSKIP169 implementation
        //and thus me must honor it.
        return valueHash.orElse(Keccak256.ZERO_HASH);
    }

    @Override
    public synchronized Keccak256 getCodeHashStandard(RskAddress addr) {

        if (!isExist(addr)) {
            return Keccak256.ZERO_HASH;
        }

        if (!isContract(addr)) {
            return KECCAK_256_OF_EMPTY_ARRAY;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);

        return internalGetValueHash(key).orElse(KECCAK_256_OF_EMPTY_ARRAY);
    }

    @Override
    public synchronized byte[] getCode(RskAddress addr) {
        if (!isExist(addr)) {
            return EMPTY_BYTE_ARRAY;
        }

        AccountState account = getAccountState(addr);
        if (account.isHibernated()) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);
        return internalGet(key);
    }

    @Override
    public boolean isContract(RskAddress addr) {
        byte[] prefix = trieKeyMapper.getAccountStoragePrefixKey(addr);
        return internalGet(prefix) != null;
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
        if (!isExist(addr)) {
            createAccount(addr);
            setupContract(addr);
        }

        byte[] triekey = trieKeyMapper.getAccountStorageKey(addr, key);

        // Special case: if the value is an empty vector, we pass "null" which commands the trie to remove the item.
        // Note that if the call comes from addStorageRow(), this method will already have replaced 0 by null, so the
        // conversion here only applies if this is called directly. If suppose this only occurs in tests, but it can
        // also occur in precompiled contracts that store data directly using this method.
        if (value == null || value.length == 0) {
            internalPut(triekey, null); // todo(fedejinich) why put(key, null) and not deleteRecursive(key) ?
        } else {
            internalPut(triekey, value);
        }
    }

    @Override
    public synchronized DataWord getStorageValue(RskAddress addr, DataWord key) {
        byte[] triekey = trieKeyMapper.getAccountStorageKey(addr, key);
        byte[] value = internalGet(triekey);
        if (value == null) {
            return null;
        }

        return DataWord.valueOf(value);
    }

    @Override
    public synchronized byte[] getStorageBytes(RskAddress addr, DataWord key) {
        byte[] triekey = trieKeyMapper.getAccountStorageKey(addr, key);
        return internalGet(triekey);
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        // -1 b/c the first bit is implicit in the storage node
        return internalGetStorageKeys(addr);
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
        Iterator<Trie.IterationElement> preOrderIterator = trie.getPreOrderIterator(true);
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
    // todo(fedejinich) to me this should be named as newChildRepository()
    @Override
    public synchronized Repository startTracking() {
        MutableRepository mutableRepository = new MutableRepository(new MutableTrieCache(this.mutableTrie), this, this.enableTracking);
        mutableRepository.setTrackedTransactionHash(trackedTransactionHash); // todo(fedejinich) this will be moved to MutableRepositoryTracked
        return mutableRepository;
    }

    @Override
    public void save() {
        this.mutableTrie.save();
    }

    @Override
    public synchronized void commit() {
        this.mutableTrie.commit();

        if(this.parentRepository != null) {
            this.parentRepository.mergeTrackedNodes(this.trackedNodes);
            this.parentRepository.addRollbackNodes(this.rollbackNodes);
        }
    }

    @Override
    public synchronized void rollback() {
        this.mutableTrie.rollback();

        if(parentRepository != null) {
            this.parentRepository.addRollbackNodes(this.trackedNodes);
            this.trackedNodes.clear();
            this.rollbackNodes.clear();
        }
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
        internalPut(accountKey, accountState.getEncoded());
    }

    @Nonnull
    private synchronized AccountState getAccountStateOrCreateNew(RskAddress addr) {
        AccountState account = getAccountState(addr);
        return (account == null) ? createAccount(addr) : account;
    }

    private byte[] getAccountData(RskAddress addr) {
        return internalGet(trieKeyMapper.getAccountKey(addr));
    }

    @VisibleForTesting // todo(techdebt) this method shouldn't be here
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

    // todo(fedejinich) all the content below might be extracted to MutableRepositoryTracked

    public Set<TrackedNode> getTrackedNodes(String transactionHash) {
        return this.trackedNodes.stream()
                .filter(trackedNode -> trackedNode.getTransactionHash().equals(transactionHash))
                .collect(Collectors.toSet());
    }

    public List<TrackedNode> getRollBackNodes(String transactionHash) {
        return this.rollbackNodes.stream()
                .filter(trackedNode -> trackedNode.getTransactionHash().equals(transactionHash))
                .collect(Collectors.toList());
    }

    public void setTrackedTransactionHash(String trackedTransactionHash) {
        this.trackedTransactionHash = trackedTransactionHash;
    }

    // Internal methods contains node tracking

    protected void internalPut(byte[] key, byte[] value) {
        mutableTrie.put(key, value);
        // todo(fedejinich) should track delete operation (value == null)
        trackNodeWriteOperation(key, value == null);
    }

    protected void internalDeleteRecursive(byte[] key) {
        // todo(fedejinich) what happens for non existing keys? should track with false result?
        mutableTrie.deleteRecursive(key);
        trackNodeWriteOperation(key, true);
    }

    protected byte[] internalGet(byte[] key) {
        byte[] value = mutableTrie.get(key);

        // todo(fedejinich) should track get() success with a bool (value != null)
        trackNodeReadOperation(key, value != null);

        return value;
    }

    protected Optional<Keccak256> internalGetValueHash(byte[] key) {
        Optional<Keccak256> valueHash = mutableTrie.getValueHash(key);

        trackNodeReadOperation(key, valueHash.isPresent());

        return valueHash;
    }

    protected Uint24 internalGetValueLength(byte[] key) {
        Uint24 valueLength = mutableTrie.getValueLength(key);

        trackNodeReadOperation(key, valueLength != Uint24.ZERO);

        return valueLength;
    }

    protected Iterator<DataWord> internalGetStorageKeys(RskAddress addr) {
        Iterator<DataWord> storageKeys = mutableTrie.getStorageKeys(addr);

        // todo(fedejinich) how should I track the right key/s?
        boolean result = !storageKeys.equals(Collections.emptyIterator());
        byte[] accountStoragePrefixKey = trieKeyMapper.getAccountStoragePrefixKey(addr);

        trackNodeReadOperation(accountStoragePrefixKey, result);

        return storageKeys;
    }

    protected void trackNodeWriteOperation(byte[] key, boolean isDelete) {
        trackNode(key, WRITE_OPERATION, true, isDelete);
    }

    protected void trackNodeReadOperation(byte[] key, boolean result) {
        trackNode(key, READ_OPERATION, result, false);
    }

    protected void trackNode(byte[] key, OperationType operationType, boolean result, boolean isDelete) {
        if(this.enableTracking) {
            // todo(fedejinich) NEED TO DEFINE WHEN/HOW TRACK CONTRACT CODES OPERATIONS
            TrackedNode trackedNode = new TrackedNode(
                new ByteArrayWrapper(key),
                operationType,
                this.trackedTransactionHash,
                result,
                isDelete
            );
            this.trackedNodes.add(trackedNode);
        }
    }

    private void mergeTrackedNodes(Set<TrackedNode> trackedNodes) {
        this.trackedNodes.addAll(trackedNodes);
    }

    private void addRollbackNodes(Collection<TrackedNode> trackedNodes) {
        this.rollbackNodes.addAll(trackedNodes);
    }
}
