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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieHashTest;
import org.ethereum.TestUtils;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Set;

import static org.hamcrest.Matchers.is;

/**
 * Created by ajlopez on 29/03/2017.
 */
class RepositoryImplTest {
    private static Keccak256 emptyHash = TrieHashTest.makeEmptyHash();

    @Test
    void getNonceUnknownAccount() {
        Repository repository = createRepository();
        BigInteger nonce = repository.getNonce(TestUtils.generateAddress("nonce"));

        Assertions.assertEquals(BigInteger.ZERO, nonce);
    }

    @Test
    void hasEmptyHashAsRootWhenCreated() {
        Repository repository = createRepository();

        Assertions.assertArrayEquals(emptyHash.getBytes(), repository.getRoot());
    }

    @Test
    void createAccount() {
        Repository repository = createRepository();

        AccountState accState = repository.createAccount(TestUtils.generateAddress("accState"));

        Assertions.assertNotNull(accState);
        Assertions.assertEquals(BigInteger.ZERO, accState.getNonce());
        Assertions.assertEquals(BigInteger.ZERO, accState.getBalance().asBigInteger());

        Assertions.assertFalse(Arrays.equals(emptyHash.getBytes(), repository.getRoot()));
    }

    @Test
    void updateAccountState() {
        RskAddress accAddress = TestUtils.generateAddress("accAddress");

        Repository repository = createRepository();

        AccountState accState = repository.createAccount(accAddress);

        accState.incrementNonce();
        accState.addToBalance(Coin.valueOf(1L));

        repository.updateAccountState(accAddress, accState);

        AccountState newAccState = repository.getAccountState(accAddress);
        Assertions.assertNotNull(newAccState);
        Assertions.assertEquals(BigInteger.ONE, newAccState.getNonce());
        Assertions.assertEquals(BigInteger.ONE, newAccState.getBalance().asBigInteger());
    }

    @Test
    void incrementAccountNonceForNewAccount() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = createRepository();

        repository.increaseNonce(accAddress);

        Assertions.assertEquals(BigInteger.ONE, repository.getNonce(accAddress));
    }

    @Test
    void incrementAccountNonceForAlreadyCreatedAccount() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = createRepository();

        repository.createAccount(accAddress);
        repository.increaseNonce(accAddress);

        Assertions.assertEquals(BigInteger.ONE, repository.getNonce(accAddress));
    }

    @Test
    void incrementAccountNonceTwiceForAlreadyCreatedAccount() {
        RskAddress accAddress = TestUtils.generateAddress("address");

        Repository repository = createRepository();

        repository.createAccount(accAddress);
        repository.increaseNonce(accAddress);
        repository.increaseNonce(accAddress);

        Assertions.assertEquals(2, repository.getNonce(accAddress).longValue());
    }

    @Test
    void incrementAccountBalanceForNewAccount() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = createRepository();

        Assertions.assertEquals(BigInteger.ONE, repository.addBalance(accAddress, Coin.valueOf(1L)).asBigInteger());

        Assertions.assertEquals(BigInteger.ONE, repository.getBalance(accAddress).asBigInteger());
    }

    @Test
    void incrementAccountBalanceForAlreadyCreatedAccount() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = createRepository();

        repository.createAccount(accAddress);
        Assertions.assertEquals(BigInteger.ONE, repository.addBalance(accAddress, Coin.valueOf(1L)).asBigInteger());

        Assertions.assertEquals(BigInteger.ONE, repository.getBalance(accAddress).asBigInteger());
    }

    @Test
    void incrementAccountBalanceTwiceForAlreadyCreatedAccount() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = createRepository();

        repository.createAccount(accAddress);
        Assertions.assertEquals(BigInteger.ONE, repository.addBalance(accAddress, Coin.valueOf(1L)).asBigInteger());
        Assertions.assertEquals(2, repository.addBalance(accAddress, Coin.valueOf(1L)).asBigInteger().longValue());

        Assertions.assertEquals(2, repository.getBalance(accAddress).asBigInteger().longValue());
    }

    @Test
    void isExistReturnsFalseForUnknownAccount() {
        Repository repository = createRepository();

        Assertions.assertFalse(repository.isExist(TestUtils.generateAddress("acc")));
    }

    @Test
    void isExistReturnsTrueForCreatedAccount() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = createRepository();

        repository.createAccount(accAddress);

        Assertions.assertTrue(repository.isExist(accAddress));
    }

    @Test
    void getCodeFromUnknownAccount() {
        Repository repository = createRepository();

        byte[] code = repository.getCode(TestUtils.generateAddress("code"));

        Assertions.assertNotNull(code);
        Assertions.assertEquals(0, code.length);
    }

    @Test
    void getCodeFromAccountWithoutCode() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = createRepository();

        repository.createAccount(accAddress);

        byte[] code = repository.getCode(accAddress);

        // From now on null represents no code, because the code node is not even
        // created
        Assertions.assertNull(code);

    }

    @Test
    void saveAndGetCodeFromAccount() {
        RskAddress accAddress = TestUtils.generateAddress("acc");
        byte[] accCode = new byte[] { 0x01, 0x02, 0x03 };

        Repository repository = createRepository();

        repository.createAccount(accAddress);

        repository.saveCode(accAddress, accCode);

        byte[] code = repository.getCode(accAddress);

        Assertions.assertNotNull(code);
        Assertions.assertArrayEquals(accCode, code);
    }

    @Test
    void hibernateAccount() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = createRepository();

        repository.createAccount(accAddress);
        repository.hibernate(accAddress);

        AccountState accState = repository.getAccountState(accAddress);

        Assertions.assertNotNull(accState);
        Assertions.assertTrue(accState.isHibernated());
    }

    @Test
    void getCodeFromHibernatedAccount() {
        RskAddress accAddress = TestUtils.generateAddress("acc");
        byte[] accCode = new byte[] { 0x01, 0x02, 0x03 };

        Repository repository = createRepository();

        repository.createAccount(accAddress);

        repository.saveCode(accAddress, accCode);
        repository.hibernate(accAddress);

        byte[] code = repository.getCode(accAddress);

        Assertions.assertNotNull(code);
        Assertions.assertEquals(0, code.length);
    }

    @Test
    void startTracking() {
        Repository repository = createRepository();

        Repository track = repository.startTracking();

        Assertions.assertNotNull(track);
    }

    @Test
    void createAccountInTrackAndCommit() {
        RskAddress accAddress = TestUtils.generateAddress("acc");
        Repository repository = createRepository();

        Repository track = repository.startTracking();

        Assertions.assertNotNull(track);
        track.createAccount(accAddress);
        track.commit();

        Assertions.assertTrue(repository.isExist(accAddress));
    }

    @Test
    void createAccountInTrackAndRollback() {
        RskAddress accAddress = TestUtils.generateAddress("acc");
        Repository repository = createRepository();

        Repository track = repository.startTracking();

        Assertions.assertNotNull(track);
        track.createAccount(accAddress);
        track.rollback();

        Assertions.assertFalse(repository.isExist(accAddress));
    }

    @Test
    void getEmptyStorageValue() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = createRepository();

        repository.createAccount(accAddress);
        DataWord value = repository.getStorageValue(accAddress, DataWord.ONE);

        Assertions.assertNull(value);
    }

    @Test
    void setAndGetStorageValue() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = createRepository();

        repository.addStorageRow(accAddress, DataWord.ONE, DataWord.ONE);

        DataWord value = repository.getStorageValue(accAddress, DataWord.ONE);

        // Prior this change repostiory state would point to previous state,
        // so the returned value was null.
        // This semantic has changed. If you modify a repository, you modify
        // a repository. The previous semantics were really really weird.
        Assertions.assertEquals(DataWord.ONE,value);
    }

    @Test
    void setAndGetStorageValueUsingNewRepositoryForTest() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = new MutableRepository(new MutableTrieImpl(null, new Trie()));

        repository.addStorageRow(accAddress, DataWord.ONE, DataWord.ONE);

        DataWord value = repository.getStorageValue(accAddress, DataWord.ONE);

        Assertions.assertNotNull(value);
        Assertions.assertEquals(DataWord.ONE, value);
    }

    @Test
    void setAndGetStorageValueUsingTrack() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = createRepository();

        Repository track = repository.startTracking();

        track.addStorageRow(accAddress, DataWord.ONE, DataWord.ONE);
        track.commit();

        DataWord value = repository.getStorageValue(accAddress, DataWord.ONE);

        Assertions.assertNotNull(value);
        Assertions.assertEquals(DataWord.ONE, value);
    }

    @Test
    void getEmptyStorageBytes() {
        RskAddress accAddress = TestUtils.generateAddress("acc");

        Repository repository = createRepository();

        byte[] bytes = repository.getStorageBytes(accAddress, DataWord.ONE);

        Assertions.assertNull(bytes);
    }

    @Test
    void setAndGetStorageBytesUsingTrack() {
        RskAddress accAddress = TestUtils.generateAddress("acc");
        byte[] bytes = new byte[] { 0x01, 0x02, 0x03 };

        Repository repository = createRepository();

        Repository track = repository.startTracking();
        track.addStorageBytes(accAddress, DataWord.ONE, bytes);
        track.commit();

        byte[] savedBytes = repository.getStorageBytes(accAddress, DataWord.ONE);

        Assertions.assertNotNull(savedBytes);
        Assertions.assertArrayEquals(bytes, savedBytes);
    }

    @Test
    void emptyAccountsKeysOnNonExistentAccount()
    {
        Repository repository = createRepository();

        Set<RskAddress> keys = repository.getAccountsKeys();

        Assertions.assertNotNull(keys);
        Assertions.assertTrue(keys.isEmpty());
    }

    @Test
    void getAccountsKeys() {
        RskAddress accAddress1 = TestUtils.generateAddress("acc");
        RskAddress accAddress2 = TestUtils.generateAddress("acc2");

        Repository repository = createRepositoryWithCache();

        repository.createAccount(accAddress1);
        repository.createAccount(accAddress2);

        Set<RskAddress> keys = repository.getAccountsKeys();
        Assertions.assertNotNull(keys);
        Assertions.assertFalse(keys.isEmpty());
        Assertions.assertEquals(2, keys.size());
        Assertions.assertTrue(keys.contains(accAddress1));
        Assertions.assertTrue(keys.contains(accAddress2));
    }

    @Test
    void isContract() {
        Repository repository = createRepository();
        RskAddress rskAddressA = TestUtils.generateAddress("rskAddressA");
        RskAddress rskAddressB = TestUtils.generateAddress("rskAddressB");

        repository.createAccount(rskAddressA);
        repository.setupContract(rskAddressA);
        byte[] randomCode = TestUtils.generateBytes(RepositoryImplTest.class,"code",32);
        repository.saveCode(rskAddressA, randomCode);

        MatcherAssert.assertThat(repository.isContract(rskAddressA), is(true));
        MatcherAssert.assertThat(repository.isContract(rskAddressB), is(false));
    }

    private static Repository createRepositoryWithCache() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    private static Repository createRepository() {
        return new MutableRepository(new MutableTrieImpl(null, new Trie()));
    }
}
