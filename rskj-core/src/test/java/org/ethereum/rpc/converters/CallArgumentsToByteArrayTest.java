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

import org.ethereum.rpc.CallArguments;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import co.rsk.config.TestSystemProperties;
import co.rsk.util.HexUtils;

/**
 * Created by martin.medina on 3/7/17.
 */
class CallArgumentsToByteArrayTest {

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
    void gasLimitForGasEstimationExceedingGasCap() {
        long hugeAmountOfGas = 900000000000000l;
        long gasEstimationCap = config.getGasEstimationCap();

        CallArguments callArguments = new CallArguments();
        callArguments.setGas(HexUtils.toQuantityJsonHex(hugeAmountOfGas));

        Assertions.assertEquals(hugeAmountOfGas, Long.decode(callArguments.getGas()).longValue());

        CallArgumentsToByteArray callArgumentsToByteArray = new CallArgumentsToByteArray(callArguments);

        Assertions.assertEquals(hugeAmountOfGas, ByteUtil.byteArrayToLong(callArgumentsToByteArray.getGasLimit()));
        Assertions.assertEquals(gasEstimationCap, ByteUtil.byteArrayToLong(
                callArgumentsToByteArray.gasLimitForGasEstimation(gasEstimationCap)));
    }

    @Test
    void gasLimitForGasEstimationBelowGasCap() {
        CallArguments callArguments = new CallArguments();
        callArguments.setGas(HexUtils.toQuantityJsonHex(1));

        CallArgumentsToByteArray callArgumentsToByteArray = new CallArgumentsToByteArray(callArguments);

        Assertions.assertEquals(1, ByteUtil.byteArrayToLong(
                callArgumentsToByteArray.gasLimitForGasEstimation(config.getGasEstimationCap())));
    }

    @Test
    void gasLimitForGasExceedingGasCap() {
        long hugeAmountOfGas = 900000000000000l;
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
}
