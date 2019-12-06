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
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Random;

public class TopRepositoryTest {
    private Random random = new Random();

    @Test
    public void createWithTrie() {
        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie);

        Assert.assertNotNull(repository.getTrie());
        Assert.assertSame(trie, repository.getTrie());
    }

    @Test
    public void createAccount() {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie);

        AccountState accountState = repository.createAccount(address);

        Assert.assertNotNull(accountState);

        Assert.assertNotNull(repository.getTrie());
        Assert.assertSame(trie, repository.getTrie());

        AccountState result = repository.getAccountState(address);

        Assert.assertNotNull(result);
        Assert.assertSame(accountState, result);

        Assert.assertNull(trie.get(trieKeyMapper.getAccountKey(address)));
    }

    @Test
    public void getUnknownAccount() {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie);

        AccountState accountState = repository.getAccountState(address);

        Assert.assertNull(accountState);

        Assert.assertNotNull(repository.getTrie());
        Assert.assertSame(trie, repository.getTrie());

        Assert.assertNull(trie.get(trieKeyMapper.getAccountKey(address)));
    }

    @Test
    public void getAccountFromTrie() {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(1000000));

        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        Trie trie = new Trie();

        trie = trie.put(trieKeyMapper.getAccountKey(address), accountState.getEncoded());

        TopRepository repository = new TopRepository(trie);

        AccountState result = repository.getAccountState(address);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(accountState.getEncoded(), result.getEncoded());
    }

    @Test
    public void getAccountFromTrieAndCommit() {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(1000000));

        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        Trie trie = new Trie();

        trie = trie.put(trieKeyMapper.getAccountKey(address), accountState.getEncoded());

        TopRepository repository = new TopRepository(trie);

        AccountState result = repository.getAccountState(address);

        repository.commit();

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(accountState.getEncoded(), result.getEncoded());

        Assert.assertSame(trie, repository.getTrie());
    }

    @Test
    public void createAndCommitAccount() {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie);

        AccountState accountState = repository.createAccount(address);

        repository.commit();

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
        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie);

        Assert.assertEquals(Coin.ZERO, repository.getBalance(address));
        Assert.assertEquals(BigInteger.ZERO, repository.getNonce(address));

        repository.commit();

        Assert.assertSame(trie, repository.getTrie());
    }

    @Test
    public void getBalanceAndNonceFromKnownAccount() {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(42));

        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        Trie trie = new Trie();

        trie = trie.put(trieKeyMapper.getAccountKey(address), accountState.getEncoded());

        TopRepository repository = new TopRepository(trie);

        Assert.assertEquals(Coin.valueOf(42), repository.getBalance(address));
        Assert.assertEquals(BigInteger.TEN, repository.getNonce(address));

        repository.commit();

        Assert.assertSame(trie, repository.getTrie());
    }

    @Test
    public void addBalanceToUnknownAccount() {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie);

        Assert.assertEquals(Coin.valueOf(42), repository.addBalance(address, Coin.valueOf(42)));

        Assert.assertEquals(Coin.valueOf(42), repository.getBalance(address));
        Assert.assertEquals(BigInteger.ZERO, repository.getNonce(address));

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState accountState = new AccountState(repository.getTrie().get(accountKey));

        Assert.assertNotNull(accountState);
        Assert.assertEquals(Coin.valueOf(42), accountState.getBalance());
        Assert.assertEquals(BigInteger.ZERO, accountState.getNonce());
    }

    @Test
    public void addBalanceToKnownAccount() {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(42));

        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        trie = trie.put(accountKey, accountState.getEncoded());

        TopRepository repository = new TopRepository(trie);

        Assert.assertEquals(Coin.valueOf(50), repository.addBalance(address, Coin.valueOf(8)));

        Assert.assertEquals(Coin.valueOf(50), repository.getBalance(address));
        Assert.assertEquals(BigInteger.TEN, repository.getNonce(address));

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState newAccountState = new AccountState(repository.getTrie().get(accountKey));

        Assert.assertNotNull(newAccountState);
        Assert.assertEquals(Coin.valueOf(50), newAccountState.getBalance());
        Assert.assertEquals(BigInteger.TEN, newAccountState.getNonce());
    }

    @Test
    public void increaseNonceToUnknownAccount() {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie);

        Assert.assertEquals(BigInteger.ONE, repository.increaseNonce(address));

        Assert.assertEquals(Coin.ZERO, repository.getBalance(address));
        Assert.assertEquals(BigInteger.ONE, repository.getNonce(address));

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState accountState = new AccountState(repository.getTrie().get(accountKey));

        Assert.assertNotNull(accountState);
        Assert.assertEquals(Coin.ZERO, accountState.getBalance());
        Assert.assertEquals(BigInteger.ONE, accountState.getNonce());
    }

    @Test
    public void increaseNonceToKnownAccount() {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

        AccountState accountState = new AccountState(BigInteger.TEN, Coin.valueOf(42));

        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        trie = trie.put(accountKey, accountState.getEncoded());

        TopRepository repository = new TopRepository(trie);

        Assert.assertEquals(BigInteger.valueOf(11), repository.increaseNonce((address)));

        Assert.assertEquals(Coin.valueOf(42), repository.getBalance(address));
        Assert.assertEquals(BigInteger.valueOf(11), repository.getNonce(address));

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState newAccountState = new AccountState(repository.getTrie().get(accountKey));

        Assert.assertNotNull(newAccountState);
        Assert.assertEquals(Coin.valueOf(42), newAccountState.getBalance());
        Assert.assertEquals(BigInteger.valueOf(11), newAccountState.getNonce());
    }

    @Test
    public void setNonceToUnknownAccount() {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        TopRepository repository = new TopRepository(trie);

        repository.setNonce(address, BigInteger.TEN);

        Assert.assertEquals(Coin.ZERO, repository.getBalance(address));
        Assert.assertEquals(BigInteger.TEN, repository.getNonce(address));

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState accountState = new AccountState(repository.getTrie().get(accountKey));

        Assert.assertNotNull(accountState);
        Assert.assertEquals(Coin.ZERO, accountState.getBalance());
        Assert.assertEquals(BigInteger.TEN, accountState.getNonce());
    }

    @Test
    public void setNonceToKnownAccount() {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

        AccountState accountState = new AccountState(BigInteger.ONE, Coin.valueOf(42));

        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        this.random.nextBytes(bytes);

        RskAddress address = new RskAddress(bytes);

        byte[] accountKey = trieKeyMapper.getAccountKey(address);

        Trie trie = new Trie();

        trie = trie.put(accountKey, accountState.getEncoded());

        TopRepository repository = new TopRepository(trie);

        repository.setNonce(address, BigInteger.TEN);

        Assert.assertEquals(Coin.valueOf(42), repository.getBalance(address));
        Assert.assertEquals(BigInteger.TEN, repository.getNonce(address));

        repository.commit();

        Assert.assertNotSame(trie, repository.getTrie());

        AccountState newAccountState = new AccountState(repository.getTrie().get(accountKey));

        Assert.assertNotNull(newAccountState);
        Assert.assertEquals(Coin.valueOf(42), newAccountState.getBalance());
        Assert.assertEquals(BigInteger.TEN, newAccountState.getNonce());
    }
}
