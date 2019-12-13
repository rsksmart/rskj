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
import co.rsk.trie.TrieHashTest;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.*;

public class TopRepositoryTest {
    private static final byte[] ONE_BYTE_ARRAY = new byte[] { 0x01 };
    private static Random random = new Random();

    private TrieKeyMapper trieKeyMapper;
    private RskAddress address;

    @Before
    public void setup() {
        this.trieKeyMapper = new TrieKeyMapper();
        this.address = createRandomAddress();
    }

    @Test
    public void createWithTrie() {
        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertNotNull(repository.getTrie());
        Assert.assertSame(trie, repository.getTrie());
    }

    @Test
    public void createAccount() {
        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertFalse(repository.isExist(address));

        AccountState accountState = repository.createAccount(address);

        Assert.assertNotNull(accountState);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertNotNull(repository.getTrie());
        Assert.assertSame(trie, repository.getTrie());

        AccountState result = repository.getAccountState(address);

        Assert.assertNotNull(result);
        Assert.assertSame(accountState, result);

        Assert.assertNull(trie.get(trieKeyMapper.getAccountKey(address)));
    }

    @Test
    public void getUnknownAccount() {
        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertFalse(repository.isExist(address));

        AccountState accountState = repository.getAccountState(address);

        Assert.assertNull(accountState);

        Assert.assertNotNull(repository.getTrie());
        Assert.assertSame(trie, repository.getTrie());

        Assert.assertNull(trie.get(trieKeyMapper.getAccountKey(address)));
    }

    @Test
    public void getAccountFromTrie() {
        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(1000000));

        Trie trie = new Trie();

        trie = trie.put(trieKeyMapper.getAccountKey(address), accountState.getEncoded());

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertTrue(repository.isExist(address));

        AccountState result = repository.getAccountState(address);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(accountState.getEncoded(), result.getEncoded());
    }

    @Test
    public void getAccountFromTrieAndCommit() {
        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(1000000));

        Trie trie = new Trie();

        trie = trie.put(trieKeyMapper.getAccountKey(address), accountState.getEncoded());

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertTrue(repository.isExist(address));

        AccountState result = repository.getAccountState(address);

        repository.commit();

        Assert.assertTrue(repository.isExist(address));

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(accountState.getEncoded(), result.getEncoded());

        Assert.assertSame(trie, repository.getTrie());
    }

    @Test
    public void createAndCommitAccount() {
        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertFalse(repository.isExist(address));

        AccountState accountState = repository.createAccount(address);

        Assert.assertTrue(repository.isExist(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));

        Assert.assertNotNull(accountState);

        Assert.assertNotNull(repository.getTrie());
        Assert.assertNotSame(trie, repository.getTrie());

        AccountState result = repository.getAccountState(address);

        Assert.assertNotNull(result);
        Assert.assertSame(accountState, result);

        Assert.assertNotNull(repository.getTrie().get(trieKeyMapper.getAccountKey(address)));
        Assert.assertArrayEquals(result.getEncoded(), repository.getTrie().get(trieKeyMapper.getAccountKey(address)));
    }

    @Test
    public void getBalanceAndNonceFromUnknownAccount() {
        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertEquals(Coin.ZERO, repository.getBalance(address));
        Assert.assertEquals(BigInteger.ZERO, repository.getNonce(address));

        Assert.assertFalse(repository.isExist(address));

        repository.commit();

        Assert.assertFalse(repository.isExist(address));

        Assert.assertSame(trie, repository.getTrie());
    }

    @Test
    public void getBalanceAndNonceFromKnownAccount() {
        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(42));
        Trie trie = new Trie();

        trie = trie.put(trieKeyMapper.getAccountKey(address), accountState.getEncoded());

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.valueOf(42), repository.getBalance(address));
        Assert.assertEquals(BigInteger.TEN, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));

        Assert.assertSame(trie, repository.getTrie());
    }

    @Test
    public void addBalanceToUnknownAccount() {
        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertFalse(repository.isExist(address));

        Assert.assertEquals(Coin.valueOf(42), repository.addBalance(address, Coin.valueOf(42)));

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.valueOf(42), repository.getBalance(address));
        Assert.assertEquals(BigInteger.ZERO, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState accountState = new AccountState(repository.getTrie().get(accountKey));

        Assert.assertNotNull(accountState);
        Assert.assertEquals(Coin.valueOf(42), accountState.getBalance());
        Assert.assertEquals(BigInteger.ZERO, accountState.getNonce());
    }

    @Test
    public void addBalanceToKnownAccount() {
        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(42));
        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        trie = trie.put(accountKey, accountState.getEncoded());

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.valueOf(50), repository.addBalance(address, Coin.valueOf(8)));

        Assert.assertEquals(Coin.valueOf(50), repository.getBalance(address));
        Assert.assertEquals(BigInteger.TEN, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState newAccountState = new AccountState(repository.getTrie().get(accountKey));

        Assert.assertNotNull(newAccountState);
        Assert.assertEquals(Coin.valueOf(50), newAccountState.getBalance());
        Assert.assertEquals(BigInteger.TEN, newAccountState.getNonce());
    }

    @Test
    public void increaseNonceToUnknownAccount() {
        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertFalse(repository.isExist(address));

        Assert.assertEquals(BigInteger.ONE, repository.increaseNonce(address));

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.ZERO, repository.getBalance(address));
        Assert.assertEquals(BigInteger.ONE, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState accountState = new AccountState(repository.getTrie().get(accountKey));

        Assert.assertNotNull(accountState);
        Assert.assertEquals(Coin.ZERO, accountState.getBalance());
        Assert.assertEquals(BigInteger.ONE, accountState.getNonce());
    }

    @Test
    public void increaseNonceToKnownAccount() {
        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(42));
        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        trie = trie.put(accountKey, accountState.getEncoded());

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(BigInteger.valueOf(11), repository.increaseNonce((address)));

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.valueOf(42), repository.getBalance(address));
        Assert.assertEquals(BigInteger.valueOf(11), repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState newAccountState = new AccountState(repository.getTrie().get(accountKey));

        Assert.assertNotNull(newAccountState);
        Assert.assertEquals(Coin.valueOf(42), newAccountState.getBalance());
        Assert.assertEquals(BigInteger.valueOf(11), newAccountState.getNonce());
    }

    @Test
    public void increaseNonceUsingUpdateAccountState() {
        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie, null);

        AccountState accountState = repository.createAccount(this.address);
        Assert.assertTrue(repository.isExist(address));
        accountState.incrementNonce();
        repository.updateAccountState(address, accountState);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.ZERO, repository.getBalance(address));
        Assert.assertEquals(BigInteger.ONE, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState newAccountState = new AccountState(repository.getTrie().get(this.trieKeyMapper.getAccountKey(this.address)));

        Assert.assertNotNull(newAccountState);
        Assert.assertEquals(Coin.ZERO, newAccountState.getBalance());
        Assert.assertEquals(BigInteger.ONE, newAccountState.getNonce());
    }

    @Test
    public void setNonceToUnknownAccount() {
        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertFalse(repository.isExist(address));

        repository.setNonce(address, BigInteger.TEN);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.ZERO, repository.getBalance(address));
        Assert.assertEquals(BigInteger.TEN, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState accountState = new AccountState(repository.getTrie().get(accountKey));

        Assert.assertNotNull(accountState);
        Assert.assertEquals(Coin.ZERO, accountState.getBalance());
        Assert.assertEquals(BigInteger.TEN, accountState.getNonce());
    }

    @Test
    public void setNonceToKnownAccount() {
        AccountState accountState = new AccountState(BigInteger.ONE, Coin.valueOf(42));
        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        trie = trie.put(accountKey, accountState.getEncoded());

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertTrue(repository.isExist(address));

        repository.setNonce(address, BigInteger.TEN);

        Assert.assertTrue(repository.isExist(address));

        Assert.assertEquals(Coin.valueOf(42), repository.getBalance(address));
        Assert.assertEquals(BigInteger.TEN, repository.getNonce(address));

        repository.commit();

        Assert.assertTrue(repository.isExist(address));

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState newAccountState = new AccountState(repository.getTrie().get(accountKey));

        Assert.assertNotNull(newAccountState);
        Assert.assertEquals(Coin.valueOf(42), newAccountState.getBalance());
        Assert.assertEquals(BigInteger.TEN, newAccountState.getNonce());
    }
    @Test
    public void getStorageBytesFromUnknownAccount() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        Assert.assertNull(repository.getStorageBytes(this.address, DataWord.ONE));

        Assert.assertFalse(repository.isExist(this.address));
    }

    @Test
    public void setAndGetStorageBytesFromUnknownAccount() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);
        byte[] value = new byte[42];
        random.nextBytes(value);

        repository.addStorageBytes(this.address, DataWord.ONE, value);

        Assert.assertTrue(repository.isExist(this.address));

        byte[] result = repository.getStorageBytes(this.address, DataWord.ONE);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(value, result);

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());

        Assert.assertArrayEquals(value, repository.getStorageBytes(address, DataWord.ONE));
        Assert.assertArrayEquals(value, repository.getTrie().get(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.ONE)));
        Assert.assertArrayEquals(ONE_BYTE_ARRAY, repository.getTrie().get(this.trieKeyMapper.getAccountStoragePrefixKey(this.address)));
    }

    @Test
    public void getStorageBytesFromTrie() {
        Trie trie = new Trie();
        byte[] value = new byte[42];
        random.nextBytes(value);

        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(address, DataWord.ONE), value);

        TopRepository repository = new TopRepository(trie, null);

        byte[] result = repository.getStorageBytes(this.address, DataWord.ONE);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(value, result);

        repository.commit();

        Assert.assertSame(trie, repository.getTrie());

        Assert.assertArrayEquals(value, repository.getStorageBytes(address, DataWord.ONE));
        Assert.assertArrayEquals(value, repository.getTrie().get(this.trieKeyMapper.getAccountStorageKey(address, DataWord.ONE)));
    }

    @Test
    public void changeStorageBytesFromTrie() {
        Trie trie = new Trie();
        byte[] value = new byte[42];
        random.nextBytes(value);
        byte[] value2 = new byte[100];
        random.nextBytes(value2);

        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(address, DataWord.ONE), value);

        TopRepository repository = new TopRepository(trie, null);

        byte[] result = repository.getStorageBytes(this.address, DataWord.ONE);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(value, result);

        repository.addStorageBytes(address, DataWord.ONE, value2);

        byte[] result2 = repository.getStorageBytes(this.address, DataWord.ONE);

        Assert.assertNotNull(result2);
        Assert.assertArrayEquals(value2, result2);

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());

        Assert.assertArrayEquals(value2, repository.getStorageBytes(address, DataWord.ONE));
        Assert.assertArrayEquals(value2, repository.getTrie().get(this.trieKeyMapper.getAccountStorageKey(address, DataWord.ONE)));
    }

    @Test
    public void getStorageBytesFromCreatedAccount() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        repository.createAccount(this.address);

        Assert.assertNull(repository.getStorageBytes(this.address, DataWord.ONE));

        Assert.assertTrue(repository.isExist(this.address));
    }

    @Test
    public void getStorageValueFromUnknownAccount() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));

        Assert.assertFalse(repository.isExist(this.address));
    }

    @Test
    public void getStorageValueFromCreatedAccount() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        repository.createAccount(this.address);

        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));

        Assert.assertTrue(repository.isExist(this.address));
    }

    @Test
    public void setAndGetStorageValueFromUnknownAccount() {
        Trie trie = new Trie();
        DataWord value = DataWord.valueOf(42);

        TopRepository repository = new TopRepository(trie, null);

        repository.addStorageRow(this.address, DataWord.ONE, value);

        Assert.assertTrue(repository.isExist(this.address));

        DataWord result = repository.getStorageValue(this.address, DataWord.ONE);

        Assert.assertNotNull(result);
        Assert.assertEquals(value, result);

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());

        Assert.assertEquals(value, repository.getStorageValue(address, DataWord.ONE));
        Assert.assertArrayEquals(value.getByteArrayForStorage(), repository.getTrie().get(this.trieKeyMapper.getAccountStorageKey(address, DataWord.ONE)));
        Assert.assertArrayEquals(ONE_BYTE_ARRAY, repository.getTrie().get(this.trieKeyMapper.getAccountStoragePrefixKey(address)));
    }

    @Test
    public void getStorageValueFromTrie() {
        Trie trie = new Trie();
        DataWord value = DataWord.valueOf(42);

        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(address, DataWord.ONE), value.getByteArrayForStorage());

        TopRepository repository = new TopRepository(trie, null);

        DataWord result = repository.getStorageValue(this.address, DataWord.ONE);

        Assert.assertNotNull(result);
        Assert.assertEquals(value, result);

        repository.commit();

        Assert.assertSame(trie, repository.getTrie());

        Assert.assertEquals(value, repository.getStorageValue(address, DataWord.ONE));
        Assert.assertArrayEquals(value.getByteArrayForStorage(), repository.getTrie().get(this.trieKeyMapper.getAccountStorageKey(address, DataWord.ONE)));
    }

    @Test
    public void changeStorageValueFromTrie() {
        Trie trie = new Trie();
        DataWord value = DataWord.valueOf(42);
        DataWord value2 = DataWord.valueOf(100);

        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(address, DataWord.ONE), value.getByteArrayForStorage());

        TopRepository repository = new TopRepository(trie, null);

        DataWord result = repository.getStorageValue(this.address, DataWord.ONE);

        Assert.assertNotNull(result);
        Assert.assertEquals(value, result);

        repository.addStorageRow(address, DataWord.ONE, value2);

        DataWord result2 = repository.getStorageValue(this.address, DataWord.ONE);

        Assert.assertNotNull(result2);
        Assert.assertEquals(value2, result2);

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());

        Assert.assertEquals(value2, repository.getStorageValue(address, DataWord.ONE));
        Assert.assertArrayEquals(value2.getByteArrayForStorage(), repository.getTrie().get(this.trieKeyMapper.getAccountStorageKey(address, DataWord.ONE)));
    }

    @Test
    public void getCodeFromUnknownAccount() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        Assert.assertFalse(repository.isExist(this.address));

        byte[] result = repository.getCode(this.address);

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length);
    }

    @Test
    public void getCodeFromCreatedAccountWithoutCode() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        repository.createAccount(this.address);

        Assert.assertNull(repository.getCode(this.address));
    }

    @Test
    public void getCodeFromTrie() {
        byte[] code = new byte[32];
        random.nextBytes(code);

        Trie trie = new Trie();
        trie = trie.put(this.trieKeyMapper.getCodeKey(this.address), code);

        TopRepository repository = new TopRepository(trie, null);

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

        TopRepository repository = new TopRepository(trie, null);

        repository.createAccount(this.address);

        Assert.assertFalse(repository.isContract(this.address));

        repository.setupContract(this.address);

        Assert.assertTrue(repository.isContract(this.address));
        Assert.assertSame(trie, repository.getTrie());

        repository.commit();

        Assert.assertTrue(repository.isContract(this.address));
        Assert.assertNotSame(trie, repository.getTrie());

        Assert.assertArrayEquals(new byte[] { 0x01 }, repository.getTrie().get(this.trieKeyMapper.getAccountStoragePrefixKey(this.address)));
    }

    @Test
    public void hasEmptyHashAsRootWhenCreated() {
        TopRepository repository = new TopRepository(new Trie(), null);

        Assert.assertArrayEquals(TrieHashTest.makeEmptyHash().getBytes(), repository.getRoot());
    }

    @Test
    public void createAccountAndRollback() {
        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertFalse(repository.isExist(this.address));

        repository.createAccount(this.address);

        Assert.assertTrue(repository.isExist(this.address));

        repository.rollback();

        Assert.assertSame(trie, repository.getTrie());
        Assert.assertFalse(repository.isExist(this.address));
    }

    @Test
    public void setupContractAndRollback() {
        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertFalse(repository.isExist(this.address));

        repository.createAccount(this.address);
        repository.setupContract(this.address);

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertTrue(repository.isContract(this.address));

        repository.rollback();

        Assert.assertSame(trie, repository.getTrie());
        Assert.assertFalse(repository.isContract(this.address));
        Assert.assertFalse(repository.isExist(this.address));
    }

    @Test
    public void addStorageAndRollback() {
        Trie trie = new Trie();
        byte[] data = new byte[42];
        random.nextBytes(data);

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertFalse(repository.isExist(this.address));

        repository.createAccount(this.address);
        repository.addStorageBytes(this.address, DataWord.ONE, data);
        repository.addStorageRow(this.address, DataWord.ZERO, DataWord.ONE);

        Assert.assertArrayEquals(data, repository.getStorageBytes(this.address, DataWord.ONE));
        Assert.assertEquals(DataWord.ONE, repository.getStorageValue(this.address, DataWord.ZERO));

        repository.rollback();
        repository.commit();

        Assert.assertSame(trie, repository.getTrie());
        Assert.assertNull(repository.getStorageBytes(this.address, DataWord.ONE));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ZERO));
    }

    @Test
    public void saveCode() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

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
        TopRepository repository = new TopRepository(trie, null);

        byte[] code = new byte[42];
        random.nextBytes(code);

        repository.saveCode(this.address, code);

        Assert.assertSame(trie, repository.getTrie());
        Assert.assertArrayEquals(code, repository.getCode(address));
        Assert.assertTrue(repository.isExist(address));

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());
        Assert.assertArrayEquals(code, repository.getCode(address));
        Assert.assertTrue(repository.isExist(address));

        Assert.assertArrayEquals(code, repository.getTrie().get(this.trieKeyMapper.getCodeKey(this.address)));
    }

    @Test
    public void saveCodeAndRollback() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        byte[] code = new byte[42];
        random.nextBytes(code);

        repository.saveCode(this.address, code);

        Assert.assertSame(trie, repository.getTrie());
        Assert.assertArrayEquals(code, repository.getCode(address));
        Assert.assertTrue(repository.isExist(address));

        repository.rollback();

        Assert.assertSame(trie, repository.getTrie());
        Assert.assertArrayEquals(new byte[0], repository.getCode(address));
        Assert.assertFalse(repository.isExist(address));

        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getCodeKey(this.address)));
    }

    @Test
    public void saveNullCode() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        repository.saveCode(this.address, null);

        Assert.assertArrayEquals(new byte[0], repository.getCode(address));
        Assert.assertFalse(repository.isExist(address));
    }

    @Test
    public void saveEmptyCode() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        repository.saveCode(this.address, new byte[0]);

        Assert.assertArrayEquals(new byte[0], repository.getCode(address));
        Assert.assertFalse(repository.isExist(address));
    }

    @Test
    public void hibernateUnknownAccount() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        repository.hibernate(this.address);

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertNotNull(repository.getAccountState(this.address).isHibernated());

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertNotNull(repository.getAccountState(this.address).isHibernated());

        Assert.assertArrayEquals(repository.getAccountState(this.address).getEncoded(), repository.getTrie().get(this.trieKeyMapper.getAccountKey(this.address)));
    }

    @Test
    public void hibernateUnknownAccountAndRollback() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        repository.hibernate(this.address);

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertNotNull(repository.getAccountState(this.address).isHibernated());

        repository.rollback();

        Assert.assertSame(trie, repository.getTrie());

        Assert.assertFalse(repository.isExist(this.address));
    }

    @Test
    public void hibernateKnownAccount() {
        Trie trie = new Trie();

        AccountState accountState = new AccountState();

        trie = trie.put(this.trieKeyMapper.getAccountKey(this.address), accountState.getEncoded());

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertTrue(repository.isExist(this.address));

        repository.hibernate(this.address);

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertTrue(repository.getAccountState(this.address).isHibernated());

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertTrue(repository.getAccountState(this.address).isHibernated());

        Assert.assertArrayEquals(repository.getAccountState(this.address).getEncoded(), repository.getTrie().get(this.trieKeyMapper.getAccountKey(this.address)));
    }

    @Test
    public void hibernateKnownAccountAndRollback() {
        Trie trie = new Trie();

        AccountState accountState = new AccountState();

        trie = trie.put(this.trieKeyMapper.getAccountKey(this.address), accountState.getEncoded());

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertTrue(repository.isExist(this.address));

        repository.hibernate(this.address);

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertTrue(repository.getAccountState(this.address).isHibernated());

        repository.rollback();

        Assert.assertSame(trie, repository.getTrie());

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertFalse(repository.getAccountState(this.address).isHibernated());

        Assert.assertArrayEquals(accountState.getEncoded(), repository.getTrie().get(this.trieKeyMapper.getAccountKey(this.address)));
    }

    @Test
    public void createAndDeleteAccount() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        repository.createAccount(this.address);

        Assert.assertTrue(repository.isExist(this.address));

        repository.delete(this.address);

        Assert.assertFalse(repository.isExist(this.address));

        repository.commit();

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getAccountKey(this.address)));
    }

    @Test
    public void createAndDeleteAccountAddingStorageAndCode() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        repository.createAccount(this.address);

        Assert.assertTrue(repository.isExist(this.address));

        repository.addStorageRow(this.address, DataWord.ONE, DataWord.valueOf(42));
        repository.saveCode(this.address, new byte[1]);

        repository.delete(this.address);

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));

        repository.commit();

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getAccountKey(this.address)));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.ONE)));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getCodeKey(this.address)));
    }

    @Test
    public void deleteKnownAccountWithStorageAndCode() {
        AccountState accountState = new AccountState();

        Trie trie = new Trie();

        trie = trie.put(this.trieKeyMapper.getAccountKey(this.address), accountState.getEncoded());
        trie = trie.put(this.trieKeyMapper.getCodeKey(this.address), new byte[1]);
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.ONE), DataWord.valueOf(42).getByteArrayForStorage());

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertEquals(DataWord.valueOf(42), repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[1], repository.getCode(this.address));

        repository.delete(this.address);

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));

        Assert.assertNull(repository.getAccountState(this.address));

        repository.commit();

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getAccountKey(this.address)));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.ONE)));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getCodeKey(this.address)));

        Assert.assertNull(repository.getAccountState(this.address));
    }

    @Test
    public void createAndDeleteAccountAddingStorageWithRollback() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

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
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getAccountKey(this.address)));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.ONE)));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getCodeKey(this.address)));
    }

    @Test
    public void deleteKnownAccount() {
        AccountState accountState = new AccountState();

        Trie trie = new Trie();

        trie = trie.put(this.trieKeyMapper.getAccountKey(this.address), accountState.getEncoded());

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertTrue(repository.isExist(this.address));

        repository.delete(this.address);

        Assert.assertFalse(repository.isExist(this.address));

        repository.commit();

        Assert.assertFalse(repository.isExist(this.address));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getAccountKey(this.address)));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.ONE)));
        Assert.assertArrayEquals(new byte[0], repository.getCode(this.address));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getCodeKey(this.address)));
    }

    @Test
    public void deleteKnownAccountAndRollback() {
        AccountState accountState = new AccountState();

        Trie trie = new Trie();

        trie = trie.put(this.trieKeyMapper.getAccountKey(this.address), accountState.getEncoded());

        TopRepository repository = new TopRepository(trie, null);

        Assert.assertTrue(repository.isExist(this.address));

        repository.delete(this.address);

        Assert.assertFalse(repository.isExist(this.address));

        repository.rollback();

        Assert.assertTrue(repository.isExist(this.address));
        Assert.assertNotNull(repository.getTrie().get(this.trieKeyMapper.getAccountKey(this.address)));
        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.ONE)));
        Assert.assertNull(repository.getCode(this.address));
        Assert.assertNull(repository.getTrie().get(this.trieKeyMapper.getCodeKey(this.address)));
    }

    @Test
    public void createAccountCommitAndSave() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, trieStore);

        repository.createAccount(this.address);
        repository.commit();
        repository.save();

        Assert.assertNotSame(trie, repository.getTrie());
        Assert.assertNotNull(trieStore.retrieve(repository.getRoot()));
    }

    @Test
    public void getCodeLengthAndHashFromUnknownAccount() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

        Assert.assertEquals(0, repository.getCodeLength(this.address));
        Assert.assertEquals(Keccak256.ZERO_HASH, repository.getCodeHash(this.address));
    }

    @Test
    public void saveCodeAndGetCodeLengthAndHashFromUnknownAccount() {
        byte[] code = new byte[] { 0x01, 0x02, 0x03, 0x04 };

        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);
        repository.saveCode(this.address, code);

        Assert.assertEquals(4, repository.getCodeLength(this.address));
        Assert.assertEquals(new Keccak256(Keccak256Helper.keccak256(code)), repository.getCodeHash(this.address));
    }

    @Test
    public void createAccountsAndGetAccountKeys() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

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

        TopRepository repository = new TopRepository(trie, null);

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

        TopRepository repository = new TopRepository(trie, null);

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

        TopRepository repository = new TopRepository(trie, null);

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
        TopRepository repository = new TopRepository(trie, null);

        Iterator<DataWord> result = repository.getStorageKeys(this.address);

        Assert.assertNotNull(result);
        Assert.assertFalse(result.hasNext());
    }

    @Test
    public void getStorageKeysFromCreatedAccountWithStorage() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie, null);

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
    }

    @Test
    public void getStorageKeysFromAccountWithStorageInTrie() {
        Trie trie = new Trie();

        trie = trie.put(this.trieKeyMapper.getAccountKey(this.address), new AccountState().getEncoded());
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.valueOf(10)), DataWord.valueOf(100).getByteArrayForStorage());
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.valueOf(11)), DataWord.valueOf(101).getByteArrayForStorage());

        TopRepository repository = new TopRepository(trie, null);

        Iterator<DataWord> result = repository.getStorageKeys(this.address);

        Assert.assertNotNull(result);

        List<DataWord> list = iteratorToList(result);

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());
        Assert.assertTrue(list.contains(DataWord.valueOf(10)));
        Assert.assertTrue(list.contains(DataWord.valueOf(11)));
    }

    @Test
    public void getStorageKeysFromAccountWithStorageInTrieAndDeleteOneRow() {
        Trie trie = new Trie();

        trie = trie.put(this.trieKeyMapper.getAccountKey(this.address), new AccountState().getEncoded());
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.valueOf(10)), DataWord.valueOf(100).getByteArrayForStorage());
        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(this.address, DataWord.valueOf(11)), DataWord.valueOf(101).getByteArrayForStorage());

        TopRepository repository = new TopRepository(trie, null);

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

        TopRepository repository = new TopRepository(trie, null);

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
