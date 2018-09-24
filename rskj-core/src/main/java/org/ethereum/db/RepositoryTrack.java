/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import co.rsk.db.*;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.*;

import static org.ethereum.crypto.Keccak256Helper.keccak256;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * @author Roman Mandeleil
 * @since 17.11.2014
 */
public class RepositoryTrack implements Repository {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final byte[] EMPTY_DATA_HASH = HashUtil.keccak256(EMPTY_BYTE_ARRAY);

    private static final Logger logger = LoggerFactory.getLogger("repository");

    private MutableTrie trie;
    private Repository parentRepo;
    private boolean closed;

    public MutableTrie getMutableTrie() {
        return trie;
    }

    public RepositoryTrack() {
        trie = new MutableTrieImpl(new TrieImpl());

    }

    public RepositoryTrack(Repository aparentRepo) {
        trie = new TrieCache(aparentRepo.getMutableTrie());
        this.parentRepo = aparentRepo;
    }
    public RepositoryTrack(Trie atrie) {
        trie = new TrieCache(new MutableTrieImpl(atrie));
        this.parentRepo =null;
    }
    public RepositoryTrack(Trie atrie,Repository aparentRepo) {
        trie = new TrieCache(new MutableTrieImpl(atrie));
        this.parentRepo = aparentRepo;
    }

    @Override
    public synchronized AccountState createAccount(RskAddress addr) {
        AccountState accountState = new AccountState();
        updateAccountState(addr, accountState);
        return accountState;
    }

    public byte[] getAccountData(RskAddress addr) {
        byte[] accountData = null;

        accountData = this.trie.get(getAccountKey(addr));
        return accountData;
    }

    public byte[] getAccountKey(RskAddress addr) {
        return addr.getBytes();
    }

    public static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public byte[] getAccountKeyChildKey(RskAddress addr,byte child) {
        return concat(addr.getBytes(),new byte[] {child});
    }

    @Override
    public synchronized boolean isExist(RskAddress addr) {
        byte[] accountData = getAccountData(addr);
        if (accountData != null && accountData.length != 0) {
            return true;
        }
        return false;

    }

    @Override
    public synchronized AccountState getAccountState(RskAddress addr) {
        AccountState result = null;
        byte[] accountData = getAccountData(addr);

        if (accountData != null && accountData.length != 0) {
            result = new AccountState(accountData);
        }
        return result;
    }

    @Override
    public synchronized void delete(RskAddress addr) {
        this.trie.delete(getAccountKey(addr));
    }

    @Override
    public synchronized void hibernate(RskAddress addr) {
        AccountState account = getAccountStateOrCreateNew(addr);

        account.hibernate();
        updateAccountState(addr, account);
    }

    @Override
    public void setNonce(RskAddress addr,BigInteger  nonce) {
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
        AccountState account = getAccountStateOrCreateNew(addr);
        return account.getNonce();
    }

    @Override
    public synchronized ContractDetails getContractDetails_deprecated(RskAddress addr) {
        ContractDetails details =  createProxyContractDetails(addr);
        return  details;
    }

    public synchronized ContractDetails createProxyContractDetails(RskAddress addr) {
        return new ContractDetailsImpl(addr.getBytes(),
                null, // for now I'm not returning any storage rows
                getCode(addr),0,"");
        //
    }

    public byte[] getCodeKey(RskAddress addr) {
        return getAccountKeyChildKey(addr,(byte) 1);
    }


    public byte[] getAccountStorageKey(RskAddress addr,byte[] subkey) {
        return concat(getAccountKeyChildKey(addr,(byte) 0),subkey);
    }

    @Override
    public synchronized void saveCode(RskAddress addr, byte[] code) {
        byte[] key = getCodeKey(addr);
        this.trie.put(key,code);

        AccountState  accountState = getAccountState(addr);
        accountState.setCodeHash(Keccak256Helper.keccak256(code));
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
        byte[] key = getCodeKey(addr);
        return this.trie.get(key);
    }


    @Override
    public synchronized void addStorageRow(RskAddress addr, DataWord key, DataWord value) {
        addStorageBytes(addr,key,value.getData());
    }

    @Override
    public synchronized void addStorageBytes(RskAddress addr, DataWord key, byte[] value) {
        if (!isExist(addr)) {
            createAccount(addr);
        }
        byte[] triekey = getAccountStorageKey(addr,key.getData());

        this.trie.put(triekey, value);
    }

    @Override
    public synchronized DataWord getStorageValue(RskAddress addr, DataWord key) {
        byte[] triekey = getAccountStorageKey(addr,key.getData());
        return new DataWord(this.trie.get(triekey));
    }

    @Override
    public synchronized byte[] getStorageBytes(RskAddress addr, DataWord key) {
        byte[] triekey = getAccountStorageKey(addr,key.getData());
        return this.trie.get(triekey);
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

    @Override
    public synchronized Set<RskAddress> getAccountsKeys() {
        Set<ByteArrayWrapper>  r = trie.collectKeys(20); // size must be 20 bytes
        Set<RskAddress> result = new HashSet<>();
        for (ByteArrayWrapper b: r
             ) {
            result.add(new RskAddress(b.getData()));
        }
        return result;

    }

    @Override
    public synchronized void dumpState(Block block, long gasUsed, int txNumber, byte[] txHash) {
        // To be implemented
    }

    // To start tracking, a new repository wrapper is created, with a TrieCache in the middle
    @Override
    public synchronized Repository startTracking() {

        return new RepositoryTrack(this);
    }

    public synchronized Repository startTrackingWithBenchmark() {

        return new RepositoryTrackWithBenchmarking(this);
    }

    @Override
    public synchronized void flush() {
        this.trie.save();
    }

    @Override
    public synchronized void flushNoReconnect() {
        this.flush();
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
        //
        // Note: ContractDetails is only compatible with DataWord sized elements in storage!
        for (Map.Entry<RskAddress, ContractDetails> entry : cacheDetails.entrySet()) {
            RskAddress addr = entry.getKey();
            ContractDetails details = entry.getValue();
            for (DataWord key : details.getStorageKeys()) {
                addStorageRow(addr, key, details.getStorage().get(key));
            }
            // inefficient
            saveCode(addr,details.getCode());

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

    @Override
    public synchronized Repository getSnapshotTo(byte[] root) {
        this.trie = this.trie.getSnapshotTo(new Keccak256(root));
        return this;
    }

    @Override
    public synchronized void updateAccountState(RskAddress addr, final AccountState accountState) {
        this.trie.put(addr.getBytes(), accountState.getEncoded());
    }

    @Nonnull
    private synchronized AccountState getAccountStateOrCreateNew(RskAddress addr) {
        AccountState account = getAccountState(addr);
        return (account == null) ? createAccount(addr) : account;
    }
}
