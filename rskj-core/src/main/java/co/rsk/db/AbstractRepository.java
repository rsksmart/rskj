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

package co.rsk.db;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;

/**
 * Created by ajlopez on 09/12/2019.
 */
public abstract class AbstractRepository implements Repository {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final Map<RskAddress, AccountState> accountStates = new HashMap<>();
    private final Set<RskAddress> modifiedAccounts = new HashSet<>();

    private final Set<RskAddress> contracts = new HashSet<>();

    private final Map<RskAddress, Map<DataWord, byte[]>> storage = new HashMap<>();
    private final Map<RskAddress, Map<DataWord, Boolean>> modifiedStorage = new HashMap<>();
    private final Map<RskAddress, byte[]> codes = new HashMap<>();

    @Override
    public Trie getTrie() {
        return null;
    }

    // TODO synchronized?
    @Override
    public AccountState createAccount(RskAddress address) {
        AccountState accountState = new AccountState();

        this.accountStates.put(address, accountState);
        this.modifiedAccounts.add(address);

        return accountState;
    }

    @Override
    public void setupContract(RskAddress address) {
        this.contracts.add(address);
    }

    @Override
    public void delete(RskAddress addr) {

    }

    @Override
    public void hibernate(RskAddress addr) {

    }

    @Override
    public BigInteger increaseNonce(RskAddress address) {
        AccountState accountState = this.getOrCreateAccountState(address);

        accountState.incrementNonce();
        this.modifiedAccounts.add(address);

        return accountState.getNonce();
    }

    @Override
    public void setNonce(RskAddress address, BigInteger nonce) {
        AccountState accountState = this.getOrCreateAccountState(address);

        accountState.setNonce(nonce);

        this.modifiedAccounts.add(address);
    }

    @Override
    public void saveCode(RskAddress address, byte[] code) {
        this.codes.put(address, code);

        if (code != null && code.length != 0 && !this.isExist(address)) {
            this.createAccount(address);
        }
    }

    @Override
    public void addStorageRow(RskAddress address, DataWord key, DataWord value) {
        this.addStorageBytes(address, key, value.getByteArrayForStorage());
    }

    @Override
    public void addStorageBytes(RskAddress address, DataWord key, byte[] value) {
        if (!this.isExist(address)) {
            this.createAccount(address);
            this.setupContract(address);
        }

        if (!this.storage.containsKey(address)) {
            this.storage.put(address, new HashMap<>());
        }

        if (!this.modifiedStorage.containsKey(address)) {
            this.modifiedStorage.put(address, new HashMap<>());
        }

        this.storage.get(address).put(key, value);
        this.modifiedStorage.get(address).put(key, true);
    }

    @Override
    public Coin addBalance(RskAddress address, Coin value) {
        AccountState accountState = this.getOrCreateAccountState(address);

        Coin newBalance = accountState.addToBalance(value);

        this.modifiedAccounts.add(address);

        return newBalance;
    }

    private AccountState getOrCreateAccountState(RskAddress address) {
        AccountState accountState = this.getAccountState(address);

        if (accountState != null) {
            return accountState;
        }

        return this.createAccount(address);
    }

    @Override
    public void commit() {
        for (Map.Entry<RskAddress, byte[]> entry : this.codes.entrySet()) {
            this.commitCode(entry.getKey(), entry.getValue());
        }

        for (RskAddress address : this.modifiedAccounts) {
            AccountState accountState = this.accountStates.get(address);
            this.commitAccountState(address, accountState);
        }

        for (RskAddress address : this.contracts) {
            this.commitContract(address);
        }

        for (Map.Entry<RskAddress, Map<DataWord, Boolean>> entry : this.modifiedStorage.entrySet()) {
            for (Map.Entry<DataWord, Boolean> entry2 : this.modifiedStorage.get(entry.getKey()).entrySet()) {
                this.commitStorage(entry.getKey(), entry2.getKey(), this.storage.get(entry.getKey()).get(entry2.getKey()));
            }
        }

        this.modifiedStorage.clear();
        this.modifiedAccounts.clear();
        this.contracts.clear();
    }

    @Override
    public void rollback() {
        this.accountStates.clear();
        this.storage.clear();
        this.codes.clear();

        this.modifiedAccounts.clear();
        this.modifiedStorage.clear();
        this.contracts.clear();
    }

    @Override
    public void save() {

    }

    @Override
    public void updateAccountState(RskAddress address, AccountState accountState) {
        this.accountStates.put(address, accountState);
        this.modifiedAccounts.add(address);
    }

    @Override
    public abstract byte[] getRoot();

    @Override
    public Set<RskAddress> getAccountsKeys() {
        return null;
    }

    @Override
    public int getCodeLength(RskAddress addr) {
        return 0;
    }

    @Override
    public Keccak256 getCodeHash(RskAddress addr) {
        return null;
    }

    @Override
    public boolean isExist(RskAddress address) {
        if (this.accountStates.containsKey(address)) {
            return true;
        }

        return this.getAccountState(address) != null;
    }

    @Override
    public AccountState getAccountState(RskAddress address) {
        AccountState accountState = this.accountStates.get(address);

        if (accountState != null) {
            return accountState;
        }

        accountState = this.retrieveAccountState(address);

        if (accountState == null) {
            return null;
        }

        this.accountStates.put(address, accountState);

        return accountState;
    }

    @Override
    public Repository startTracking() {
        return null;
    }

    @Override
    public Coin getBalance(RskAddress address) {
        AccountState accountState = this.getAccountState(address);

        if (accountState == null) {
            return Coin.ZERO;
        }

        return accountState.getBalance();
    }

    @Nullable
    @Override
    public DataWord getStorageValue(RskAddress address, DataWord key) {
        byte[] bytes = this.getStorageBytes(address, key);

        if (bytes == null) {
            return null;
        }

        return DataWord.valueOf(bytes);
    }

    @Nullable
    @Override
    public byte[] getStorageBytes(RskAddress address, DataWord key) {
        if (this.storage.containsKey(address) && this.storage.get(address).containsKey(key)) {
            return this.storage.get(address).get(key);
        }

        byte[] value = this.retrieveStorageBytes(address, key);

        if (!this.storage.containsKey(address)) {
            this.storage.put(address, new HashMap<>());
        }

        this.storage.get(address).put(key, value);

        return value;
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        return null;
    }

    @Override
    public int getStorageKeysCount(RskAddress addr) {
        return 0;
    }

    @Nullable
    @Override
    public byte[] getCode(RskAddress address) {
        if (!this.isExist(address)) {
            return EMPTY_BYTE_ARRAY;
        }

        if (this.codes.containsKey(address)) {
            return this.codes.get(address);
        }

        // TODO check account is hibernated

        byte[] code = this.retrieveCode(address);

        if (code != null) {
            this.codes.put(address, code);
        }

        return code;
    }

    @Override
    public boolean isContract(RskAddress address) {
        if (this.contracts.contains(address)) {
            return true;
        }

        return this.retrieveIsContract(address);
    }

    @Override
    public BigInteger getNonce(RskAddress address) {
        AccountState accountState = this.getAccountState(address);

        if (accountState == null) {
            return BigInteger.ZERO;
        }

        return accountState.getNonce();
    }

    public abstract AccountState retrieveAccountState(RskAddress address);

    public abstract boolean retrieveIsContract(RskAddress address);

    public abstract byte[] retrieveStorageBytes(RskAddress address, DataWord key);

    public abstract byte[] retrieveCode(RskAddress address);

    public abstract void commitAccountState(RskAddress address, AccountState accountState);

    public abstract void commitContract(RskAddress address);

    public abstract void commitStorage(RskAddress address, DataWord key, byte[] value);

    public abstract void commitCode(RskAddress address, byte[] code);
}
