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

import co.rsk.db.ContractDetailsImpl;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.ethereum.crypto.SHA3Helper.sha3;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.util.ByteUtil.wrap;

/**
 * @author Roman Mandeleil
 * @since 17.11.2014
 */
public class RepositoryTrack implements Repository {
    private static final byte[] EMPTY_DATA_HASH = HashUtil.sha3(EMPTY_BYTE_ARRAY);
    private static final Logger logger = LoggerFactory.getLogger("repository");

    Map<ByteArrayWrapper, AccountState> cacheAccounts = new HashMap<>();
    Map<ByteArrayWrapper, ContractDetails> cacheDetails = new HashMap<>();

    DetailsDataStore dds = new DetailsDataStore();

    Repository repository;

    public RepositoryTrack(Repository repository) {
        this.repository = repository;
        dds.setDB(new DatabaseImpl(new HashMapDB()));
    }

    @Override
    public AccountState createAccount(byte[] addr) {

        synchronized (repository) {
            logger.trace("createAccount: [{}]", Hex.toHexString(addr));

            AccountState accountState = new AccountState();
            cacheAccounts.put(wrap(addr), accountState);

            ContractDetails contractDetails = new ContractDetailsCacheImpl(null);
            contractDetails.setDirty(true);
            cacheDetails.put(wrap(addr), contractDetails);

            return accountState;
        }
    }

    @Override
    public AccountState getAccountState(byte[] addr) {

        synchronized (repository) {

            AccountState accountState = cacheAccounts.get(wrap(addr));

            if (accountState == null) {
                repository.loadAccount(addr, cacheAccounts, cacheDetails);

                accountState = cacheAccounts.get(wrap(addr));
            }
            return accountState;
        }
    }

    @Override
    public boolean isExist(byte[] addr) {

        synchronized (repository) {
            AccountState accountState = cacheAccounts.get(wrap(addr));
            if (accountState != null)
                return !accountState.isDeleted();

            return repository.isExist(addr);
        }
    }

    @Override
    public ContractDetails getContractDetails(byte[] addr) {

        synchronized (repository) {
            ContractDetails contractDetails = cacheDetails.get(wrap(addr));

            if (contractDetails == null) {
                repository.loadAccount(addr, cacheAccounts, cacheDetails);
                contractDetails = cacheDetails.get(wrap(addr));
            }

            return contractDetails;
        }
    }

    @Override
    public void loadAccount(byte[] addr, Map<ByteArrayWrapper, AccountState> cacheAccounts,
                            Map<ByteArrayWrapper, ContractDetails> cacheDetails) {

        synchronized (repository) {
            AccountState accountState = this.cacheAccounts.get(wrap(addr));
            ContractDetails contractDetails = this.cacheDetails.get(wrap(addr));

            if (accountState == null) {
                repository.loadAccount(addr, this.cacheAccounts, this.cacheDetails);
                accountState = this.cacheAccounts.get(wrap(addr));
                contractDetails = this.cacheDetails.get(wrap(addr));
            }

            cacheAccounts.put(wrap(addr), accountState.clone());
            ContractDetails contractDetailsLvl2 = new ContractDetailsCacheImpl(contractDetails);
            cacheDetails.put(wrap(addr), contractDetailsLvl2);
        }
    }


    @Override
    public void delete(byte[] addr) {
        logger.trace("delete account: [{}]", Hex.toHexString(addr));

        synchronized (repository) {
            getAccountState(addr).setDeleted(true);
            getContractDetails(addr).setDeleted(true);
        }
    }

    @Override
    public BigInteger increaseNonce(byte[] addr) {

        synchronized (repository) {
            AccountState accountState = getAccountState(addr);

            if (accountState == null)
                accountState = createAccount(addr);

            getContractDetails(addr).setDirty(true);

            BigInteger saveNonce = accountState.getNonce();
            accountState.incrementNonce();

            logger.trace("increase nonce addr: [{}], from: [{}], to: [{}]", Hex.toHexString(addr),
                    saveNonce, accountState.getNonce());

            return accountState.getNonce();
        }
    }

    @Override
    public void hibernate(byte[] addr) {

        synchronized (repository) {
            AccountState accountState = getAccountState(addr);

            if (accountState == null)
                accountState = createAccount(addr);

            getContractDetails(addr).setDirty(true);

            accountState.hibernate();
        }
        logger.trace("hibernate addr: [{}]", Hex.toHexString(addr));
    }

    public BigInteger setNonce(byte[] addr, BigInteger bigInteger) {
        synchronized (repository) {
            AccountState accountState = getAccountState(addr);

            if (accountState == null)
                accountState = createAccount(addr);

            getContractDetails(addr).setDirty(true);

            BigInteger saveNonce = accountState.getNonce();
            accountState.setNonce(bigInteger);

            logger.trace("increase nonce addr: [{}], from: [{}], to: [{}]", Hex.toHexString(addr),
                    saveNonce, accountState.getNonce());

            return accountState.getNonce();
        }
    }


    @Override
    public BigInteger getNonce(byte[] addr) {
        AccountState accountState = getAccountState(addr);
        return accountState == null ? AccountState.EMPTY.getNonce() : accountState.getNonce();
    }

    @Override
    public BigInteger getBalance(byte[] addr) {
        AccountState accountState = getAccountState(addr);
        return accountState == null ? AccountState.EMPTY.getBalance() : accountState.getBalance();
    }

    @Override
    public BigInteger addBalance(byte[] addr, BigInteger value) {

        synchronized (repository) {
            AccountState accountState = getAccountState(addr);
            if (accountState == null) {
                accountState = createAccount(addr);
            }

            getContractDetails(addr).setDirty(true);
            BigInteger newBalance = accountState.addToBalance(value);

            logger.trace("adding to balance addr: [{}], balance: [{}], delta: [{}]", Hex.toHexString(addr),
                    newBalance, value);

            return newBalance;
        }
    }

    @Override
    public synchronized void setBlockNumberOfLastEvent(byte[] addr, long value) {
        synchronized (repository) {
            AccountState accountState = getAccountState(addr);
            if (accountState == null) {
                accountState = createAccount(addr);
            }

            getContractDetails(addr).setDirty(true);
            accountState.setBlockNumberOfLastEvent(value);

            logger.trace("setting block number of last event: [{}], value: [{}]", Hex.toHexString(addr), value);

        }
    }

    @Override
    public long getBlockNumberOfLastEvent(byte[] addr) {
        AccountState accountState = getAccountState(addr);
        return accountState == null ? AccountState.EMPTY.getBlockNumberOfLastEvent() : accountState.getBlockNumberOfLastEvent();
    }

    @Override
    public void saveCode(byte[] addr, byte[] code) {
        logger.trace("saving code addr: [{}], code: [{}]", Hex.toHexString(addr),
                Hex.toHexString(code));
        synchronized (repository) {
            getContractDetails(addr).setCode(code);
            getContractDetails(addr).setDirty(true);
            getAccountState(addr).setCodeHash(sha3(code));
        }
    }

    @Override
    public byte[] getCode(byte[] addr) {

        synchronized (repository) {
            if (!isExist(addr))
                return EMPTY_BYTE_ARRAY;

            byte[] codeHash = getAccountState(addr).getCodeHash();
            if (Arrays.equals(codeHash, EMPTY_DATA_HASH))
                return EMPTY_BYTE_ARRAY;

            return getContractDetails(addr).getCode();
        }
    }

    @Override
    public void addStorageRow(byte[] addr, DataWord key, DataWord value) {

        logger.trace("add storage row, addr: [{}], key: [{}] val: [{}]", Hex.toHexString(addr),
                key.toString(), value.toString());

        synchronized (repository) {
            getContractDetails(addr).put(key, value);
        }
    }

    @Override
    public void addStorageBytes(byte[] addr, DataWord key, byte[] value) {

        logger.trace("add storage bytes, addr: [{}], key: [{}]", Hex.toHexString(addr),
                key.toString());

        synchronized (repository) {
            getContractDetails(addr).putBytes(key, value);
        }
    }

    @Override
    public DataWord getStorageValue(byte[] addr, DataWord key) {
        synchronized (repository) {
            return getContractDetails(addr).get(key);
        }
    }

    @Override
    public byte[] getStorageBytes(byte[] addr, DataWord key) {
        synchronized (repository) {
            return getContractDetails(addr).getBytes(key);
        }
    }

    @Override
    public Set<ByteArrayWrapper> getAccountsKeys() {
        throw new UnsupportedOperationException();
    }


    public Set<ByteArrayWrapper> getFullAddressSet() {
        return cacheAccounts.keySet();
    }


    @Override
    public void dumpState(Block block, long gasUsed, int txNumber, byte[] txHash) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Repository startTracking() {
        logger.debug("start tracking");

        return new RepositoryTrack(this);
    }


    @Override
    public void flush() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void flushNoReconnect() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void commit() {

        synchronized (repository) {
            applyCacheDetailsChanges();

            repository.updateBatch(cacheAccounts, cacheDetails);
            cacheAccounts.clear();
            cacheDetails.clear();
            logger.debug("committed changes");
        }
    }

    public void applyCacheDetailsChanges(){
        synchronized (repository) {
            for (ContractDetails contractDetails : cacheDetails.values()) {

                ContractDetailsCacheImpl contractDetailsCache = (ContractDetailsCacheImpl) contractDetails;
                contractDetailsCache.commit();
            }
        }
    }

    @Override
    public void syncToRoot(byte[] root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rollback() {
        logger.debug("rollback changes");

        cacheAccounts.clear();
        cacheDetails.clear();
    }

    public void dumpChanges() {
        HashMap<ByteArrayWrapper, AccountState> accountStates = new HashMap<>();
        HashMap<ByteArrayWrapper, ContractDetails> contractDetails= new HashMap<>();
        updateBatch(accountStates,contractDetails);

        StringBuilder buf = new StringBuilder();
        buf.append("accountStates:\n");
        for (HashMap.Entry<ByteArrayWrapper, AccountState> entry : accountStates.entrySet()) {
            buf.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        }

        buf.append("contractDetails:\n");
        for (HashMap.Entry<ByteArrayWrapper, ContractDetails> entry : contractDetails.entrySet()) {
            buf.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        }

        logger.debug(buf.toString());
    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, AccountState> accountStates,
                            Map<ByteArrayWrapper, ContractDetails> contractDetails) {

        synchronized (repository) {
            for (Map.Entry<ByteArrayWrapper, AccountState> entry : accountStates.entrySet()) {
                cacheAccounts.put(entry.getKey(), entry.getValue());
            }

            for (Map.Entry<ByteArrayWrapper, ContractDetails> entry : contractDetails.entrySet()) {

                ContractDetailsCacheImpl contractDetailsCache = (ContractDetailsCacheImpl) entry.getValue();
                if (    contractDetailsCache.origContract != null
                        && !(contractDetailsCache.origContract instanceof ContractDetailsImpl))
                    cacheDetails.put(entry.getKey(), contractDetailsCache.origContract);
                else
                    cacheDetails.put(entry.getKey(), contractDetailsCache);
            }
        }
    }

    @Override // that's the idea track is here not for root calculations
    public byte[] getRoot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Repository getSnapshotTo(byte[] root) {
        throw new UnsupportedOperationException();
    }

    public Repository getOriginRepository() {
        return (repository instanceof RepositoryTrack)
                ? ((RepositoryTrack) repository).getOriginRepository()
                : repository;
    }

    @Override
    public DetailsDataStore getDetailsDataStore(){
        return dds;
    }

    @Override
    public void updateContractDetails(byte[] address, ContractDetails contractDetails) {
        synchronized (repository) {
            logger.trace("updateContractDetails: [{}]", Hex.toHexString(address));
            ContractDetails contractDetailsCache = new ContractDetailsCacheImpl(null);
            contractDetails.setDirty(true);
            cacheDetails.put(wrap(address), contractDetailsCache);
        }
    }

    @Override
    public void updateAccountState(byte[] address, AccountState accountState) {
        synchronized (repository) {
            logger.trace("updateAccountState: [{}]", Hex.toHexString(address));
            cacheAccounts.put(wrap(address), accountState);
        }
    }
}
