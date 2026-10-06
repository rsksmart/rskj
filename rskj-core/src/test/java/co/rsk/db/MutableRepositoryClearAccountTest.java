/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RSKIP701: a cleared account keeps its nonce and its flags, loses its code, its storage and its
 * balance, and its node becomes a terminal node in the trie.
 */
class MutableRepositoryClearAccountTest {

    private static final RskAddress ACCOUNT = new RskAddress("7c0d52faab596c08f484e3478aebc6205f3f5d8c");
    private static final BigInteger NONCE = BigInteger.ONE;
    private static final Coin BALANCE = Coin.valueOf(1000);
    // PUSH1 42 PUSH1 0 SSTORE PUSH1 0 CALLDATALOAD SELFDESTRUCT
    private static final byte[] CODE = Hex.decode("602a600055600035ff");
    private static final DataWord SLOT = DataWord.ZERO;
    private static final DataWord SLOT_VALUE = DataWord.valueOf(42);

    private final TrieKeyMapper trieKeyMapper = new TrieKeyMapper();
    private TrieStore trieStore;
    private MutableRepository repository;

    @BeforeEach
    void setUp() {
        trieStore = new TrieStoreImpl(new HashMapDB());
        repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));
    }

    @Test
    void contractKeepsNonceAndLosesCodeStorageAndBalance() {
        installContract();

        repository.clearAccount(ACCOUNT);
        repository.commit();

        assertClearedShape(0);
        assertFalse(repository.hasDelegationAuthority(ACCOUNT));

        AccountState expectedAccount = new AccountState(NONCE, Coin.ZERO);
        assertArrayEquals(rootWithOnlyAccountNode(expectedAccount), repository.getRoot());
    }

    @Test
    void delegationAuthorityFlagSurvivesClearing() {
        installContract();
        repository.setDelegationAuthority(ACCOUNT);
        int flagsBeforeClearing = repository.getAccountState(ACCOUNT).getStateFlags();

        repository.clearAccount(ACCOUNT);
        repository.commit();

        assertClearedShape(flagsBeforeClearing);
        assertTrue(repository.hasDelegationAuthority(ACCOUNT));

        AccountState expectedAccount = new AccountState(NONCE, Coin.ZERO);
        expectedAccount.setDelegationAuthority();
        assertArrayEquals(rootWithOnlyAccountNode(expectedAccount), repository.getRoot());
    }

    @Test
    void clearingThroughTrackingRepositoryIsVisibleBeforeAndAfterCommit() {
        installContract();
        Repository track = repository.startTracking();

        track.clearAccount(ACCOUNT);

        assertClearedShape(track, 0);
        assertEquals(CODE.length, repository.getCodeLength(ACCOUNT));

        track.commit();

        assertClearedShape(repository, 0);
        AccountState expectedAccount = new AccountState(NONCE, Coin.ZERO);
        assertArrayEquals(rootWithOnlyAccountNode(expectedAccount), repository.getRoot());
    }

    @Test
    void missingAccountIsLeftMissing() {
        repository.clearAccount(ACCOUNT);
        repository.commit();

        assertFalse(repository.isExist(ACCOUNT));
    }

    private void installContract() {
        repository.createAccount(ACCOUNT);
        repository.setNonce(ACCOUNT, NONCE);
        repository.addBalance(ACCOUNT, BALANCE);
        repository.initializeStorage(ACCOUNT);
        repository.saveCode(ACCOUNT, CODE);
        repository.addStorageRow(ACCOUNT, SLOT, SLOT_VALUE);

        assertEquals(SLOT_VALUE, repository.getStorageValue(ACCOUNT, SLOT));
        assertEquals(CODE.length, repository.getCodeLength(ACCOUNT));
    }

    private void assertClearedShape(int expectedFlags) {
        assertClearedShape(repository, expectedFlags);
    }

    private void assertClearedShape(Repository repository, int expectedFlags) {
        assertTrue(repository.isExist(ACCOUNT));
        assertEquals(NONCE, repository.getNonce(ACCOUNT));
        assertEquals(Coin.ZERO, repository.getBalance(ACCOUNT));
        assertEquals(0, repository.getCodeLength(ACCOUNT));
        assertEquals(0, repository.getStorageKeysCount(ACCOUNT));
        assertFalse(repository.hasInitializedStorage(ACCOUNT));
        assertNull(repository.getStorageValue(ACCOUNT, SLOT));
        assertEquals(expectedFlags, repository.getAccountState(ACCOUNT).getStateFlags());
        assertEquals(MutableRepository.KECCAK_256_OF_EMPTY_ARRAY, repository.getCodeHashStandard(ACCOUNT));
    }

    /**
     * The expected trie is built directly: one put of the account key with the expected account
     * state, nothing under it.
     */
    private byte[] rootWithOnlyAccountNode(AccountState accountState) {
        Trie expected = new Trie(trieStore).put(trieKeyMapper.getAccountKey(ACCOUNT), accountState.getEncoded());
        return expected.getHash().getBytes();
    }
}
