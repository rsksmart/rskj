/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.rpc.converters;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import co.rsk.config.TestSystemProperties;
import co.rsk.util.HexUtils;

import java.util.stream.Stream;

/**
 * Created by martin.medina on 3/7/17.
 */
class CallArgumentsToByteArrayTest {

    private static final long CALL_GAS_CAP = 10_000_000L;
    private static final String CALL_GAS_CAP_AS_8_BYTES = "0000000000989680";

    private TestSystemProperties config = new TestSystemProperties();

    @Test
    void getGasPriceWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {0}, byteArrayArgs.getGasPrice());
    }

    @Test
    void getGasPriceWhenValueIsEmpty() throws Exception {
        CallArguments args = new CallArguments();
        args.setGasPrice("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {0}, byteArrayArgs.getGasPrice());
    }

    @Test
    void getGasLimitWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        String maxGasLimit = "0x5AF3107A4000";
        byte[] expectedGasLimit = HexUtils.stringHexToByteArray(maxGasLimit);
        Assertions.assertArrayEquals(expectedGasLimit, byteArrayArgs.getGasLimit());
    }

    @Test
    void getGasLimitWhenValueIsEmpty() throws Exception {
        CallArguments args = new CallArguments();
        args.setGas("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        String maxGasLimit = "0x5AF3107A4000";
        byte[] expectedGasLimit = HexUtils.stringHexToByteArray(maxGasLimit);
        Assertions.assertArrayEquals(expectedGasLimit, byteArrayArgs.getGasLimit());
    }

    @Test
    void getToAddressWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertNull(byteArrayArgs.getToAddress());
    }

    @Test
    void getValueWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {0}, byteArrayArgs.getValue());
    }

    @Test
    void getValueWhenValueIsEmpty() throws Exception {
        CallArguments args = new CallArguments();
        args.setValue("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertArrayEquals(new byte[] {0}, byteArrayArgs.getValue());
    }

    @Test
    void getDataWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertNull(byteArrayArgs.getData());
    }

    @Test
    void getDataWhenValueIsEmpty() throws Exception {
        CallArguments args = new CallArguments();
        args.setData("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assertions.assertNull(byteArrayArgs.getData());
    }

    @Test
    void gasLimitForGasExceedingGasCap() {
        long hugeAmountOfGas = 900000000000000L;
        long callGasCap = config.getCallGasCap();

        CallArguments callArguments = new CallArguments();
        callArguments.setGas(HexUtils.toQuantityJsonHex(hugeAmountOfGas));

        Assertions.assertEquals(hugeAmountOfGas, Long.decode(callArguments.getGas()).longValue());

        CallArgumentsToByteArray callArgumentsToByteArray = new CallArgumentsToByteArray(callArguments);

        Assertions.assertEquals(hugeAmountOfGas, ByteUtil.byteArrayToLong(callArgumentsToByteArray.getGasLimit()));
        Assertions.assertEquals(callGasCap, ByteUtil.byteArrayToLong(callArgumentsToByteArray.gasLimitForCall(callGasCap)));
    }

    @Test
    void gasLimitForGasBelowGasCap() {
        CallArguments callArguments = new CallArguments();
        callArguments.setGas(HexUtils.toQuantityJsonHex(1));

        CallArgumentsToByteArray callArgumentsToByteArray = new CallArgumentsToByteArray(callArguments);

        Assertions.assertEquals(1, ByteUtil.byteArrayToLong(
                callArgumentsToByteArray.gasLimitForCall(config.getCallGasCap())));
    }

    private static Stream<Arguments> gasAboveTheCallGasCap() {
        return Stream.of(
                Arguments.of("0x989681"),               // cap + 1
                Arguments.of("0x7fffffffffffffff"),     // 2^63 - 1
                Arguments.of("0x8000000000000000"),     // 2^63
                Arguments.of("0xffffffffffffffff"),     // 2^64 - 1
                Arguments.of("0x010000000000000000"),   // 2^64, nine bytes
                Arguments.of("0x" + "ff".repeat(32)),   // 2^256 - 1
                Arguments.of("-9223372036854775808")    // decimal, two's complement 0x8000000000000000
        );
    }

    @ParameterizedTest
    @MethodSource("gasAboveTheCallGasCap")
    void gasLimitForCall_gasAboveTheCap_returnsTheCap(String gas) {
        CallArguments callArguments = new CallArguments();
        callArguments.setGas(gas);

        byte[] gasLimit = new CallArgumentsToByteArray(callArguments).gasLimitForCall(CALL_GAS_CAP);

        Assertions.assertArrayEquals(Hex.decode(CALL_GAS_CAP_AS_8_BYTES), gasLimit);
    }

    private static Stream<Arguments> gasAtOrBelowTheCallGasCap() {
        return Stream.of(
                Arguments.of("0x989680", "989680"),                         // the cap itself
                Arguments.of("0x000000000000000001", "000000000000000001")  // 1, zero-padded to nine bytes
        );
    }

    @ParameterizedTest
    @MethodSource("gasAtOrBelowTheCallGasCap")
    void gasLimitForCall_gasAtOrBelowTheCap_isReturnedUnchanged(String gas, String expectedBytes) {
        CallArguments callArguments = new CallArguments();
        callArguments.setGas(gas);

        byte[] gasLimit = new CallArgumentsToByteArray(callArguments).gasLimitForCall(CALL_GAS_CAP);

        Assertions.assertArrayEquals(Hex.decode(expectedBytes), gasLimit);
    }

    private static Stream<Arguments> gasWithTheCapDisabled() {
        return Stream.of(
                Arguments.of("0xffffffffffffffff", "ffffffffffffffff"),
                Arguments.of("0x010000000000000000", "010000000000000000")
        );
    }

    @ParameterizedTest
    @MethodSource("gasWithTheCapDisabled")
    void gasLimitForCall_capDisabled_returnsTheGasUnchanged(String gas, String expectedBytes) {
        CallArguments callArguments = new CallArguments();
        callArguments.setGas(gas);

        byte[] gasLimit = new CallArgumentsToByteArray(callArguments).gasLimitForCall(0);

        Assertions.assertArrayEquals(Hex.decode(expectedBytes), gasLimit);
    }
}
