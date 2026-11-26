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
package org.ethereum.db;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.remasc.RemascTransaction;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The set of accounts a repository reports as modified is what the supply check measures over, so
 * it must contain every account whose balance could have changed, and nothing that was only read.
 */
class MutableRepositoryModifiedAccountsTest {

    private static final RskAddress ALICE = new RskAddress("0000000000000000000000000000000000000001");
    private static final RskAddress BOB = new RskAddress("0000000000000000000000000000000000000002");

    private Repository repository;

    @BeforeEach
    void setUp() {
        repository = new MutableRepository(new TrieStoreImpl(new HashMapDB()), new Trie());
    }

    @Test
    void aFreshRepositoryHasModifiedNothing() {
        assertTrue(repository.getModifiedAccounts().isEmpty());
    }

    @Test
    void readingDoesNotCountAsModifying() {
        repository.addBalance(ALICE, Coin.valueOf(100));
        repository.commit();

        Repository track = repository.startTracking();
        track.getBalance(ALICE);
        track.getNonce(ALICE);
        track.isExist(ALICE);
        track.getCode(ALICE);
        track.getStorageValue(ALICE, DataWord.ZERO);

        assertTrue(track.getModifiedAccounts().isEmpty(),
                "accounts that were only read must not be reported as modified");
    }

    @Test
    void addingBalanceCountsAsModifying() {
        repository.addBalance(ALICE, Coin.valueOf(100));

        assertTrue(repository.getModifiedAccounts().contains(ALICE));
    }

    @Test
    void transferMarksBothSides() {
        repository.addBalance(ALICE, Coin.valueOf(100));
        repository.commit();

        Repository track = repository.startTracking();
        track.transfer(ALICE, BOB, Coin.valueOf(30));

        assertTrue(track.getModifiedAccounts().contains(ALICE));
        assertTrue(track.getModifiedAccounts().contains(BOB));
    }

    @Test
    void creatingAndDeletingAnAccountBothCountAsModifying() {
        repository.createAccount(ALICE);
        assertTrue(repository.getModifiedAccounts().contains(ALICE));

        Repository track = repository.startTracking();
        track.delete(ALICE);
        assertTrue(track.getModifiedAccounts().contains(ALICE));
    }

    @Test
    void nonceChangesCountAsModifyingToo() {
        // The balance did not change, but the account did; the delta arithmetic will score it zero.
        repository.increaseNonce(ALICE);
        assertTrue(repository.getModifiedAccounts().contains(ALICE));

        Repository track = repository.startTracking();
        track.setNonce(BOB, BigInteger.TEN);
        assertTrue(track.getModifiedAccounts().contains(BOB));
    }

    /**
     * A zero-value addBalance is a no-op in the repository, and reporting the account as modified
     * would be harmless but misleading. Either way the delta arithmetic scores it zero.
     */
    @Test
    void addingZeroBalanceToAnExistingAccountScoresZero() {
        repository.addBalance(ALICE, Coin.valueOf(100));
        repository.commit();

        Repository track = repository.startTracking();
        track.addBalance(ALICE, Coin.ZERO);

        assertEquals(Coin.valueOf(100), track.getBalance(ALICE));
    }

    @Test
    void committingPropagatesModifiedAccountsToTheParent() {
        Repository track = repository.startTracking();
        track.addBalance(ALICE, Coin.valueOf(100));

        assertFalse(repository.getModifiedAccounts().contains(ALICE),
                "the parent must not see the child's writes before they are committed");

        track.commit();

        assertTrue(repository.getModifiedAccounts().contains(ALICE));
    }

    @Test
    void propagationWorksThroughSeveralLevels() {
        Repository txTrack = repository.startTracking();
        Repository vmTrack = txTrack.startTracking();

        vmTrack.addBalance(BOB, Coin.valueOf(5));
        vmTrack.commit();
        assertTrue(txTrack.getModifiedAccounts().contains(BOB));

        txTrack.commit();
        assertTrue(repository.getModifiedAccounts().contains(BOB));
    }

    @Test
    void rollingBackDiscardsModifiedAccounts() {
        Repository track = repository.startTracking();
        track.addBalance(ALICE, Coin.valueOf(100));
        assertTrue(track.getModifiedAccounts().contains(ALICE));

        track.rollback();

        assertTrue(track.getModifiedAccounts().isEmpty());
        assertFalse(repository.getModifiedAccounts().contains(ALICE));
    }

    /**
     * The REMASC sender address encodes as a single zero byte rather than twenty, so its trie key
     * is shorter than an ordinary account's. Tracking by address rather than by trie key means that
     * special case costs nothing.
     */
    @Test
    void theRemascSenderIsTrackedLikeAnyOtherAccount() {
        RskAddress remascSender = RemascTransaction.REMASC_ADDRESS;

        repository.addBalance(remascSender, Coin.valueOf(7));

        assertTrue(repository.getModifiedAccounts().contains(remascSender));
        assertEquals(Coin.valueOf(7), repository.getBalance(remascSender));
    }
}
