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

import co.rsk.trie.TrieImplHashTest;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;

/**
 * Created by ajlopez on 29/03/2017.
 */
public class RepositoryImplTest {
    private static byte[] emptyHash = TrieImplHashTest.makeEmptyHash();

    @Test
    public void getNonceUnknownAccount() {
        RepositoryImpl repository = new RepositoryImpl();
        BigInteger nonce = repository.getNonce(randomAccountAddress());

        Assert.assertEquals(BigInteger.ZERO, nonce);
    }

    @Test
    public void isNotClosedWhenCreated() {
        RepositoryImpl repository = new RepositoryImpl();

        Assert.assertFalse(repository.isClosed());
    }

    @Test
    public void hasEmptyHashAsRootWhenCreated() {
        RepositoryImpl repository = new RepositoryImpl();

        Assert.assertArrayEquals(emptyHash, repository.getRoot());
    }

    @Test
    public void createAccount() {
        RepositoryImpl repository = new RepositoryImpl();

        AccountState accState = repository.createAccount(randomAccountAddress());

        Assert.assertNotNull(accState);
        Assert.assertEquals(BigInteger.ZERO, accState.getNonce());
        Assert.assertEquals(BigInteger.ZERO, accState.getBalance());

        Assert.assertFalse(Arrays.equals(emptyHash, repository.getRoot()));
    }

    @Test
    public void syncToRootAfterCreatingAnAccount() {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        RepositoryImpl repository = new RepositoryImpl(store);

        repository.flush();

        byte[] accAddress = randomAccountAddress();
        byte[] initialRoot = repository.getRoot();

        repository.createAccount(accAddress);
        repository.flush();

        byte[] newRoot = repository.getRoot();

        Assert.assertTrue(repository.isExist(accAddress));

        repository.syncToRoot(initialRoot);

        Assert.assertFalse(repository.isExist(accAddress));

        repository.syncToRoot(newRoot);

        Assert.assertTrue(repository.isExist(accAddress));
    }

    @Test
    public void updateAccountState() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        AccountState accState = repository.createAccount(accAddress);

        accState.incrementNonce();
        accState.addToBalance(BigInteger.ONE);

        repository.updateAccountState(accAddress, accState);

        AccountState newAccState = repository.getAccountState(accAddress);
        Assert.assertNotNull(newAccState);
        Assert.assertEquals(BigInteger.ONE, newAccState.getNonce());
        Assert.assertEquals(BigInteger.ONE, newAccState.getBalance());
    }

    @Test
    public void incrementAccountNonceForNewAccount() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        repository.increaseNonce(accAddress);

        Assert.assertEquals(BigInteger.ONE, repository.getNonce(accAddress));
    }

    @Test
    public void incrementAccountNonceForAlreadyCreatedAccount() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        repository.createAccount(accAddress);
        repository.increaseNonce(accAddress);

        Assert.assertEquals(BigInteger.ONE, repository.getNonce(accAddress));
    }

    @Test
    public void incrementAccountNonceTwiceForAlreadyCreatedAccount() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        repository.createAccount(accAddress);
        repository.increaseNonce(accAddress);
        repository.increaseNonce(accAddress);

        Assert.assertEquals(2, repository.getNonce(accAddress).longValue());
    }

    @Test
    public void incrementAccountBalanceForNewAccount() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        Assert.assertEquals(BigInteger.ONE, repository.addBalance(accAddress, BigInteger.ONE));

        Assert.assertEquals(BigInteger.ONE, repository.getBalance(accAddress));
    }

    @Test
    public void incrementAccountBalanceForAlreadyCreatedAccount() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        repository.createAccount(accAddress);
        Assert.assertEquals(BigInteger.ONE, repository.addBalance(accAddress, BigInteger.ONE));

        Assert.assertEquals(BigInteger.ONE, repository.getBalance(accAddress));
    }

    @Test
    public void incrementAccountBalanceTwiceForAlreadyCreatedAccount() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        repository.createAccount(accAddress);
        Assert.assertEquals(BigInteger.ONE, repository.addBalance(accAddress, BigInteger.ONE));
        Assert.assertEquals(2, repository.addBalance(accAddress, BigInteger.ONE).longValue());

        Assert.assertEquals(2, repository.getBalance(accAddress).longValue());
    }

    @Test
    public void isExistReturnsFalseForUnknownAccount() {
        RepositoryImpl repository = new RepositoryImpl();

        Assert.assertFalse(repository.isExist(randomAccountAddress()));
    }

    @Test
    public void isExistReturnsTrueForCreatedAccount() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        repository.createAccount(accAddress);

        Assert.assertTrue(repository.isExist(accAddress));
    }

    @Test
    public void getCodeFromUnknownAccount() {
        RepositoryImpl repository = new RepositoryImpl();

        byte[] code = repository.getCode(randomAccountAddress());

        Assert.assertNotNull(code);
        Assert.assertEquals(0, code.length);
    }

    @Test
    public void getCodeFromAccountWithoutCode() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        repository.createAccount(accAddress);

        byte[] code = repository.getCode(accAddress);

        Assert.assertNotNull(code);
        Assert.assertEquals(0, code.length);
    }

    @Test
    public void saveAndGetCodeFromAccount() {
        byte[] accAddress = randomAccountAddress();
        byte[] accCode = new byte[] { 0x01, 0x02, 0x03 };

        RepositoryImpl repository = new RepositoryImpl();

        repository.createAccount(accAddress);

        repository.saveCode(accAddress, accCode);

        byte[] code = repository.getCode(accAddress);

        Assert.assertNotNull(code);
        Assert.assertArrayEquals(accCode, code);
    }

    @Test
    public void hibernateAccount() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        repository.createAccount(accAddress);
        repository.hibernate(accAddress);

        AccountState accState = repository.getAccountState(accAddress);

        Assert.assertNotNull(accState);
        Assert.assertTrue(accState.isHibernated());
    }

    @Test
    public void getCodeFromHibernatedAccount() {
        byte[] accAddress = randomAccountAddress();
        byte[] accCode = new byte[] { 0x01, 0x02, 0x03 };

        RepositoryImpl repository = new RepositoryImpl();

        repository.createAccount(accAddress);

        repository.saveCode(accAddress, accCode);
        repository.hibernate(accAddress);

        byte[] code = repository.getCode(accAddress);

        Assert.assertNotNull(code);
        Assert.assertEquals(0, code.length);
    }

    @Test
    public void startTracking() {
        RepositoryImpl repository = new RepositoryImpl();

        Repository track = repository.startTracking();

        Assert.assertNotNull(track);
    }

    @Test
    public void createAccountInTrackAndCommit() {
        byte[] accAddress = randomAccountAddress();
        RepositoryImpl repository = new RepositoryImpl();

        Repository track = repository.startTracking();

        Assert.assertNotNull(track);
        track.createAccount(accAddress);
        track.commit();

        Assert.assertTrue(repository.isExist(accAddress));
    }

    @Test
    public void createAccountInTrackAndRollback() {
        byte[] accAddress = randomAccountAddress();
        RepositoryImpl repository = new RepositoryImpl();

        Repository track = repository.startTracking();

        Assert.assertNotNull(track);
        track.createAccount(accAddress);
        track.rollback();

        Assert.assertFalse(repository.isExist(accAddress));
    }

    @Test
    public void getEmptyStorageValue() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        repository.createAccount(accAddress);
        DataWord value = repository.getStorageValue(accAddress, DataWord.ONE);

        Assert.assertNull(value);
    }

    @Test
    public void setAndGetStorageValue() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        repository.addStorageRow(accAddress, DataWord.ONE, DataWord.ONE);

        DataWord value = repository.getStorageValue(accAddress, DataWord.ONE);

        // Account state points to previous state, use track to update values
        Assert.assertNull(value);
    }

    @Test
    public void setAndGetStorageValueUsingNewRepositoryForTest() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImplForTesting();

        repository.addStorageRow(accAddress, DataWord.ONE, DataWord.ONE);

        DataWord value = repository.getStorageValue(accAddress, DataWord.ONE);

        Assert.assertNotNull(value);
        Assert.assertEquals(DataWord.ONE, value);
    }

    @Test
    public void setAndGetStorageValueUsingTrack() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        Repository track = repository.startTracking();

        track.addStorageRow(accAddress, DataWord.ONE, DataWord.ONE);
        track.commit();

        DataWord value = repository.getStorageValue(accAddress, DataWord.ONE);

        Assert.assertNotNull(value);
        Assert.assertEquals(DataWord.ONE, value);
    }

    @Test
    public void getEmptyStorageBytes() {
        byte[] accAddress = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        byte[] bytes = repository.getStorageBytes(accAddress, DataWord.ONE);

        Assert.assertNull(bytes);
    }

    @Test
    public void setAndGetStorageBytesUsingTrack() {
        byte[] accAddress = randomAccountAddress();
        byte[] bytes = new byte[] { 0x01, 0x02, 0x03 };

        RepositoryImpl repository = new RepositoryImpl();

        Repository track = repository.startTracking();
        track.addStorageBytes(accAddress, DataWord.ONE, bytes);
        track.commit();

        byte[] savedBytes = repository.getStorageBytes(accAddress, DataWord.ONE);

        Assert.assertNotNull(savedBytes);
        Assert.assertArrayEquals(bytes, savedBytes);
    }

    @Test
    public void emptyAccountsKeysOnNonExistentAccount()
    {
        RepositoryImpl repository = new RepositoryImpl();

        Set<ByteArrayWrapper> keys = repository.getAccountsKeys();

        Assert.assertNotNull(keys);
        Assert.assertTrue(keys.isEmpty());
    }

    @Test
    public void getAccountsKeys()
    {
        byte[] accAddress1 = randomAccountAddress();
        byte[] accAddress2 = randomAccountAddress();

        RepositoryImpl repository = new RepositoryImpl();

        repository.createAccount(accAddress1);
        repository.createAccount(accAddress2);

        Set<ByteArrayWrapper> keys = repository.getAccountsKeys();

        Assert.assertNotNull(keys);
        Assert.assertFalse(keys.isEmpty());
        Assert.assertEquals(2, keys.size());
    }

    @Test
    public void getAccountsKeysOnSnapshot()
    {
        byte[] accAddress1 = randomAccountAddress();
        byte[] accAddress2 = randomAccountAddress();

        TrieStore store = new TrieStoreImpl(new HashMapDB());
        RepositoryImpl repository = new RepositoryImpl(store);

        repository.createAccount(accAddress1);
        repository.flush();

        byte[] root = repository.getRoot();

        repository.createAccount(accAddress2);

        repository.syncToRoot(root);

        Set<ByteArrayWrapper> keys = repository.getAccountsKeys();

        Assert.assertNotNull(keys);
        Assert.assertFalse(keys.isEmpty());
        Assert.assertEquals(1, keys.size());
    }

    @Test
    public void getDetailsDataStore() {
        RepositoryImpl repository = new RepositoryImpl();

        Assert.assertNotNull(repository.getDetailsDataStore());
    }

    @Test
    public void flushNoReconnect() {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        RepositoryImpl repository = new RepositoryImpl(store);

        byte[] accAddress = randomAccountAddress();
        byte[] initialRoot = repository.getRoot();

        repository.createAccount(accAddress);
        repository.flushNoReconnect();

        Assert.assertTrue(repository.isExist(accAddress));
    }

    private static byte[] randomAccountAddress() {
        byte[] bytes = new byte[20];

        new Random().nextBytes(bytes);

        return bytes;
    }
}
