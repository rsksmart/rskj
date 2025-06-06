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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.ethereum.TestUtils;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.crypto.signature.Secp256k1AdditionTestHelper;
import org.ethereum.crypto.signature.Secp256k1Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class Secp256k1AdditionTest {
    private Secp256k1Addition secp256k1Addition;
    private Secp256k1Service secp256k1Service;

    @BeforeEach
    void setUp() {
        secp256k1Service = Secp256k1.getInstance();
        secp256k1Addition = new Secp256k1Addition(secp256k1Service);
    }

    /* Call the testAddition method from the Secp256k1AdditionTestHelper class, which will use  
       the implementation of the secp256k1Service to test the addition operations.
       Doesn't make sense to add specific tests for the addition operations, since the testAddition
       method already covers all the cases and the Secp256k1Addition uses the secp256k1Service implementation and
       initialized to perform the addition operations.
    */
    @Test
    void testAdditionEC256Operations() {
        Secp256k1AdditionTestHelper.executeAllAdditionTests(secp256k1Service);
    }

    @Test
    void givenDifferentDataLengths_whenGetGasForDataIsCalled_thenGasCostIsCalculatedCorrectly() {
        //given 
        byte[] dataInput1 = new byte[0];
        byte[] dataInput2 = TestUtils.generateBytes("secp256k1AdditionRandomInput", 64);
        byte[] dataInput3 = TestUtils.generateBytes("secp256k1AdditionRandomInput", 128);
        long expectedGasCost = 150;

        //when the getGasForData method is called with different data lengths
        long gasCost1 = secp256k1Addition.getGasForData(dataInput1);
        long gasCost2 = secp256k1Addition.getGasForData(dataInput2);
        long gasCost3 = secp256k1Addition.getGasForData(dataInput3);

        //then the gas cost should be calculated correctly
        assertEquals(expectedGasCost, gasCost1);
        assertEquals(expectedGasCost, gasCost2);
        assertEquals(expectedGasCost, gasCost3);
    }

    @Test
    void givenEmptyInput_whenExecuteOperationIsCalled_thenResultIsExpected() {
        
        //given
        byte[] emptyInput = new byte[0];
        //when 
        byte[] result = secp256k1Addition.executeOperation(emptyInput);
        //then 
        assertNotNull(result);
        assertEquals(64, result.length);
    }

    @Test
    void givenInvalidInput_whenExecuteOperationIsCalled_thenResultIsNull() {
        //given
        byte[] invalidInput = new byte[128];
        invalidInput[0] = 1;  // Make it an invalid point
        //when
        byte[] result = secp256k1Addition.executeOperation(invalidInput);
        //then
        assertNull(result);
    }

    @Test
    void givenInfinityInput_whenExecuteOperationIsCalled_thenResultIsExpected() {
        // given
        byte[] infinityInput = new byte[128];

        //when
        byte[] result = secp256k1Addition.executeOperation(infinityInput);
        
        //then
        assertNotNull(result);
        assertEquals(64, result.length);
    }
    
}