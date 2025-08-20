/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.core.RskAddress;
import co.rsk.rpc.modules.eth.AccountOverride;
import co.rsk.util.HexUtils;
import org.ethereum.TestUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.AccountOverrideParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.rpc.parameters.HexNumberParam;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccountOverrideTest {

    @Test
    void applyWithoutAddressThrowsException() {
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            new AccountOverride(null, 0, 0);
        });
        assertEquals(-32602, exception.getCode());
        assertEquals("Address cannot be null", exception.getMessage());
    }

    @Test
    void fromAccountOverrideParam_setMovePrecompileToAddress_throwsExceptionAsExpected() {
        // Given
        AccountOverride accountOverride = new AccountOverride(TestUtils.generateAddress("address"), 0, 0);
        HexAddressParam hexAddressParam = new HexAddressParam(TestUtils.generateAddress("aPrecompiledAddress").toString());
        AccountOverrideParam accountOverrideParam = new AccountOverrideParam(null, null, null, null, null, hexAddressParam);

        // When
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            accountOverride.fromAccountOverrideParam(accountOverrideParam);
        });

        // Then
        assertEquals(-32201, exception.getCode());
        assertEquals("Move precompile to address is not supported yet", exception.getMessage());
    }

    @Test
    void fromAccountOverrideParam_nullParameters_executesAsExpected() {
        // Given
        RskAddress address = TestUtils.generateAddress("address");
        AccountOverride accountOverride = new AccountOverride(address, 0, 0);
        AccountOverrideParam accountOverrideParam = new AccountOverrideParam(null, null, null, null, null, null);

        // When
        accountOverride = accountOverride.fromAccountOverrideParam(accountOverrideParam);

        // Then
        assertEquals(address, accountOverride.getAddress());
        assertNull(accountOverride.getBalance());
        assertNull(accountOverride.getNonce());
        assertNull(accountOverride.getCode());
        assertNull(accountOverride.getState());
        assertNull(accountOverride.getStateDiff());
    }

    @Test
    void fromAccountOverrideParam_validParameters_executesAsExpected() {
        // Given
        RskAddress address = TestUtils.generateAddress("address");
        AccountOverride accountOverride = new AccountOverride(address, 1, 1);

        HexNumberParam balance = new HexNumberParam("0x01");
        HexNumberParam nonce = new HexNumberParam("0x02");
        HexDataParam code = new HexDataParam("0x03");
        Map<HexDataParam, HexDataParam> state = new HashMap<>();
        state.put(new HexDataParam("0x00"), new HexDataParam("0x01"));

        AccountOverrideParam accountOverrideParam = new AccountOverrideParam(balance, nonce, code, state, null, null);

        // When
        accountOverride = accountOverride.fromAccountOverrideParam(accountOverrideParam);

        // Then
        assertEquals(address, accountOverride.getAddress());
        assertEquals(HexUtils.stringHexToBigInteger(balance.getHexNumber()), accountOverride.getBalance());
        assertEquals(HexUtils.jsonHexToLong(nonce.getHexNumber()), accountOverride.getNonce());
        assertEquals(code.getRawDataBytes(), accountOverride.getCode());
        assertNull(accountOverride.getStateDiff());
    }

    @Test
    void testSetBalance_balanceLessThanZero_throwsExceptionAsExpected() {
        // Given
        RskAddress address = TestUtils.generateAddress("address");
        AccountOverride accountOverride = new AccountOverride(address, 0, 0);

        BigInteger balance = BigInteger.valueOf(-1L);

        // When
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            accountOverride.setBalance(balance);
        });

        // Then
        assertEquals(-32602, exception.getCode());
        assertEquals("Balance must be equal or bigger than zero", exception.getMessage());
    }

    @Test
    void testSetNonce_nonceLessThanZero_throwsExceptionAsExpected() {
        // Given
        RskAddress address = TestUtils.generateAddress("address");
        AccountOverride accountOverride = new AccountOverride(address, 0, 0);

        Long nonce = -1L;

        // When
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            accountOverride.setNonce(nonce);
        });

        // Then
        assertEquals(-32602, exception.getCode());
        assertEquals("Nonce must be equal or bigger than zero", exception.getMessage());
    }

    @Test
    void testSetCode_codeSizeBiggerThanMax_throwsExceptionAsExpected() {
        // Given
        int maxOverridableCodeSize = 1;
        RskAddress address = TestUtils.generateAddress("address");
        AccountOverride accountOverride = new AccountOverride(address, maxOverridableCodeSize, 0);

        byte[] code = "01".getBytes(); // Two Bytes

        // When
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            accountOverride.setCode(code);
        });

        // Then
        assertEquals(-32602, exception.getCode());
        assertEquals("Code length in bytes exceeded. Max " + maxOverridableCodeSize, exception.getMessage());
    }

    @Test
    void testSetState_stateSizeBiggerThanMax_throwsExceptionAsExpected() {
        // Given
        int maxStateOverrideChanges = 1;
        RskAddress address = TestUtils.generateAddress("address");
        AccountOverride accountOverride = new AccountOverride(address, 0, maxStateOverrideChanges);

        Map<DataWord, DataWord> state = new HashMap<>();
        state.put(DataWord.valueOf(0), DataWord.ZERO);
        state.put(DataWord.valueOf(1), DataWord.ONE);

        // When
        RskJsonRpcRequestException exception = assertThrows(RskJsonRpcRequestException.class, () -> {
            accountOverride.setState(state);
        });

        // Then
        assertEquals(-32602, exception.getCode());
        assertEquals("Number of state changes exceeded. Max " + maxStateOverrideChanges, exception.getMessage());
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        RskAddress address= TestUtils.generateAddress("address");

        AccountOverride accountOverride = new AccountOverride(address, 1, 1);
        accountOverride.setBalance(BigInteger.TEN);
        accountOverride.setNonce(1L);
        accountOverride.setCode(new byte[]{1});
        accountOverride.setState(Map.of(DataWord.valueOf(1), DataWord.valueOf(2)));
        accountOverride.setStateDiff(Map.of(DataWord.valueOf(3), DataWord.valueOf(4)));

        AccountOverride otherAccountOverride = new AccountOverride(address, 1, 1);
        otherAccountOverride.setBalance(BigInteger.TEN);
        otherAccountOverride.setNonce(1L);
        otherAccountOverride.setCode(new byte[]{1});
        otherAccountOverride.setState(Map.of(DataWord.valueOf(1), DataWord.valueOf(2)));
        otherAccountOverride.setStateDiff(Map.of(DataWord.valueOf(3), DataWord.valueOf(4)));

        // Then
        assertEquals(accountOverride, otherAccountOverride);
        assertEquals(accountOverride.hashCode(), otherAccountOverride.hashCode());
    }

}
