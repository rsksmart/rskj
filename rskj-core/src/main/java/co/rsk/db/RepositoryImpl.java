/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieStore;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.*;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.crypto.SHA3Helper.sha3;

/**
 * Created by ajlopez on 29/03/2017.
 */
public class RepositoryImpl implements Repository {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final byte[] EMPTY_DATA_HASH = HashUtil.sha3(EMPTY_BYTE_ARRAY);

    private static final Logger logger = LoggerFactory.getLogger("repository");

    private final RskSystemProperties config;
    private TrieStore store;
    private Trie trie;
    private DetailsDataStore detailsDataStore;
    private boolean closed;

    public RepositoryImpl(RskSystemProperties config) {
        this(config, null);
    }

    public RepositoryImpl(RskSystemProperties config, TrieStore store) {
        this(config, store, new HashMapDB());
    }

    public RepositoryImpl(RskSystemProperties config, TrieStore store, KeyValueDataSource detailsDS) {
        this(config, store, new DetailsDataStore(config, new DatabaseImpl(detailsDS)));
    }

    public RepositoryImpl(RskSystemProperties config, TrieStore store, DetailsDataStore detailsDataStore) {
        this.config = config;
        this.store = store;
        this.trie = new TrieImpl(store, true);
        this.detailsDataStore = detailsDataStore;
    }

    @Override
    public synchronized AccountState createAccount(RskAddress addr) {
        AccountState accountState = new AccountState();
        updateAccountState(addr, accountState);
        updateContractDetails(addr, new ContractDetailsImpl(config));
        return accountState;
    }

    @Override
    public synchronized boolean isExist(RskAddress addr) {
        return getAccountState(addr) != null;
    }

    @Override
    public synchronized AccountState getAccountState(RskAddress addr) {
        AccountState result = null;
        byte[] accountData = null;

        accountData = this.trie.get(addr.getBytes());

        if (accountData != null && accountData.length != 0) {
            result = new AccountState(accountData);
        }

        return result;
    }

    @Override
    public synchronized void delete(RskAddress addr) {
        this.trie = this.trie.delete(addr.getBytes());
    }

    @Override
    public synchronized void hibernate(RskAddress addr) {
        AccountState account = getAccountStateOrCreateNew(addr);

        account.hibernate();
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
        AccountState account = getAccountStateOrCreateNew(addr);
        return account.getNonce();
    }

    @Override
    public synchronized ContractDetails getContractDetails(RskAddress addr) {
        // That part is important cause if we have
        // to sync details storage according the trie root
        // saved in the account
        AccountState accountState = getAccountState(addr);
        byte[] storageRoot = EMPTY_TRIE_HASH;
        if (accountState != null) {
            storageRoot = getAccountState(addr).getStateRoot();
        }

        ContractDetails details =  detailsDataStore.get(addr);
        if (details != null) {
            details = details.getSnapshotTo(storageRoot);
        }

        return  details;
    }

    @Override
    public synchronized void saveCode(RskAddress addr, byte[] code) {
        AccountState accountState = getAccountState(addr);
        ContractDetails details = getContractDetails(addr);

        if (accountState == null) {
            accountState = createAccount(addr);
            details = getContractDetails(addr);
        }

        details.setCode(code);
        accountState.setCodeHash(sha3(code));

        updateContractDetails(addr, details);
        updateAccountState(addr, accountState);
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

        byte[] codeHash = account.getCodeHash();

        if (Arrays.equals(codeHash, EMPTY_DATA_HASH)) {
            return EMPTY_BYTE_ARRAY;
        }

        ContractDetails details = getContractDetails(addr);
        return (details == null) ? null : details.getCode();
    }

    @Override
    public synchronized void addStorageRow(RskAddress addr, DataWord key, DataWord value) {
        ContractDetails details = getContractDetails(addr);
        if (details == null) {
            createAccount(addr);
            details = getContractDetails(addr);
        }

        details.put(key, value);

        updateContractDetails(addr, details);
    }

    @Override
    public synchronized void addStorageBytes(RskAddress addr, DataWord key, byte[] value) {
        ContractDetails details = getContractDetails(addr);

        if (details == null) {
            createAccount(addr);
            details = getContractDetails(addr);
        }

        details.putBytes(key, value);

        updateContractDetails(addr, details);
    }

    @Override
    public synchronized DataWord getStorageValue(RskAddress addr, DataWord key) {
        ContractDetails details = getContractDetails(addr);
        return (details == null) ? null : details.get(key);
    }

    @Override
    public synchronized byte[] getStorageBytes(RskAddress addr, DataWord key) {
        ContractDetails details = getContractDetails(addr);
        return (details == null) ? null : details.getBytes(key);
    }

    @Override
    public synchronized Coin getBalance(RskAddress addr) {
        AccountState account = getAccountState(addr);
        return (account == null) ? new AccountState().getBalance() : account.getBalance();
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

        for (RskAddress addr : detailsDataStore.keys()) {
            if (this.isExist(addr)) {
                result.add(addr);
            }
        }

        return result;
    }

    @Override
    public synchronized void dumpState(Block block, long gasUsed, int txNumber, byte[] txHash) {
        // To be implemented
    }

    @Override
    public synchronized Repository startTracking() {
        return new RepositoryTrack(config, this);
    }

    @Override
    public synchronized void flush() {
        if (this.detailsDataStore != null) {
            this.detailsDataStore.flush();
        }

        if (this.store != null) {
            this.trie.save();
        }
    }

    @Override
    public synchronized void flushNoReconnect() {
        this.flush();
    }

    @Override
    public synchronized void commit() {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void rollback() {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void syncToRoot(byte[] root) {
        this.trie = this.trie.getSnapshotTo(root);
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
    public synchronized void updateBatch(Map<RskAddress, AccountState> stateCache,
                                         Map<RskAddress, ContractDetails> detailsCache) {
        logger.debug("updatingBatch: detailsCache.size: {}", detailsCache.size());

        for (Map.Entry<RskAddress, AccountState> entry : stateCache.entrySet()) {
            RskAddress addr = entry.getKey();
            AccountState accountState = entry.getValue();

            ContractDetails contractDetails = detailsCache.get(addr);

            if (accountState.isDeleted()) {
                delete(addr);
                logger.debug("delete: [{}]", addr);
            } else {
                if (!contractDetails.isDirty()) {
                    continue;
                }

                ContractDetailsCacheImpl contractDetailsCache = (ContractDetailsCacheImpl) contractDetails;

                if (contractDetailsCache.getOriginalContractDetails() == null) {
                    ContractDetails originalContractDetails = new ContractDetailsImpl(config);
                    originalContractDetails.setAddress(addr.getBytes());
                    contractDetailsCache.setOriginalContractDetails(originalContractDetails);
                    contractDetailsCache.commit();
                }

                contractDetails = contractDetailsCache.getOriginalContractDetails();

                updateContractDetails(addr, contractDetails);

                if (!Arrays.equals(accountState.getCodeHash(), EMPTY_TRIE_HASH)) {
                    accountState.setStateRoot(contractDetails.getStorageHash());
                }

                updateAccountState(addr, accountState);
            }
        }

        logger.debug("updated: detailsCache.size: {}", detailsCache.size());

        stateCache.clear();
        detailsCache.clear();
    }

    @Override
    public synchronized byte[] getRoot() {
        if (this.trie.hasStore()) {
            this.trie.save();
        }

        byte[] rootHash = this.trie.getHash();

        logger.trace("getting repository root hash {}", Hex.toHexString(rootHash));

        return rootHash;
    }

    @Override
    public synchronized void loadAccount(RskAddress addr,
                                         Map<RskAddress, AccountState> cacheAccounts,
                                         Map<RskAddress, ContractDetails> cacheDetails) {
        AccountState account = getAccountState(addr);
        ContractDetails details = getContractDetails(addr);

        account = (account == null) ? new AccountState() : account.clone();
        details = new ContractDetailsCacheImpl(details);

        cacheAccounts.put(addr, account);
        cacheDetails.put(addr, details);
    }

    @Override
    public synchronized Repository getSnapshotTo(byte[] root) {
        RepositoryImpl snapshotRepository = new RepositoryImpl(this.config, this.store, this.detailsDataStore);
        snapshotRepository.syncToRoot(root);
        return snapshotRepository;
    }

    @Override
    public synchronized DetailsDataStore getDetailsDataStore() {
        return this.detailsDataStore;
    }

    @Override
    public synchronized void updateContractDetails(RskAddress addr, final ContractDetails contractDetails) {
        detailsDataStore.update(addr, contractDetails);
    }

    @Override
    public synchronized void updateAccountState(RskAddress addr, final AccountState accountState) {
        this.trie = this.trie.put(addr.getBytes(), accountState.getEncoded());
    }

    @Nonnull
    private synchronized AccountState getAccountStateOrCreateNew(RskAddress addr) {
        AccountState account = getAccountState(addr);
        return (account == null) ? createAccount(addr) : account;
    }
}
