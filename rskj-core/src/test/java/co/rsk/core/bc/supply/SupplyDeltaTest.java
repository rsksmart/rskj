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
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic of supply conservation, independent of block execution.
 */
class SupplyDeltaTest {

    private static final RskAddress ALICE = new RskAddress("0000000000000000000000000000000000000001");
    private static final RskAddress BOB = new RskAddress("0000000000000000000000000000000000000002");
    private static final RskAddress CAROL = new RskAddress("0000000000000000000000000000000000000003");

    private final Map<RskAddress, Coin> before = new HashMap<>();
    private final Map<RskAddress, Coin> after = new HashMap<>();

    private SupplyDelta deltaOver(RskAddress... modified) {
        return SupplyDelta.between(
                Arrays.asList(modified),
                address -> before.getOrDefault(address, Coin.ZERO),
                address -> after.getOrDefault(address, Coin.ZERO));
    }

    @Test
    void plainTransferConservesSupply() {
        before.put(ALICE, Coin.valueOf(100));
        before.put(BOB, Coin.valueOf(0));
        after.put(ALICE, Coin.valueOf(70));
        after.put(BOB, Coin.valueOf(30));

        SupplyDelta delta = deltaOver(ALICE, BOB);

        assertEquals(Coin.ZERO, delta.getBalance());
        assertTrue(delta.isConserved());
        assertFalse(delta.isCreation());
        assertFalse(delta.isDestruction());
    }

    @Test
    void currencyCreatedIsReportedWithTheAccountsThatGained() {
        before.put(ALICE, Coin.valueOf(100));
        after.put(ALICE, Coin.valueOf(100));
        after.put(BOB, Coin.valueOf(30));

        SupplyDelta delta = deltaOver(ALICE, BOB);

        assertTrue(delta.isCreation());
        assertEquals(Coin.valueOf(30), delta.getBalance());
        assertEquals(Collections.singletonMap(BOB, Coin.valueOf(30)), delta.getGained());
        assertTrue(delta.getLost().isEmpty());
    }

    @Test
    void currencyDestroyedIsReportedWithTheAccountsThatLost() {
        before.put(ALICE, Coin.valueOf(100));
        after.put(ALICE, Coin.valueOf(40));

        SupplyDelta delta = deltaOver(ALICE);

        assertTrue(delta.isDestruction());
        assertFalse(delta.isCreation());
        assertEquals(Coin.valueOf(-60), delta.getBalance());
        assertEquals(Collections.singletonMap(ALICE, Coin.valueOf(60)), delta.getLost());
        assertTrue(delta.getGained().isEmpty());
    }

    /**
     * A destroyed account reads as zero in the "after" view, so its whole balance counts as outflow
     * exactly once -- it is not counted again for having ceased to exist.
     */
    @Test
    void destroyedAccountCountsItsWholeBalanceAsOutflowOnce() {
        before.put(ALICE, Coin.valueOf(100));
        // ALICE is absent from `after`: destroyed.

        SupplyDelta delta = deltaOver(ALICE);

        assertEquals(Coin.valueOf(-100), delta.getBalance());
        assertEquals(Coin.valueOf(100), delta.getOutflow());
        assertEquals(Coin.ZERO, delta.getInflow());
    }

    /**
     * An account destroyed and re-created within the same scope must be accounted once, at its
     * final balance. It appears once in the modified set, and each view is read once, so the
     * intermediate destruction is invisible -- which is the required behaviour.
     */
    @Test
    void accountDestroyedThenRecreatedIsAccountedOnceAtItsFinalBalance() {
        before.put(ALICE, Coin.valueOf(100));
        after.put(ALICE, Coin.valueOf(100));

        // The modified set is a set: destroying and re-creating still yields one entry.
        SupplyDelta delta = SupplyDelta.between(
                Set.of(ALICE),
                address -> before.getOrDefault(address, Coin.ZERO),
                address -> after.getOrDefault(address, Coin.ZERO));

        assertTrue(delta.isConserved());
        assertEquals(Coin.ZERO, delta.getInflow());
        assertEquals(Coin.ZERO, delta.getOutflow());
    }

    /**
     * Accounts that were merely read are not in the modified set, so a scope that only reads
     * reports nothing -- otherwise ordinary blocks would report changes that did not occur.
     */
    @Test
    void accountsThatWereOnlyReadAreExcluded() {
        before.put(ALICE, Coin.valueOf(100));
        before.put(CAROL, Coin.valueOf(5000));
        after.put(ALICE, Coin.valueOf(70));
        after.put(BOB, Coin.valueOf(30));
        after.put(CAROL, Coin.valueOf(5000));

        // CAROL was read but never written, so she is not in the modified set.
        SupplyDelta delta = deltaOver(ALICE, BOB);

        assertTrue(delta.isConserved());
        assertFalse(delta.getGained().containsKey(CAROL));
        assertFalse(delta.getLost().containsKey(CAROL));
    }

    @Test
    void anEmptyScopeIsConserved() {
        assertTrue(SupplyDelta.between(Collections.emptySet(), a -> Coin.ZERO, a -> Coin.ZERO).isConserved());
    }

    @Test
    void anAccountModifiedWithoutItsBalanceChangingContributesNothing() {
        // e.g. a nonce bump or a storage write.
        before.put(ALICE, Coin.valueOf(100));
        after.put(ALICE, Coin.valueOf(100));

        SupplyDelta delta = deltaOver(ALICE);

        assertTrue(delta.isConserved());
        assertTrue(delta.getGained().isEmpty());
        assertTrue(delta.getLost().isEmpty());
    }

    /**
     * Block-level netting must not hide a creation: this is why the per-transaction scope is the
     * primary check. Here one account gains exactly what another loses, but they were modified by
     * different transactions.
     */
    @Test
    void netZeroAcrossAccountsLooksConservedWhichIsWhyPerTransactionScopeExists() {
        before.put(ALICE, Coin.valueOf(100));
        after.put(ALICE, Coin.ZERO);
        after.put(BOB, Coin.valueOf(100));

        SupplyDelta blockScope = deltaOver(ALICE, BOB);
        assertTrue(blockScope.isConserved());

        // The transaction that only credited BOB is a creation on its own.
        SupplyDelta txScope = deltaOver(BOB);
        assertTrue(txScope.isCreation());
        assertEquals(Coin.valueOf(100), txScope.getBalance());
    }

    @Test
    void pendingInflowModelsAFeeDebitedNowAndCreditedLater() {
        before.put(ALICE, Coin.valueOf(100));
        after.put(ALICE, Coin.valueOf(90));

        // Without modelling the fee, the transaction looks like a burn.
        SupplyDelta raw = deltaOver(ALICE);
        assertTrue(raw.isDestruction());

        SupplyDelta modelled = raw.withPendingInflow(Coin.valueOf(10));
        assertTrue(modelled.isConserved());
    }

    @Test
    void settledInflowNetsOutABulkCreditAlreadyAccountedFor() {
        before.put(ALICE, Coin.ZERO);
        after.put(ALICE, Coin.valueOf(10));

        // Without netting, the bulk credit is indistinguishable from a mint.
        SupplyDelta raw = deltaOver(ALICE);
        assertTrue(raw.isCreation());

        SupplyDelta modelled = raw.withSettledInflow(Coin.valueOf(10));
        assertTrue(modelled.isConserved());
    }

    @Test
    void adjustmentsOfZeroLeaveTheDeltaUntouched() {
        before.put(ALICE, Coin.valueOf(100));
        after.put(ALICE, Coin.valueOf(90));

        SupplyDelta raw = deltaOver(ALICE);

        assertEquals(raw.getBalance(), raw.withPendingInflow(Coin.ZERO).getBalance());
        assertEquals(raw.getBalance(), raw.withSettledInflow(Coin.ZERO).getBalance());
    }
}
