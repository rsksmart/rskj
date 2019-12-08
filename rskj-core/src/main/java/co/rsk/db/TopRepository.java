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

public class TopRepository implements Repository {
    private Trie trie;

    private final TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

    private final Map<RskAddress, AccountState> accountStates = new HashMap<>();
    private final Set<RskAddress> modifiedAccounts = new HashSet<>();

    public TopRepository(Trie trie) {
        this.trie = trie;
    }

    @Override
    public Trie getTrie() {
        return this.trie;
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
    public void setupContract(RskAddress addr) {

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
    public void saveCode(RskAddress addr, byte[] code) {

    }

    @Override
    public void addStorageRow(RskAddress addr, DataWord key, DataWord value) {

    }

    @Override
    public void addStorageBytes(RskAddress addr, DataWord key, byte[] value) {

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
        for (RskAddress address : this.modifiedAccounts) {
            AccountState accountState = this.accountStates.get(address);
            this.trie = this.trie.put(this.trieKeyMapper.getAccountKey(address), accountState.getEncoded());
        }

        modifiedAccounts.clear();
    }

    @Override
    public void rollback() {

    }

    @Override
    public void save() {

    }

    @Override
    public void updateAccountState(RskAddress addr, AccountState accountState) {

    }

    @Override
    public byte[] getRoot() {
        return new byte[0];
    }

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

        byte[] data = this.trie.get(this.trieKeyMapper.getAccountKey(address));

        if (data == null || data.length == 0) {
            return null;
        }

        accountState = new AccountState(data);

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
    public DataWord getStorageValue(RskAddress addr, DataWord key) {
        return null;
    }

    @Nullable
    @Override
    public byte[] getStorageBytes(RskAddress addr, DataWord key) {
        return null;
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
    public byte[] getCode(RskAddress addr) {
        return new byte[0];
    }

    @Override
    public boolean isContract(RskAddress addr) {
        return false;
    }

    @Override
    public BigInteger getNonce(RskAddress address) {
        AccountState accountState = this.getAccountState(address);

        if (accountState == null) {
            return BigInteger.ZERO;
        }

        return accountState.getNonce();
    }
}
