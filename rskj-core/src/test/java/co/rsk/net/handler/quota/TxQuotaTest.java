/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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
package co.rsk.net.handler.quota;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.util.TimeProvider;
import org.ethereum.TestUtils;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TxQuotaTest {

    private static final long MAX_GAS_PER_SECOND = Math.round(6_800_000 * 0.9);
    private static final long MAX_QUOTA = MAX_GAS_PER_SECOND * 2000;

    private TimeProvider timeProvider;

    @BeforeEach
    void setUp() {
        timeProvider = mock(TimeProvider.class);
    }

    @Test
    void acceptVirtualGasConsumption() {
        Transaction tx = mock(Transaction.class);
        Keccak256 txHash = TestUtils.randomHash();
        when(tx.getHash()).thenReturn(txHash);

        RskAddress address = TestUtils.randomAddress();

        TxQuota txQuota = TxQuota.createNew(address, txHash, 10, System::currentTimeMillis);

        assertTrue(txQuota.acceptVirtualGasConsumption(9, tx, 10));
        assertFalse(txQuota.acceptVirtualGasConsumption(2, tx, 10));
        assertTrue(txQuota.acceptVirtualGasConsumption(1, tx, 10));
    }

    @Test
    void forceVirtualGasSubtraction() {
        Transaction tx = mock(Transaction.class);
        Keccak256 txHash = TestUtils.randomHash();
        when(tx.getHash()).thenReturn(txHash);

        RskAddress address = TestUtils.randomAddress();

        TxQuota txQuota = TxQuota.createNew(address, txHash, 10, System::currentTimeMillis);
        assertTrue(txQuota.forceVirtualGasSubtraction(5, tx, 10));
        assertTrue(txQuota.forceVirtualGasSubtraction(4, tx, 10));
        assertFalse(txQuota.forceVirtualGasSubtraction(2, tx, 10));
        assertFalse(txQuota.forceVirtualGasSubtraction(1, tx, 10));
    }

    @Test
    void refresh() {
        long currentTime = System.currentTimeMillis();
        when(timeProvider.currentTimeMillis()).thenReturn(currentTime);

        Transaction tx = mock(Transaction.class);
        Keccak256 txHash = TestUtils.randomHash();
        when(tx.getHash()).thenReturn(txHash);

        RskAddress address = TestUtils.randomAddress();

        TxQuota txQuota = TxQuota.createNew(address, tx.getHash(), MAX_QUOTA, timeProvider);
        assertFalse(txQuota.acceptVirtualGasConsumption(MAX_QUOTA + 1, tx, 10), "should reject tx over initial limit");
        assertTrue(txQuota.acceptVirtualGasConsumption(MAX_QUOTA - 1, tx, 10), "should accept tx below initial limit");

        long timeElapsed = 1;
        double accumulatedGasApprox = timeElapsed / 1000d * MAX_GAS_PER_SECOND;
        when(timeProvider.currentTimeMillis()).thenReturn(currentTime += timeElapsed);
        txQuota.refresh(address, MAX_GAS_PER_SECOND, MAX_QUOTA);
        assertFalse(txQuota.acceptVirtualGasConsumption(accumulatedGasApprox + 1000, tx, 10), "should reject tx over refreshed limit (not enough quiet time)");

        timeElapsed = 30;
        accumulatedGasApprox = timeElapsed / 1000d * MAX_GAS_PER_SECOND;
        when(timeProvider.currentTimeMillis()).thenReturn(currentTime += timeElapsed);
        txQuota.refresh(address, MAX_GAS_PER_SECOND, MAX_QUOTA);
        assertTrue(txQuota.acceptVirtualGasConsumption(accumulatedGasApprox, tx, 10), "should accept tx when enough gas accumulated (enough quiet time)");
    }
}
