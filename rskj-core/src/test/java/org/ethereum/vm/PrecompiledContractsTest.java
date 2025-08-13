/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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
package org.ethereum.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests for the PrecompiledContracts class, specifically testing the
 * getMaxInput() method
 * functionality for different types of precompiled contracts.
 */
class PrecompiledContractsTest {

    @Test
    void shouldReturnNoLimitForContractsWithoutMaxInput() {
        // Test that contracts without maxInput limits return -1 (NO_LIMIT_ON_MAX_INPUT)

        // Identity contract should have no limit
        PrecompiledContracts.Identity identity = new PrecompiledContracts.Identity();
        assertEquals(PrecompiledContracts.NO_LIMIT_ON_MAX_INPUT, identity.getMaxInput());

        // SHA256 contract should have no limit
        PrecompiledContracts.Sha256 sha256 = new PrecompiledContracts.Sha256();
        assertEquals(PrecompiledContracts.NO_LIMIT_ON_MAX_INPUT, sha256.getMaxInput());

        // RIPEMD160 contract should have no limit
        PrecompiledContracts.Ripempd160 ripempd160 = new PrecompiledContracts.Ripempd160();
        assertEquals(PrecompiledContracts.NO_LIMIT_ON_MAX_INPUT, ripempd160.getMaxInput());

        // BigIntegerModexp contract should have no limit
        PrecompiledContracts.BigIntegerModexp bigIntegerModexp = new PrecompiledContracts.BigIntegerModexp();
        assertEquals(PrecompiledContracts.NO_LIMIT_ON_MAX_INPUT, bigIntegerModexp.getMaxInput());
    }

    @Test
    void shouldReturnCorrectMaxInputForContractsWithLimits() {
        // Test that contracts with maxInput limits return the correct values

        // ECRecover should have 128 bytes limit
        PrecompiledContracts.ECRecover ecRecover = new PrecompiledContracts.ECRecover();
        assertEquals(128, ecRecover.getMaxInput());

        // Blake2F should have 213 bytes limit
        PrecompiledContracts.Blake2F blake2F = new PrecompiledContracts.Blake2F();
        assertEquals(213, blake2F.getMaxInput());
    }
}
