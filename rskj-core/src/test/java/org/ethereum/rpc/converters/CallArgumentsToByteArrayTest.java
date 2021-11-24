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

import co.rsk.config.TestSystemProperties;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * Created by martin.medina on 3/7/17.
 */
public class CallArgumentsToByteArrayTest {

    private TestSystemProperties config = new TestSystemProperties();

    @Test
    public void getGasPriceWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertArrayEquals(new byte[] {0}, byteArrayArgs.getGasPrice());
    }

    @Test
    public void getGasPriceWhenValueIsEmpty() throws Exception {
        CallArguments args = new CallArguments();
        args.setGasPrice("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertArrayEquals(new byte[] {0}, byteArrayArgs.getGasPrice());
    }

    @Test
    public void getGasLimitWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        String maxGasLimit = "0x5AF3107A4000";
        byte[] expectedGasLimit = TypeConverter.stringHexToByteArray(maxGasLimit);
        Assert.assertArrayEquals(expectedGasLimit, byteArrayArgs.getGasLimit());
    }

    @Test
    public void getGasLimitWhenValueIsEmpty() throws Exception {
        CallArguments args = new CallArguments();
        args.setGas("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        String maxGasLimit = "0x5AF3107A4000";
        byte[] expectedGasLimit = TypeConverter.stringHexToByteArray(maxGasLimit);
        Assert.assertArrayEquals(expectedGasLimit, byteArrayArgs.getGasLimit());
    }

    @Test
    public void getToAddressWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertNull(byteArrayArgs.getToAddress());
    }

    @Test
    public void getValueWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertArrayEquals(new byte[] {0}, byteArrayArgs.getValue());
    }

    @Test
    public void getValueWhenValueIsEmpty() throws Exception {
        CallArguments args = new CallArguments();
        args.setValue("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertArrayEquals(new byte[] {0}, byteArrayArgs.getValue());
    }

    @Test
    public void getDataWhenValueIsNull() throws Exception {
        CallArguments args = new CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertNull(byteArrayArgs.getData());
    }

    @Test
    public void getDataWhenValueIsEmpty() throws Exception {
        CallArguments args = new CallArguments();
        args.setData("");

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertNull(byteArrayArgs.getData());
    }

    @Test
    public void gasLimitForGasEstimationExceedingGasCap() {
        long hugeAmountOfGas = 900000000000000l;
        long gasEstimationCap = config.getGasEstimationCap();

        CallArguments callArguments = new CallArguments();
        callArguments.setGas(TypeConverter.toQuantityJsonHex(hugeAmountOfGas));

        Assert.assertEquals(hugeAmountOfGas, Long.decode(callArguments.getGas()).longValue());

        CallArgumentsToByteArray callArgumentsToByteArray = new CallArgumentsToByteArray(callArguments);

        Assert.assertEquals(hugeAmountOfGas, ByteUtil.byteArrayToLong(callArgumentsToByteArray.getGasLimit()));
        Assert.assertEquals(gasEstimationCap, ByteUtil.byteArrayToLong(
                callArgumentsToByteArray.gasLimitForGasEstimation(gasEstimationCap)));
    }

    @Test
    public void gasLimitForGasEstimationBelowGasCap() {
        CallArguments callArguments = new CallArguments();
        callArguments.setGas(TypeConverter.toQuantityJsonHex(1));

        CallArgumentsToByteArray callArgumentsToByteArray = new CallArgumentsToByteArray(callArguments);

        Assert.assertEquals(1, ByteUtil.byteArrayToLong(
                callArgumentsToByteArray.gasLimitForGasEstimation(config.getGasEstimationCap())));
    }
}
