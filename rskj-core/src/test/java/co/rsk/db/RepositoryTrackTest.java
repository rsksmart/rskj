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
import co.rsk.trie.Trie;
import co.rsk.trie.TrieHashTest;
import org.ethereum.core.AccountState;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Random;

public class RepositoryTrackTest {
    private static Random random = new Random();

    private RskAddress address;

    @Before
    public void setup() {
        this.address = createRandomAddress();
    }

    @Test
    public void createAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertFalse(repository.isExist(address));

        AccountState accountState = repository.getAccountState(address);

        Assert.assertNull(accountState);
    }

    @Test
    public void getAccountFromParent() {
        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(1000000));

        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertNull(repository.getStorageBytes(this.address, DataWord.ONE));

        Assert.assertFalse(repository.isExist(this.address));
    }

    @Test
    public void setAndGetStorageBytesFromUnknownAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie);
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
    public void getStorageBytesFromParent() {
        Trie trie = new Trie();

        byte[] value = new byte[42];
        random.nextBytes(value);

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);

        Assert.assertNull(repository.getStorageBytes(this.address, DataWord.ONE));

        Assert.assertTrue(repository.isExist(this.address));
    }

    @Test
    public void getStorageValueFromUnknownAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));

        Assert.assertFalse(repository.isExist(this.address));
    }

    @Test
    public void getStorageValueFromCreatedAccount() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);

        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));

        Assert.assertTrue(repository.isExist(this.address));
    }

    @Test
    public void setAndGetStorageValueFromUnknownAccount() {
        Trie trie = new Trie();
        DataWord value = DataWord.valueOf(42);

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
        RepositoryTrack repository = new RepositoryTrack(parent);

        Assert.assertFalse(repository.isExist(this.address));

        byte[] result = repository.getCode(this.address);

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length);
    }

    @Test
    public void getCodeFromCreatedAccountWithoutCode() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.createAccount(this.address);

        Assert.assertNull(repository.getCode(this.address));
    }

    @Test
    public void getCodeFromParent() {
        byte[] code = new byte[32];
        random.nextBytes(code);

        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
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

        TopRepository parent = new TopRepository(trie);
        RepositoryTrack repository = new RepositoryTrack(parent);

        byte[] code = new byte[42];
        random.nextBytes(code);

        repository.saveCode(this.address, code);

        Assert.assertArrayEquals(code, repository.getCode(address));
        Assert.assertTrue(repository.isExist(address));
    }

    @Test
    public void saveCodeAndCommit() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie);
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
        Assert.assertTrue(parent.isExist(address));
    }

    @Test
    public void saveCodeAndRollback() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie);
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
    }

    @Test
    public void saveNullCode() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.saveCode(this.address, null);

        Assert.assertArrayEquals(new byte[0], repository.getCode(address));
        Assert.assertFalse(repository.isExist(address));
    }

    @Test
    public void saveEmptyCode() {
        Trie trie = new Trie();

        TopRepository parent = new TopRepository(trie);
        RepositoryTrack repository = new RepositoryTrack(parent);

        repository.saveCode(this.address, new byte[0]);

        Assert.assertArrayEquals(new byte[0], repository.getCode(address));
        Assert.assertFalse(repository.isExist(address));
    }

    private static RskAddress createRandomAddress() {
        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        random.nextBytes(bytes);

        return new RskAddress(bytes);
    }
}
