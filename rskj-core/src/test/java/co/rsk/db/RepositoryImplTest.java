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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieImplHashTest;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.TestUtils;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.Matchers.is;

/**
 * Created by ajlopez on 29/03/2017.
 */
public class RepositoryImplTest {
    private static Keccak256 emptyHash = TrieImplHashTest.makeEmptyHash();
    private final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void getNonceUnknownAccount() {
        Repository repository = createRepository();
        BigInteger nonce = repository.getNonce(randomAccountAddress());

        Assert.assertEquals(BigInteger.ZERO, nonce);
    }

    @Test
    public void hasEmptyHashAsRootWhenCreated() {
        Repository repository = createRepository();

        Assert.assertArrayEquals(emptyHash.getBytes(), repository.getRoot());
    }

    @Test
    public void createAccount() {
        Repository repository = createRepository();

        AccountState accState = repository.createAccount(randomAccountAddress());

        Assert.assertNotNull(accState);
        Assert.assertEquals(BigInteger.ZERO, accState.getNonce());
        Assert.assertEquals(BigInteger.ZERO, accState.getBalance().asBigInteger());

        Assert.assertFalse(Arrays.equals(emptyHash.getBytes(), repository.getRoot()));
    }

    @Test
    public void syncToRootAfterCreatingAnAccount() {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(new TrieImpl(store, true)));

        repository.flush();

        RskAddress accAddress = randomAccountAddress();
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
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        AccountState accState = repository.createAccount(accAddress);

        accState.incrementNonce();
        accState.addToBalance(Coin.valueOf(1L));

        repository.updateAccountState(accAddress, accState);

        AccountState newAccState = repository.getAccountState(accAddress);
        Assert.assertNotNull(newAccState);
        Assert.assertEquals(BigInteger.ONE, newAccState.getNonce());
        Assert.assertEquals(BigInteger.ONE, newAccState.getBalance().asBigInteger());
    }

    @Test
    public void incrementAccountNonceForNewAccount() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        repository.increaseNonce(accAddress);

        Assert.assertEquals(BigInteger.ONE, repository.getNonce(accAddress));
    }

    @Test
    public void incrementAccountNonceForAlreadyCreatedAccount() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        repository.createAccount(accAddress);
        repository.increaseNonce(accAddress);

        Assert.assertEquals(BigInteger.ONE, repository.getNonce(accAddress));
    }

    @Test
    public void incrementAccountNonceTwiceForAlreadyCreatedAccount() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        repository.createAccount(accAddress);
        repository.increaseNonce(accAddress);
        repository.increaseNonce(accAddress);

        Assert.assertEquals(2, repository.getNonce(accAddress).longValue());
    }

    @Test
    public void incrementAccountBalanceForNewAccount() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        Assert.assertEquals(BigInteger.ONE, repository.addBalance(accAddress, Coin.valueOf(1L)).asBigInteger());

        Assert.assertEquals(BigInteger.ONE, repository.getBalance(accAddress).asBigInteger());
    }

    @Test
    public void incrementAccountBalanceForAlreadyCreatedAccount() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        repository.createAccount(accAddress);
        Assert.assertEquals(BigInteger.ONE, repository.addBalance(accAddress, Coin.valueOf(1L)).asBigInteger());

        Assert.assertEquals(BigInteger.ONE, repository.getBalance(accAddress).asBigInteger());
    }

    @Test
    public void incrementAccountBalanceTwiceForAlreadyCreatedAccount() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        repository.createAccount(accAddress);
        Assert.assertEquals(BigInteger.ONE, repository.addBalance(accAddress, Coin.valueOf(1L)).asBigInteger());
        Assert.assertEquals(2, repository.addBalance(accAddress, Coin.valueOf(1L)).asBigInteger().longValue());

        Assert.assertEquals(2, repository.getBalance(accAddress).asBigInteger().longValue());
    }

    @Test
    public void isExistReturnsFalseForUnknownAccount() {
        Repository repository = createRepository();

        Assert.assertFalse(repository.isExist(randomAccountAddress()));
    }

    @Test
    public void isExistReturnsTrueForCreatedAccount() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        repository.createAccount(accAddress);

        Assert.assertTrue(repository.isExist(accAddress));
    }

    @Test
    public void getCodeFromUnknownAccount() {
        Repository repository = createRepository();

        byte[] code = repository.getCode(randomAccountAddress());

        Assert.assertNotNull(code);
        Assert.assertEquals(0, code.length);
    }

    @Test
    public void getCodeFromAccountWithoutCode() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        repository.createAccount(accAddress);

        byte[] code = repository.getCode(accAddress);

        // From now on null represents no code, because the code node is not even
        // created
        Assert.assertNull(code);

    }

    @Test
    public void saveAndGetCodeFromAccount() {
        RskAddress accAddress = randomAccountAddress();
        byte[] accCode = new byte[] { 0x01, 0x02, 0x03 };

        Repository repository = createRepository();

        repository.createAccount(accAddress);

        repository.saveCode(accAddress, accCode);

        byte[] code = repository.getCode(accAddress);

        Assert.assertNotNull(code);
        Assert.assertArrayEquals(accCode, code);
    }

    @Test
    public void hibernateAccount() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        repository.createAccount(accAddress);
        repository.hibernate(accAddress);

        AccountState accState = repository.getAccountState(accAddress);

        Assert.assertNotNull(accState);
        Assert.assertTrue(accState.isHibernated());
    }

    @Test
    public void getCodeFromHibernatedAccount() {
        RskAddress accAddress = randomAccountAddress();
        byte[] accCode = new byte[] { 0x01, 0x02, 0x03 };

        Repository repository = createRepository();

        repository.createAccount(accAddress);

        repository.saveCode(accAddress, accCode);
        repository.hibernate(accAddress);

        byte[] code = repository.getCode(accAddress);

        Assert.assertNotNull(code);
        Assert.assertEquals(0, code.length);
    }

    @Test
    public void startTracking() {
        Repository repository = createRepository();

        Repository track = repository.startTracking();

        Assert.assertNotNull(track);
    }

    @Test
    public void createAccountInTrackAndCommit() {
        RskAddress accAddress = randomAccountAddress();
        Repository repository = createRepository();

        Repository track = repository.startTracking();

        Assert.assertNotNull(track);
        track.createAccount(accAddress);
        track.commit();

        Assert.assertTrue(repository.isExist(accAddress));
    }

    @Test
    public void createAccountInTrackAndRollback() {
        RskAddress accAddress = randomAccountAddress();
        Repository repository = createRepository();

        Repository track = repository.startTracking();

        Assert.assertNotNull(track);
        track.createAccount(accAddress);
        track.rollback();

        Assert.assertFalse(repository.isExist(accAddress));
    }

    @Test
    public void getEmptyStorageValue() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        repository.createAccount(accAddress);
        DataWord value = repository.getStorageValue(accAddress, DataWord.ONE);

        Assert.assertNull(value);
    }

    @Test
    public void setAndGetStorageValue() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        repository.addStorageRow(accAddress, DataWord.ONE, DataWord.ONE);

        DataWord value = repository.getStorageValue(accAddress, DataWord.ONE);

        // Prior this change repostiory state would point to previous state,
        // so the returned value was null.
        // This semantic has changed. If you modify a repository, you modify
        // a repository. The previous semantics were really really weird.
        Assert.assertEquals(DataWord.ONE,value);
    }

    @Test
    public void setAndGetStorageValueUsingNewRepositoryForTest() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = new RepositoryImplForTesting();

        repository.addStorageRow(accAddress, DataWord.ONE, DataWord.ONE);

        DataWord value = repository.getStorageValue(accAddress, DataWord.ONE);

        Assert.assertNotNull(value);
        Assert.assertEquals(DataWord.ONE, value);
    }

    @Test
    public void setAndGetStorageValueUsingTrack() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        Repository track = repository.startTracking();

        track.addStorageRow(accAddress, DataWord.ONE, DataWord.ONE);
        track.commit();

        DataWord value = repository.getStorageValue(accAddress, DataWord.ONE);

        Assert.assertNotNull(value);
        Assert.assertEquals(DataWord.ONE, value);
    }

    @Test
    public void getEmptyStorageBytes() {
        RskAddress accAddress = randomAccountAddress();

        Repository repository = createRepository();

        byte[] bytes = repository.getStorageBytes(accAddress, DataWord.ONE);

        Assert.assertNull(bytes);
    }

    @Test
    public void setAndGetStorageBytesUsingTrack() {
        RskAddress accAddress = randomAccountAddress();
        byte[] bytes = new byte[] { 0x01, 0x02, 0x03 };

        Repository repository = createRepository();

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
        Repository repository = createRepository();

        Set<RskAddress> keys = repository.getAccountsKeys();

        Assert.assertNotNull(keys);
        Assert.assertTrue(keys.isEmpty());
    }

    @Test
    public void getAccountsKeys() {
        RskAddress accAddress1 = randomAccountAddress();
        RskAddress accAddress2 = randomAccountAddress();

        Repository repository = createRepository(true);

        repository.createAccount(accAddress1);
        repository.createAccount(accAddress2);

        Set<RskAddress> keys = repository.getAccountsKeys();
        Assert.assertNotNull(keys);
        Assert.assertFalse(keys.isEmpty());
        Assert.assertEquals(2, keys.size());
        Assert.assertTrue(keys.contains(accAddress1));
        Assert.assertTrue(keys.contains(accAddress2));
    }

    @Test
    public void getAccountsKeysOnSnapshot()
    {
        RskAddress accAddress1 = randomAccountAddress();
        RskAddress accAddress2 = randomAccountAddress();

        TrieStore store = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(new TrieImpl(store, true)));

        repository.createAccount(accAddress1);
        repository.flush();

        byte[] root = repository.getRoot();

        repository.createAccount(accAddress2);

        repository.syncToRoot(root);

        Set<RskAddress> keys = repository.getAccountsKeys();

        Assert.assertNotNull(keys);
        Assert.assertFalse(keys.isEmpty());
        Assert.assertEquals(1, keys.size());
    }

    @Test
    public void flushNoReconnect() {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(new TrieImpl(store, true)));

        RskAddress accAddress = randomAccountAddress();
        byte[] initialRoot = repository.getRoot();

        repository.createAccount(accAddress);
        repository.flushNoReconnect();

        Assert.assertTrue(repository.isExist(accAddress));
    }

    @Test
    public void isContract() {
        Repository repository = createRepository();
        RskAddress rskAddress = TestUtils.randomAddress();

        repository.createAccount(rskAddress);
        repository.saveCode(rskAddress, TestUtils.randomBytes(32));

        Assert.assertThat(repository.isContract(rskAddress), is(true));
        Assert.assertThat(repository.isContract(TestUtils.randomAddress()), is(false));
    }

    private static RskAddress randomAccountAddress() {
        byte[] bytes = new byte[20];

        new Random().nextBytes(bytes);

        return new RskAddress(bytes);
    }

    private static Repository createRepository(boolean isSecure) {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(new TrieImpl(isSecure))));
    }

    private static Repository createRepository() {
        return new MutableRepository(new MutableTrieImpl(new TrieImpl(false)));
    }
}
