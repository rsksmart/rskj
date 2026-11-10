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
package co.rsk.core.bc.supply;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The two scopes and the cross-check between them.
 */
class SupplyConservationCheckTest {

    private static final RskAddress ALICE = new RskAddress("0000000000000000000000000000000000000001");
    private static final RskAddress BOB = new RskAddress("0000000000000000000000000000000000000002");

    private final Map<RskAddress, Coin> before = new HashMap<>();
    private final Map<RskAddress, Coin> after = new HashMap<>();

    private Block block;
    private Transaction tx;
    private SupplyConservationCheck check;

    @BeforeEach
    void setUp() {
        block = mock(Block.class);
        when(block.getNumber()).thenReturn(42L);
        when(block.getPrintableHash()).thenReturn("abcdef");

        tx = mock(Transaction.class);
        when(tx.getHash()).thenReturn(new co.rsk.crypto.Keccak256(new byte[32]));

        check = new SupplyConservationCheck(block);
    }

    private BalanceLookup beforeView() {
        return address -> before.getOrDefault(address, Coin.ZERO);
    }

    private BalanceLookup afterView() {
        return address -> after.getOrDefault(address, Coin.ZERO);
    }

    @Test
    void conservedTransactionIsNotACreation() {
        before.put(ALICE, Coin.valueOf(100));
        after.put(ALICE, Coin.valueOf(60));
        after.put(BOB, Coin.valueOf(40));

        SupplyDelta delta = check.checkTransaction(tx, 0, Arrays.asList(ALICE, BOB), beforeView(), afterView());

        assertTrue(delta.isConserved());
        assertFalse(delta.isCreation());
        assertEquals(Coin.ZERO, check.getSumOfTransactionDeltas());
    }

    @Test
    void createdCurrencyIsReportedAsACreation() {
        after.put(BOB, Coin.valueOf(40));

        SupplyDelta delta = check.checkTransaction(tx, 0, Collections.singletonList(BOB), beforeView(), afterView());

        assertTrue(delta.isCreation());
        assertEquals(Coin.valueOf(40), check.getSumOfTransactionDeltas());
    }

    /**
     * A destroyed amount is not an error: destruction has legitimate causes, and destroying
     * currency cannot steal from anyone.
     */
    @Test
    void destroyedCurrencyIsNotACreation() {
        before.put(ALICE, Coin.valueOf(100));
        after.put(ALICE, Coin.valueOf(90));

        SupplyDelta delta = check.checkTransaction(tx, 0, Collections.singletonList(ALICE), beforeView(), afterView());

        assertTrue(delta.isDestruction());
        assertFalse(delta.isCreation());
        assertEquals(Coin.valueOf(-10), check.getSumOfTransactionDeltas());
    }

    /**
     * Per-transaction B values are summed so the block scope can be cross-checked against them.
     */
    @Test
    void perTransactionDeltasAccumulate() {
        after.put(BOB, Coin.valueOf(40));
        check.checkTransaction(tx, 0, Collections.singletonList(BOB), beforeView(), afterView());

        before.put(ALICE, Coin.valueOf(100));
        after.put(ALICE, Coin.valueOf(70));
        check.checkTransaction(tx, 1, Collections.singletonList(ALICE), beforeView(), afterView());

        // +40 then -30
        assertEquals(Coin.valueOf(10), check.getSumOfTransactionDeltas());
    }

    @Test
    void alreadyComputedSumsCanBeRecorded() {
        check.recordTransactionDeltas(Coin.valueOf(7));
        check.recordTransactionDeltas(Coin.valueOf(-2));

        assertEquals(Coin.valueOf(5), check.getSumOfTransactionDeltas());
    }

    /**
     * Block-level netting hides a creation whenever one transaction mints an amount and another
     * destroys the same amount. The block scope is conserved, but the per-transaction scope caught
     * it -- which is why the per-transaction check is the primary one.
     */
    @Test
    void blockNettingHidesWhatThePerTransactionScopeCatches() {
        // tx 0 mints 40 to BOB
        after.put(BOB, Coin.valueOf(40));
        SupplyDelta mint = check.checkTransaction(tx, 0, Collections.singletonList(BOB), beforeView(), afterView());
        assertTrue(mint.isCreation());

        // tx 1 burns 40 from ALICE
        before.put(ALICE, Coin.valueOf(40));
        after.put(ALICE, Coin.ZERO);
        SupplyDelta burn = check.checkTransaction(tx, 1, Collections.singletonList(ALICE), beforeView(), afterView());
        assertTrue(burn.isDestruction());

        // The block as a whole nets to zero and looks fine.
        SupplyDelta blockDelta = check.checkBlock(Arrays.asList(ALICE, BOB), beforeView(), afterView());
        assertTrue(blockDelta.isConserved());
        assertEquals(Coin.ZERO, check.getSumOfTransactionDeltas());
    }

    /**
     * The block scope is a cross-check on value moved outside any transaction. It is reported but
     * does not by itself invalidate the block; the block-level B decides that.
     */
    @Test
    void valueMovedOutsideAnyTransactionShowsAsADiscrepancy() {
        // A conserved transaction.
        before.put(ALICE, Coin.valueOf(100));
        after.put(ALICE, Coin.valueOf(60));
        after.put(BOB, Coin.valueOf(40));
        check.checkTransaction(tx, 0, Arrays.asList(ALICE, BOB), beforeView(), afterView());
        assertEquals(Coin.ZERO, check.getSumOfTransactionDeltas());

        // But the block also burned something outside the transaction loop.
        after.put(ALICE, Coin.valueOf(50));

        SupplyDelta blockDelta = check.checkBlock(Arrays.asList(ALICE, BOB), beforeView(), afterView());

        assertEquals(Coin.valueOf(-10), blockDelta.getBalance());
        assertFalse(blockDelta.getBalance().equals(check.getSumOfTransactionDeltas()));
        // Reported, but not a creation, so the block stands.
        assertFalse(blockDelta.isCreation());
    }

    @Test
    void blockThatCreatesCurrencyIsACreation() {
        after.put(BOB, Coin.valueOf(40));

        SupplyDelta blockDelta = check.checkBlock(Collections.singletonList(BOB), beforeView(), afterView());

        assertTrue(blockDelta.isCreation());
        assertEquals(Coin.valueOf(40), blockDelta.getBalance());
        assertEquals(Coin.valueOf(40), blockDelta.getGained().get(BOB));
    }
}
