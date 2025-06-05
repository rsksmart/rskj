/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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
package co.rsk.pcc.secp256k1;

import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.crypto.signature.Secp256k1AdditionTestHelper;
import org.ethereum.crypto.signature.Secp256k1Service;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Secp256k1 Addition precompiled contract
 */
class Secp256k1AdditionTest {
    private Secp256k1Addition secp256k1Addition;
    private Secp256k1Service secp256k1Service;

    @BeforeEach
    void setUp() {
        secp256k1Service = Secp256k1.getInstance();
        secp256k1Addition = new Secp256k1Addition(secp256k1Service);
    }

    @Test
    void testAdditionOperations() {
        // Test the underlying secp256k1 addition operations
        Secp256k1AdditionTestHelper.testAddition(secp256k1Service);
    }

    @Test
    void getGasForData() {
        // Test gas calculation
        assertEquals(GasCost.toGas(150), secp256k1Addition.getGasForData(new byte[0]));
        assertEquals(GasCost.toGas(150), secp256k1Addition.getGasForData(new byte[64]));
        assertEquals(GasCost.toGas(150), secp256k1Addition.getGasForData(new byte[128]));
    }

    @Test
    void executeOperation() {
        // Test the precompiled contract execution
        
        // Test empty input
        byte[] emptyResult = secp256k1Addition.executeOperation(new byte[0]);
        assertNotNull(emptyResult);
        assertEquals(64, emptyResult.length);
        
        // Test point at infinity
        byte[] infinityInput = new byte[128]; // All zeros represents point at infinity
        byte[] infinityResult = secp256k1Addition.executeOperation(infinityInput);
        assertNotNull(infinityResult);
        assertEquals(64, infinityResult.length);
        
        // Test invalid point
        byte[] invalidInput = new byte[128];
        invalidInput[0] = 1; // Make it an invalid point
        byte[] invalidResult = secp256k1Addition.executeOperation(invalidInput);
        assertNull(invalidResult); // Should return null for invalid points
    }
}