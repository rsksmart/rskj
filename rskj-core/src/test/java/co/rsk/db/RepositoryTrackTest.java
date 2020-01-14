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
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.*;

public class RepositoryTrackTest {
    private static Random random = new Random();

    private TrieKeyMapper trieKeyMapper;
    private RskAddress address;

    @Before
    public void setup() {
        this.trieKeyMapper = new TrieKeyMapper();
        this.address = createRandomAddress();
    }

    @Test
    public void createAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertFalse(repository.isExist(address));

        AccountState accountState = repository.createAccount(address);

        Assert.assertNotNull(accountState);

        Assert.assertTrue(repository.isExist(address));

        AccountState result = repository.getAccountState(address);

        Assert.assertNotNull(result);
        Assert.assertSame(accountState, result);
    }

    @Test
    public void getUnknownAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertFalse(repository.isExist(address));

        AccountState accountState = repository.getAccountState(address);

        Assert.assertNull(accountState);
    }

    @Test
    public void getAccountFromParent() {
        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(1000000));

        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        parent.updateAccountState(this.address, accountState);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertTrue(repository.isExist(address));

        AccountState result = repository.getAccountState(address);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(accountState.getEncoded(), result.getEncoded());
    }

    @Test
    public void getAccountFromParentAndCommit() {
        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(1000000));

        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        parent.updateAccountState(this.address, accountState);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertTrue(repository.isExist(address));

        AccountState result = repository.getAccountState(address);

        repository.commit();

        Assert.assertTrue(repository.isExist(address));
        Assert.assertTrue(parent.isExist(address));

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(accountState.getEncoded(), result.getEncoded());
        Assert.assertArrayEquals(accountState.getEncoded(), parent.getAccountState(this.address).getEncoded());
    }

    @Test
    public void createAndCommitAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertFalse(repository.isExist(address));

        AccountState accountState = repository.createAccount(address);

        Assert.assertTrue(repository.isExist(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));
        Assert.assertTrue(parent.isExist(address));

        Assert.assertNotNull(accountState);

        Assert.assertSame(trie, parent.getTrie());

        AccountState result = repository.getAccountState(address);

        Assert.assertNotNull(result);
        Assert.assertSame(accountState, result);

        AccountState result2 = parent.getAccountState(address);
        Assert.assertArrayEquals(result.getEncoded(), result2.getEncoded());
    }

    @Test
    public void getBalanceAndNonceFromUnknownAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertEquals(Coin.ZERO, repository.getBalance(address));
        Assert.assertEquals(BigInteger.ZERO, repository.getNonce(address));

        Assert.assertFalse(repository.isExist(address));

        repository.commit();

        Assert.assertFalse(repository.isExist(address));
        Assert.assertFalse(parent.isExist(address));
    }

    @Test
    public void getBalanceAndNonceFromKnownAccount() {
        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(42));
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        parent.updateAccountState(this.address, accountState);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.valueOf(42), repository.getBalance(address));
        Assert.assertEquals(BigInteger.TEN, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));
        Assert.assertTrue(parent.isExist(address));
    }

    @Test
    public void addBalanceToUnknownAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertFalse(repository.isExist(address));

        Assert.assertEquals(Coin.valueOf(42), repository.addBalance(address, Coin.valueOf(42)));

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.valueOf(42), repository.getBalance(address));
        Assert.assertEquals(BigInteger.ZERO, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));
        Assert.assertTrue(parent.isExist(address));

        AccountState accountState = parent.getAccountState(this.address);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(Coin.valueOf(42), accountState.getBalance());
        Assert.assertEquals(BigInteger.ZERO, accountState.getNonce());
    }

    @Test
    public void addBalanceToKnownAccount() {
        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(42));

        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        parent.updateAccountState(this.address, accountState);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.valueOf(50), repository.addBalance(address, Coin.valueOf(8)));

        Assert.assertEquals(Coin.valueOf(50), repository.getBalance(address));
        Assert.assertEquals(BigInteger.TEN, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));
        Assert.assertTrue(parent.isExist(address));

        AccountState newAccountState = parent.getAccountState(this.address);

        Assert.assertNotNull(newAccountState);
        Assert.assertEquals(Coin.valueOf(50), newAccountState.getBalance());
        Assert.assertEquals(BigInteger.TEN, newAccountState.getNonce());
    }

    @Test
    public void increaseNonceToUnknownAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertFalse(repository.isExist(address));

        Assert.assertEquals(BigInteger.ONE, repository.increaseNonce(address));

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.ZERO, repository.getBalance(address));
        Assert.assertEquals(BigInteger.ONE, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));
        Assert.assertTrue(parent.isExist(address));

        AccountState accountState = parent.getAccountState(this.address);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(Coin.ZERO, accountState.getBalance());
        Assert.assertEquals(BigInteger.ONE, accountState.getNonce());
    }

    @Test
    public void increaseNonceToKnownAccount() {
        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(42));

        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        parent.updateAccountState(this.address, accountState);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(BigInteger.valueOf(11), repository.increaseNonce((address)));

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.valueOf(42), repository.getBalance(address));
        Assert.assertEquals(BigInteger.valueOf(11), repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));
        Assert.assertTrue(parent.isExist(address));

        AccountState newAccountState = parent.getAccountState(this.address);

        Assert.assertNotNull(newAccountState);
        Assert.assertEquals(Coin.valueOf(42), newAccountState.getBalance());
        Assert.assertEquals(BigInteger.valueOf(11), newAccountState.getNonce());
    }

    @Test
    public void increaseNonceUsingUpdateAccountState() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        AccountState accountState = repository.createAccount(this.address);
        Assert.assertTrue(repository.isExist(address));
        accountState.incrementNonce();
        repository.updateAccountState(address, accountState);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.ZERO, repository.getBalance(address));
        Assert.assertEquals(BigInteger.ONE, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));
        Assert.assertTrue(parent.isExist(address));

        AccountState newAccountState = parent.getAccountState(this.address);

        Assert.assertNotNull(newAccountState);
        Assert.assertEquals(Coin.ZERO, newAccountState.getBalance());
        Assert.assertEquals(BigInteger.ONE, newAccountState.getNonce());
    }

    @Test
    public void setNonceToUnknownAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertFalse(repository.isExist(address));

        repository.setNonce(address, BigInteger.TEN);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.ZERO, repository.getBalance(address));
        Assert.assertEquals(BigInteger.TEN, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));
        Assert.assertTrue(parent.isExist(address));

        AccountState accountState = parent.getAccountState(this.address);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(Coin.ZERO, accountState.getBalance());
        Assert.assertEquals(BigInteger.TEN, accountState.getNonce());
    }

    @Test
    public void setNonceToKnownAccount() {
        AccountState accountState = new AccountState(BigInteger.ONE, Coin.valueOf(42));

        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        parent.updateAccountState(this.address, accountState);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertTrue(repository.isExist(address));

        repository.setNonce(address, BigInteger.TEN);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.valueOf(42), repository.getBalance(address));
        Assert.assertEquals(BigInteger.TEN, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));
        Assert.assertTrue(parent.isExist(address));

        AccountState newAccountState = parent.getAccountState(this.address);

        Assert.assertNotNull(newAccountState);
        Assert.assertEquals(Coin.valueOf(42), newAccountState.getBalance());
        Assert.assertEquals(BigInteger.TEN, newAccountState.getNonce());
    }

    @Test
    public void getStorageBytesFromUnknownAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertNull(repository.getStorageBytes(this.address, DataWord.ONE));

        Assert.assertFalse(repository.isExist(this.address));
    }

    @Test
    public void setAndGetStorageBytesFromUnknownAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        byte[] value = new byte[42];
        random.nextBytes(value);

        repository.addStorageBytes(this.address, DataWord.ONE, value);

        Assert.assertTrue(repository.isExist(this.address));

        byte[] result = repository.getStorageBytes(this.address, DataWord.ONE);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(value, result);

        repository.commit();

        Assert.assertArrayEquals(value, repository.getStorageBytes(address, DataWord.ONE));
        Assert.assertArrayEquals(value, parent.getStorageBytes(address, DataWord.ONE));
        Assert.assertTrue(parent.isContract(this.address));
    }

    @Test
    public void setAndGetEmptyStorageBytesFromUnknownAccount() {
        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        byte[] value = new byte[0];

        repository.addStorageBytes(this.address, DataWord.ONE, value);

        Assert.assertTrue(repository.isExist(this.address));

        Assert.assertNull(repository.getStorageBytes(this.address, DataWord.ONE));

        repository.commit();

        Assert.assertNull(repository.getStorageBytes(address, DataWord.ONE));
    }

    @Test
    public void getStorageBytesFromParent() {
        Trie trie = new Trie();

        byte[] value = new byte[42];
        random.nextBytes(value);

        TopRepository parent = new TopRepository(trie, null);
        parent.addStorageBytes(this.address, DataWord.ONE, value);
        RepositoryTrack repository = new RepositoryTrack(parent);

        byte[] result = repository.getStorageBytes(this.address, DataWord.ONE);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(value, result);

        repository.commit();

        Assert.assertArrayEquals(value, repository.getStorageBytes(address, DataWord.ONE));
        Assert.assertArrayEquals(value, parent.getStorageBytes(address, DataWord.ONE));
    }

    @Test
    public void changeStorageBytesFromParent() {
        Trie trie = new Trie();
        byte[] value = new byte[42];
        random.nextBytes(value);
        byte[] value2 = new byte[100];
        random.nextBytes(value2);

        TopRepository parent = new TopRepository(trie, null);
        parent.addStorageBytes(this.address, DataWord.ONE, value);
        RepositoryTrack repository = new RepositoryTrack(parent);

        byte[] result = repository.getStorageBytes(this.address, DataWord.ONE);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(value, result);

        repository.addStorageBytes(address, DataWord.ONE, value2);

        byte[] result2 = repository.getStorageBytes(this.address, DataWord.ONE);

        Assert.assertNotNull(result2);
        Assert.assertArrayEquals(value2, result2);

        repository.commit();

        Assert.assertArrayEquals(value2, repository.getStorageBytes(address, DataWord.ONE));
        Assert.assertArrayEquals(value2, parent.getStorageBytes(address, DataWord.ONE));
    }

    @Test
    public void getStorageBytesFromCreatedAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);

        Assert.assertNull(repository.getStorageBytes(this.address, DataWord.ONE));

        Assert.assertTrue(repository.isExist(this.address));
    }

    @Test
    public void getStorageValueFromUnknownAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));

        Assert.assertFalse(repository.isExist(this.address));
    }

    @Test
    public void getStorageValueFromCreatedAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);

        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));

        Assert.assertTrue(repository.isExist(this.address));
    }

    @Test
    public void setAndGetStorageValueFromUnknownAccount() {
        Trie trie = new Trie();
        DataWord value = DataWord.valueOf(42);

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.addStorageRow(this.address, DataWord.ONE, value);

        Assert.assertTrue(repository.isExist(this.address));

        DataWord result = repository.getStorageValue(this.address, DataWord.ONE);

        Assert.assertNotNull(result);
        Assert.assertEquals(value, result);

        repository.commit();

        Assert.assertEquals(value, repository.getStorageValue(address, DataWord.ONE));
        Assert.assertEquals(value, parent.getStorageValue(address, DataWord.ONE));
        Assert.assertTrue(repository.isContract(this.address));
        Assert.assertTrue(parent.isContract(this.address));
    }

    @Test
    public void getStorageValueFromParent() {
        Trie trie = new Trie();
        DataWord value = DataWord.valueOf(42);

        TopRepository parent = new TopRepository(trie, null);
        parent.addStorageRow(this.address, DataWord.ONE, value);
        RepositoryTrack repository = new RepositoryTrack(parent);

        DataWord result = repository.getStorageValue(this.address, DataWord.ONE);

        Assert.assertNotNull(result);
        Assert.assertEquals(value, result);

        repository.commit();

        Assert.assertEquals(value, repository.getStorageValue(address, DataWord.ONE));
        Assert.assertEquals(value, parent.getStorageValue(address, DataWord.ONE));
    }

    @Test
    public void changeStorageValueFromTrie() {
        Trie trie = new Trie();
        DataWord value = DataWord.valueOf(42);
        DataWord value2 = DataWord.valueOf(100);

        TopRepository parent = new TopRepository(trie, null);
        parent.addStorageRow(this.address, DataWord.ONE, value);
        RepositoryTrack repository = new RepositoryTrack(parent);

        DataWord result = repository.getStorageValue(this.address, DataWord.ONE);

        Assert.assertNotNull(result);
        Assert.assertEquals(value, result);

        repository.addStorageRow(address, DataWord.ONE, value2);

        DataWord result2 = repository.getStorageValue(this.address, DataWord.ONE);

        Assert.assertNotNull(result2);
        Assert.assertEquals(value2, result2);

        repository.commit();

        Assert.assertEquals(value2, repository.getStorageValue(address, DataWord.ONE));
        Assert.assertEquals(value2, parent.getStorageValue(address, DataWord.ONE));
    }

    @Test
    public void getCodeFromUnknownAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertFalse(repository.isExist(this.address));

        byte[] result = repository.getCode(this.address);

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length);
    }

    @Test
    public void getCodeFromCreatedAccountWithoutCode() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);

        Assert.assertNull(repository.getCode(this.address));
    }

    @Test
    public void getCodeFromParent() {
        byte[] code = new byte[32];
        random.nextBytes(code);

        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        parent.saveCode(this.address, code);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);

        byte[] result = repository.getCode(this.address);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(code, result);

        byte[] result2 = repository.getCode(this.address);

        Assert.assertNotNull(result2);
        Assert.assertArrayEquals(code, result2);
    }

    @Test
    public void setupContractOnCreatedAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);

        Assert.assertFalse(repository.isContract(this.address));

        repository.setupContract(this.address);

        Assert.assertTrue(repository.isContract(this.address));

        repository.commit();

        Assert.assertTrue(repository.isContract(this.address));
        Assert.assertTrue(parent.isContract(this.address));
    }

    @Test
    public void createAccountAndRollback() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertFalse(repository.isExist(this.address));

        repository.createAccount(this.address);

        Assert.assertTrue(repository.isExist(this.address));

        repository.rollback();

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertFalse(parent.isExist(this.address));
    }

    @Test
    public void setupContractAndRollback() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertFalse(repository.isExist(this.address));

        repository.createAccount(this.address);
        repository.setupContract(this.address);

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertTrue(repository.isContract(this.address));

        repository.rollback();

        Assert.assertFalse(repository.isContract(this.address));
        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertFalse(parent.isContract(this.address));
        Assert.assertFalse(parent.isExist(this.address));
    }

    @Test
    public void addStorageAndRollback() {
        Trie trie = new Trie();
        byte[] data = new byte[42];
        random.nextBytes(data);

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertFalse(repository.isExist(this.address));

        repository.createAccount(this.address);
        repository.addStorageBytes(this.address, DataWord.ONE, data);
        repository.addStorageRow(this.address, DataWord.ZERO, DataWord.ONE);

        Assert.assertArrayEquals(data, repository.getStorageBytes(this.address, DataWord.ONE));
        Assert.assertEquals(DataWord.ONE, repository.getStorageValue(this.address, DataWord.ZERO));

        repository.rollback();
        repository.commit();

        Assert.assertNull(repository.getStorageBytes(this.address, DataWord.ONE));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ZERO));
        Assert.assertNull(parent.getStorageBytes(this.address, DataWord.ONE));
        Assert.assertNull(parent.getStorageValue(this.address, DataWord.ZERO));
    }

    @Test
    public void saveCode() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        byte[] code = new byte[42];
        random.nextBytes(code);

        repository.saveCode(this.address, code);

        Assert.assertArrayEquals(code, repository.getCode(this.address));
        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertTrue(repository.isContract(this.address));
    }

    @Test
    public void saveCodeAndCommit() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        byte[] code = new byte[42];
        random.nextBytes(code);

        repository.saveCode(this.address, code);

        Assert.assertArrayEquals(code, repository.getCode(address));
        Assert.assertTrue(repository.isExist(address));

        repository.commit();

        Assert.assertArrayEquals(code, repository.getCode(address));
        Assert.assertArrayEquals(code, parent.getCode(address));
        Assert.assertTrue(repository.isExist(address));
        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertTrue(repository.isContract(this.address));

        Assert.assertTrue(parent.isExist(address));
        Assert.assertTrue(parent.isContract(this.address));
    }

    @Test
    public void saveCodeAndRollback() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        byte[] code = new byte[42];
        random.nextBytes(code);

        repository.saveCode(this.address, code);

        Assert.assertArrayEquals(code, repository.getCode(address));
        Assert.assertTrue(repository.isExist(address));

        repository.rollback();

        Assert.assertArrayEquals(new byte[0], repository.getCode(address));
        Assert.assertFalse(repository.isExist(address));

        Assert.assertArrayEquals(new byte[0], parent.getCode(address));

        Assert.assertFalse(parent.isExist(address));
        Assert.assertFalse(parent.isContract(this.address));
    }

    @Test
    public void saveNullCode() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.saveCode(this.address, null);

        Assert.assertArrayEquals(new byte[0], repository.getCode(address));
        Assert.assertFalse(repository.isExist(address));
    }

    @Test
    public void saveEmptyCodeOnExistingAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);

        repository.saveCode(this.address, new byte[0]);

        Assert.assertNull(repository.getCode(address));
        Assert.assertTrue(repository.isExist(address));
    }

    @Test
    public void hibernateUnknownAccount() {
        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.hibernate(this.address);

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertNotNull(repository.getAccountState(this.address).isHibernated());

        repository.commit();

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertNotNull(repository.getAccountState(this.address).isHibernated());
        Assert.assertTrue(parent.isExist(this.address));
        Assert.assertNotNull(parent.getAccountState(this.address).isHibernated());
    }

    @Test
    public void hibernateUnknownAccountAndRollback() {
        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.hibernate(this.address);

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertNotNull(repository.getAccountState(this.address).isHibernated());

        repository.rollback();

        Assert.assertSame(trie, parent.getTrie());

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertFalse(parent.isExist(this.address));
    }

    @Test
    public void hibernateKnownAccount() {
        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        parent.createAccount(this.address);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertTrue(repository.isExist(this.address));

        repository.hibernate(this.address);

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertTrue(repository.getAccountState(this.address).isHibernated());

        repository.commit();

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertTrue(repository.getAccountState(this.address).isHibernated());
        Assert.assertTrue(parent.isExist(this.address));
        Assert.assertTrue(parent.getAccountState(this.address).isHibernated());
    }

    @Test
    public void hibernateKnownAccountAndRollback() {
        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        parent.createAccount(this.address);
        Assert.assertFalse(parent.getAccountState(this.address).isHibernated());
        RepositoryTrack repository = new RepositoryTrack(parent);
        Assert.assertFalse(repository.getAccountState(this.address).isHibernated());

        Assert.assertTrue(repository.isExist(this.address));

        repository.hibernate(this.address);

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertTrue(repository.getAccountState(this.address).isHibernated());
        Assert.assertTrue(parent.isExist(this.address));
        Assert.assertFalse(parent.getAccountState(this.address).isHibernated());

        repository.rollback();

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertFalse(repository.getAccountState(this.address).isHibernated());
        Assert.assertTrue(parent.isExist(this.address));
        Assert.assertFalse(parent.getAccountState(this.address).isHibernated());
    }

    @Test
    public void createAndDeleteAccount() {
        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);

        Assert.assertTrue(repository.isExist(this.address));

        repository.delete(this.address);

        Assert.assertFalse(repository.isExist(this.address));

        repository.commit();

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertFalse(parent.isExist(this.address));
        Assert.assertSame(trie, parent.getTrie());
    }

    @Test
    public void createAndDeleteAccountAddingStorageAndCode() {
        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        Repository repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);

        Assert.assertTrue(repository.isExist(this.address));

        repository.addStorageRow(this.address, DataWord.ONE, DataWord.valueOf(42));
        repository.saveCode(this.address, new byte[1]);

        repository.delete(this.address);

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));

        Assert.assertEquals(0, repository.getCodeLength(this.address));

        repository.commit();

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));

        Assert.assertFalse(parent.isExist(this.address));
        Assert.assertNull(parent.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], parent.getCode(this.address));

        repository = parent.startTracking();

        Assert.assertEquals(0, repository.getCodeLength(this.address));
    }

    @Test
    public void deleteKnownAccountWithStorageAndCode() {
        AccountState accountState = new AccountState();

        Trie trie = new Trie();

        trie = trie.put(this.trieKeyMapper.getAccountKey(address), accountState.getEncoded());

        TopRepository parent = new TopRepository(trie, null);
        Assert.assertTrue(parent.isExist(this.address));

        parent.saveCode(this.address, new byte[1]);
        parent.addStorageRow(this.address, DataWord.ONE, DataWord.valueOf(42));
        Repository repository = new RepositoryTrack(parent);

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertEquals(DataWord.valueOf(42), repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[1], repository.getCode(this.address));

        repository.delete(this.address);

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));

        repository.commit();

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));

        Assert.assertFalse(parent.isExist(this.address));
        Assert.assertNull(parent.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], parent.getCode(this.address));

        repository = parent.startTracking();

        Assert.assertEquals(0, repository.getCodeLength(this.address));
        Assert.assertEquals(0, parent.getCodeLength(this.address));
    }

    @Test
    public void createAndDeleteAccountAddingStorageWithRollback() {
        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);

        Assert.assertTrue(repository.isExist(this.address));

        repository.addStorageRow(this.address, DataWord.ONE, DataWord.valueOf(42));
        repository.saveCode(this.address, new byte[1]);

        repository.delete(this.address);

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));

        repository.rollback();

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));

        Assert.assertFalse(parent.isExist(this.address));
        Assert.assertNull(parent.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], parent.getCode(this.address));
    }

    @Test
    public void deleteKnownAccount() {
        AccountState accountState = new AccountState();

        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        parent.updateAccountState(this.address, accountState);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertTrue(repository.isExist(this.address));

        repository.delete(this.address);

        Assert.assertFalse(repository.isExist(this.address));

        repository.commit();

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));

        Assert.assertFalse(parent.isExist(this.address));
        Assert.assertNull(parent.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], parent.getCode(this.address));
    }

    @Test
    public void deleteKnownAccountAndRollback() {
        AccountState accountState = new AccountState();

        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie, null);
        parent.updateAccountState(this.address, accountState);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertTrue(repository.isExist(this.address));

        repository.delete(this.address);

        Assert.assertFalse(repository.isExist(this.address));

        repository.rollback();

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertNull(repository.getCode(this.address));

        Assert.assertTrue(parent.isExist(this.address));
        Assert.assertNull(parent.getStorageValue(this.address, DataWord.ONE));
        Assert.assertNull(parent.getCode(this.address));
    }


    @Test
    public void getCodeLengthAndHashFromUnknownAccount() {
        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertEquals(0, repository.getCodeLength(this.address));
        Assert.assertEquals(Keccak256.ZERO_HASH, repository.getCodeHash(this.address));
    }

    @Test
    public void saveCodeAndGetCodeLengthAndHashFromUnknownAccount() {
        byte[] code = new byte[] { 0x01, 0x02, 0x03, 0x04 };

        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.saveCode(this.address, code);

        Assert.assertEquals(4, repository.getCodeLength(this.address));
        Assert.assertEquals(new Keccak256(Keccak256Helper.keccak256(code)), repository.getCodeHash(this.address));
    }

    @Test
    public void createAccountsAndGetAccountKeys() {
        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        Repository repository = new RepositoryTrack(parent);

        Assert.assertNotNull(repository.getAccountsKeys());
        Assert.assertTrue(repository.getAccountsKeys().isEmpty());

        List<RskAddress> addresses = createRandomAddresses(10);

        for (RskAddress address : addresses) {
            repository.createAccount(address);
        }

        Set<RskAddress> result = repository.getAccountsKeys();

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(10, result.size());

        for (RskAddress address : addresses) {
            Assert.assertTrue(result.contains(address));
        }
    }

    @Test
    public void createAccountsInTrieAndGetAccountKeys() {
        Trie trie = new Trie();

        List<RskAddress> addresses = createRandomAddresses(10);

        for (RskAddress address : addresses) {
            trie = trie.put(this.trieKeyMapper.getAccountKey(address), new AccountState().getEncoded());
        }

        TopRepository parent = new TopRepository(trie, null);
        Repository repository = new RepositoryTrack(parent);

        Set<RskAddress> result = repository.getAccountsKeys();

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(10, result.size());

        for (RskAddress address : addresses) {
            Assert.assertTrue(result.contains(address));
        }
    }

    @Test
    public void createAccountsInTrieAndInRepositoryAndGetAccountKeys() {
        Trie trie = new Trie();

        List<RskAddress> addresses = createRandomAddresses(10);
        List<RskAddress> addresses2 = createRandomAddresses(10);

        for (RskAddress address : addresses) {
            trie = trie.put(this.trieKeyMapper.getAccountKey(address), new AccountState().getEncoded());
        }

        TopRepository parent = new TopRepository(trie, null);
        Repository repository = new RepositoryTrack(parent);

        for (RskAddress address : addresses2) {
            repository.createAccount(address);
        }

        Set<RskAddress> result = repository.getAccountsKeys();

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(20, result.size());

        for (RskAddress address : addresses) {
            Assert.assertTrue(result.contains(address));
        }

        for (RskAddress address : addresses2) {
            Assert.assertTrue(result.contains(address));
        }
    }

    @Test
    public void createAccountsInTrieDeleteAccountInRepositoryAndGetAccountKeys() {
        Trie trie = new Trie();

        List<RskAddress> addresses = createRandomAddresses(10);

        for (RskAddress address : addresses) {
            trie = trie.put(this.trieKeyMapper.getAccountKey(address), new AccountState().getEncoded());
        }

        TopRepository parent = new TopRepository(trie, null);
        Repository repository = new RepositoryTrack(parent);

        repository.delete(addresses.get(9));

        Set<RskAddress> result = repository.getAccountsKeys();

        Assert.assertNotNull(result);
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(9, result.size());

        for (int k = 0; k < 9; k++) {
            RskAddress address = addresses.get(k);
            Assert.assertTrue(result.contains(address));
        }
    }

    @Test
    public void getStorageKeysFromUnknownAccount() {
        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        Repository repository = new RepositoryTrack(parent);

        Iterator<DataWord> result = repository.getStorageKeys(this.address);

        Assert.assertNotNull(result);
        Assert.assertFalse(result.hasNext());

        Assert.assertEquals(0, repository.getStorageKeysCount(this.address));
    }

    @Test
    public void getStorageKeysFromCreatedAccountWithStorage() {
        Trie trie = new Trie();
        TopRepository parent = new TopRepository(trie, null);
        Repository repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);
        repository.addStorageRow(this.address, DataWord.valueOf(10), DataWord.valueOf(11));
        repository.addStorageRow(this.address, DataWord.valueOf(11), DataWord.valueOf(12));

        Iterator<DataWord> result = repository.getStorageKeys(this.address);

        Assert.assertNotNull(result);

        List<DataWord> list = iteratorToList(result);

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());
        Assert.assertTrue(list.contains(DataWord.valueOf(10)));
        Assert.assertTrue(list.contains(DataWord.valueOf(11)));

        Assert.assertEquals(2, repository.getStorageKeysCount(this.address));
    }

    @Test
    public void getStorageKeysFromAccountWithStorageInTrie() {
        Trie trie = new Trie();

        trie = trie.put(this.trieKeyMapper.getAccountKey(this.address), new AccountState().getEncoded());
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.valueOf(10)), DataWord.valueOf(100).getByteArrayForStorage());
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.valueOf(11)), DataWord.valueOf(101).getByteArrayForStorage());

        TopRepository parent = new TopRepository(trie, null);
        Repository repository = new RepositoryTrack(parent);

        Iterator<DataWord> result = repository.getStorageKeys(this.address);

        Assert.assertNotNull(result);

        List<DataWord> list = iteratorToList(result);

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());
        Assert.assertTrue(list.contains(DataWord.valueOf(10)));
        Assert.assertTrue(list.contains(DataWord.valueOf(11)));

        Assert.assertEquals(2, repository.getStorageKeysCount(this.address));
    }

    @Test
    public void getStorageKeysFromDeletedAccountWithStorageInTrie() {
        Trie trie = new Trie();

        trie = trie.put(this.trieKeyMapper.getAccountKey(this.address), new AccountState().getEncoded());
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.valueOf(10)), DataWord.valueOf(100).getByteArrayForStorage());
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.valueOf(11)), DataWord.valueOf(101).getByteArrayForStorage());

        TopRepository parent = new TopRepository(trie, null);
        Repository repository = new RepositoryTrack(parent);

        repository.delete(this.address);

        Iterator<DataWord> result = repository.getStorageKeys(this.address);

        Assert.assertNotNull(result);

        List<DataWord> list = iteratorToList(result);

        Assert.assertNotNull(list);
        Assert.assertTrue(list.isEmpty());

        Assert.assertEquals(0, repository.getStorageKeysCount(this.address));
    }

    @Test
    public void getStorageKeysFromAccountWithStorageInTrieAndDeleteOneRow() {
        Trie trie = new Trie();

        trie = trie.put(this.trieKeyMapper.getAccountKey(this.address), new AccountState().getEncoded());
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.valueOf(10)), DataWord.valueOf(100).getByteArrayForStorage());
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.valueOf(11)), DataWord.valueOf(101).getByteArrayForStorage());

        TopRepository parent = new TopRepository(trie, null);
        Repository repository = new RepositoryTrack(parent);

        repository.addStorageRow(this.address, DataWord.valueOf(10), DataWord.ZERO);

        Iterator<DataWord> result = repository.getStorageKeys(this.address);

        Assert.assertNotNull(result);

        List<DataWord> list = iteratorToList(result);

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(1, list.size());
        Assert.assertTrue(list.contains(DataWord.valueOf(11)));
    }

    @Test
    public void getStorageKeysFromAccountWithStorageInTrieAndAddRow() {
        Trie trie = new Trie();

        trie = trie.put(this.trieKeyMapper.getAccountKey(this.address), new AccountState().getEncoded());
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.valueOf(10)), DataWord.valueOf(100).getByteArrayForStorage());
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.valueOf(11)), DataWord.valueOf(101).getByteArrayForStorage());

        TopRepository parent = new TopRepository(trie, null);
        Repository repository = new RepositoryTrack(parent);

        repository.addStorageRow(this.address, DataWord.valueOf(12), DataWord.ONE);

        Iterator<DataWord> result = repository.getStorageKeys(this.address);

        Assert.assertNotNull(result);

        List<DataWord> list = iteratorToList(result);

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(3, list.size());
        Assert.assertTrue(list.contains(DataWord.valueOf(10)));
        Assert.assertTrue(list.contains(DataWord.valueOf(11)));
        Assert.assertTrue(list.contains(DataWord.valueOf(12)));

        Assert.assertEquals(3, repository.getStorageKeysCount(this.address));
    }

    private static <T> List<T> iteratorToList(Iterator<T> iterator) {
        List<T> list = new ArrayList<>();

        while (iterator.hasNext()) {
            list.add(iterator.next());
        }

        return list;
    }

    private static List<RskAddress> createRandomAddresses(int n) {
        List<RskAddress> addresses = new ArrayList<>();

        for (int k = 0; k < n; k++) {
            addresses.add(createRandomAddress());
        }

        return addresses;
    }

    private static RskAddress createRandomAddress() {
        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        random.nextBytes(bytes);

        return new RskAddress(bytes);
    }
}
