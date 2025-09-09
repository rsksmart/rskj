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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.ethereum.TestUtils;
import org.ethereum.crypto.signature.Secp256k1Service;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class Secp256k1MultiplicationTest {
    private Secp256k1Multiplication secp256k1Multiplication;
    private Secp256k1Service secp256k1Service;

    @BeforeEach
    void setUp() {
        secp256k1Service = mock(Secp256k1Service.class);
        secp256k1Multiplication = new Secp256k1Multiplication(secp256k1Service);
    }

    @Test
    void whenExecuteOperationIsCalled_thenDelegatesCorrectlyToService() throws VMException {
        // given
        byte[] inputData = TestUtils.generateBytes("secp256k1MultiplicationInput", 96);
        byte[] expectedOutput = TestUtils.generateBytes("secp256k1MultiplicationOutput", 64);
        when(secp256k1Service.mul(inputData)).thenReturn(expectedOutput);

        // when
        byte[] result = secp256k1Multiplication.executeOperation(inputData);

        // then
        assertArrayEquals(expectedOutput, result);
        ArgumentCaptor<byte[]> inputCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(secp256k1Service).mul(inputCaptor.capture());
        assertArrayEquals(inputData, inputCaptor.getValue());
    }

    @Test
    void givenDifferentDataLengths_whenGetGasForDataIsCalled_thenGasCostIsCalculatedCorrectly() {
        // given
        byte[] dataInput1 = new byte[0];
        byte[] dataInput2 = TestUtils.generateBytes("secp256k1MultiplicationRandomInput", 64);
        byte[] dataInput3 = TestUtils.generateBytes("secp256k1MultiplicationRandomInput", 96);
        long expectedGasCost = 3000;

        // when the getGasForData method is called with different data lengths
        long gasCost1 = secp256k1Multiplication.getGasForData(dataInput1);
        long gasCost2 = secp256k1Multiplication.getGasForData(dataInput2);
        long gasCost3 = secp256k1Multiplication.getGasForData(dataInput3);

        // then the gas cost should be calculated correctly
        assertEquals(expectedGasCost, gasCost1);
        assertEquals(expectedGasCost, gasCost2);
        assertEquals(expectedGasCost, gasCost3);
    }

    @Test
    void testMaxInputReturnsCorrectValue() {
        // Secp256k1 multiplication expects exactly 3 words: x, y, scalar
        // Each word is 32 bytes, so total expected input is 96 bytes
        assertEquals(96, secp256k1Multiplication.getMaxInput());
    }
}