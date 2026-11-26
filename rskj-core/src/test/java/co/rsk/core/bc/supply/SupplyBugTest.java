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
import co.rsk.trie.Trie;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import co.rsk.trie.TrieStoreImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deliberate bug must be inert unless it was explicitly asked for.
 */
class SupplyBugTest {

    private Repository newRepository() {
        return new MutableRepository(new TrieStoreImpl(new HashMapDB()), new Trie());
    }

    @Test
    void notAskedForMeansDisabled() {
        assertSame(SupplyBug.DISABLED, SupplyBug.create(false, false));
        assertFalse(SupplyBug.create(false, false).isEnabled());
    }

    /**
     * The flag is refused outright on mainnet, where enabling it could only ever do harm.
     */
    @Test
    void refusedOnMainnetEvenWhenAskedFor() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> SupplyBug.create(true, true));
        assertTrue(e.getMessage().contains("mainnet"));
    }

    @Test
    void notAskedForIsStillDisabledOnMainnet() {
        assertSame(SupplyBug.DISABLED, SupplyBug.create(false, true));
    }

    /**
     * The single method that touches state does nothing at all when the bug is off. This is the
     * property that matters: no flag, no mint, ever.
     */
    @Test
    void disabledBugTouchesNoState() {
        Repository repository = newRepository();
        Coin balanceBefore = repository.getBalance(SupplyBug.BENEFICIARY);

        SupplyBug.DISABLED.mintFromNowhere(repository);

        assertEquals(balanceBefore, repository.getBalance(SupplyBug.BENEFICIARY));
        assertEquals(Coin.ZERO, repository.getBalance(SupplyBug.BENEFICIARY));
        assertTrue(repository.getModifiedAccounts().isEmpty());
    }

    @Test
    void enabledBugCreatesCurrencyFromNothing() {
        Repository repository = newRepository();

        SupplyBug bug = SupplyBug.create(true, false);
        assertTrue(bug.isEnabled());

        bug.mintFromNowhere(repository);

        assertEquals(SupplyBug.MINTED_AMOUNT, repository.getBalance(SupplyBug.BENEFICIARY));
        assertTrue(repository.getModifiedAccounts().contains(SupplyBug.BENEFICIARY));
    }

    /**
     * What the bug does is, by construction, exactly what the check is built to catch.
     */
    @Test
    void whatTheBugDoesIsDetectedAsACreation() {
        Repository repository = newRepository();

        SupplyBug.create(true, false).mintFromNowhere(repository);

        SupplyDelta delta = SupplyDelta.between(
                repository.getModifiedAccounts(),
                address -> Coin.ZERO,
                repository::getBalance);

        assertTrue(delta.isCreation());
        assertEquals(SupplyBug.MINTED_AMOUNT, delta.getBalance());
        assertEquals(SupplyBug.MINTED_AMOUNT, delta.getGained().get(SupplyBug.BENEFICIARY));
    }
}
