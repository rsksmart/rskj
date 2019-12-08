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
import org.ethereum.core.AccountState;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Random;

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

        TopRepository repository = new TopRepository(trie);

        Assert.assertNotNull(repository.getTrie());
        Assert.assertSame(trie, repository.getTrie());
    }

    @Test
    public void createAccount() {
        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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
    public void setNonceToUnknownAccount() {
        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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
        TopRepository repository = new TopRepository(trie);

        Assert.assertNull(repository.getStorageBytes(this.address, DataWord.ONE));

        Assert.assertFalse(repository.isExist(this.address));
    }

    @Test
    public void setAndGetStorageBytesFromUnknownAccount() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie);
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
        Assert.assertArrayEquals(value, repository.getTrie().get(this.trieKeyMapper.getAccountStorageKey(address, DataWord.ONE)));
        Assert.assertArrayEquals(ONE_BYTE_ARRAY, repository.getTrie().get(this.trieKeyMapper.getAccountStoragePrefixKey(address)));
    }

    @Test
    public void getStorageBytesFromTrie() {
        Trie trie = new Trie();
        byte[] value = new byte[42];
        random.nextBytes(value);

        trie = trie.put(this.trieKeyMapper.getAccountStorageKey(address, DataWord.ONE), value);

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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
        TopRepository repository = new TopRepository(trie);

        repository.createAccount(this.address);

        Assert.assertNull(repository.getStorageBytes(this.address, DataWord.ONE));

        Assert.assertTrue(repository.isExist(this.address));
    }

    @Test
    public void getStorageValueFromUnknownAccount() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie);

        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));

        Assert.assertFalse(repository.isExist(this.address));
    }

    @Test
    public void getStorageValueFromCreatedAccount() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie);

        repository.createAccount(this.address);

        Assert.assertNull(repository.getStorageValue(this.address, DataWord.ONE));

        Assert.assertTrue(repository.isExist(this.address));
    }

    @Test
    public void setAndGetStorageValueFromUnknownAccount() {
        Trie trie = new Trie();
        DataWord value = DataWord.valueOf(42);

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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

        TopRepository repository = new TopRepository(trie);

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
        TopRepository repository = new TopRepository(trie);

        byte[] result = repository.getCode(this.address);

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length);
    }

    @Test
    public void getCodeFromCreatedAccountWithoutCode() {
        Trie trie = new Trie();
        TopRepository repository = new TopRepository(trie);

        repository.createAccount(this.address);

        Assert.assertNull(repository.getCode(this.address));
    }

    @Test
    public void getCodeFromTrie() {
        byte[] code = new byte[32];
        random.nextBytes(code);

        Trie trie = new Trie();
        trie = trie.put(this.trieKeyMapper.getCodeKey(this.address), code);

        TopRepository repository = new TopRepository(trie);

        repository.createAccount(this.address);

        byte[] result = repository.getCode(this.address);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(code, result);

        byte[] result2 = repository.getCode(this.address);

        Assert.assertNotNull(result2);
        Assert.assertArrayEquals(code, result2);
    }

    private static RskAddress createRandomAddress() {
        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        random.nextBytes(bytes);

        return new RskAddress(bytes);
    }
}
