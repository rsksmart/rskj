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

import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by martin.medina on 3/7/17.
 */
public class CallArgumentsToByteArrayTest {

    @Test
    public void getGasPriceWhenValueIsNull() throws Exception {
        Web3.CallArguments args = new Web3.CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertArrayEquals(new byte[] {0}, byteArrayArgs.getGasPrice());
    }

    @Test
    public void getGasPriceWhenValueIsEmpty() throws Exception {
        Web3.CallArguments args = new Web3.CallArguments();
        args.gasPrice = "";

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertArrayEquals(new byte[] {0}, byteArrayArgs.getGasPrice());
    }

    @Test
    public void getGasLimitWhenValueIsNull() throws Exception {
        Web3.CallArguments args = new Web3.CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        String maxGasLimit = "0x5AF3107A4000";
        byte[] expectedGasLimit = TypeConverter.stringHexToByteArray(maxGasLimit);
        Assert.assertArrayEquals(expectedGasLimit, byteArrayArgs.getGasLimit());
    }

    @Test
    public void getGasLimitWhenValueIsEmpty() throws Exception {
        Web3.CallArguments args = new Web3.CallArguments();
        args.gas = "";

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        String maxGasLimit = "0x5AF3107A4000";
        byte[] expectedGasLimit = TypeConverter.stringHexToByteArray(maxGasLimit);
        Assert.assertArrayEquals(expectedGasLimit, byteArrayArgs.getGasLimit());
    }

    @Test
    public void getToAddressWhenValueIsNull() throws Exception {
        Web3.CallArguments args = new Web3.CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertNull(byteArrayArgs.getToAddress());
    }

    @Test
    public void getValueWhenValueIsNull() throws Exception {
        Web3.CallArguments args = new Web3.CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertArrayEquals(new byte[] {0}, byteArrayArgs.getValue());
    }

    @Test
    public void getValueWhenValueIsEmpty() throws Exception {
        Web3.CallArguments args = new Web3.CallArguments();
        args.value = "";

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertArrayEquals(new byte[] {0}, byteArrayArgs.getValue());
    }

    @Test
    public void getDataWhenValueIsNull() throws Exception {
        Web3.CallArguments args = new Web3.CallArguments();

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertNull(byteArrayArgs.getData());
    }

    @Test
    public void getDataWhenValueIsEmpty() throws Exception {
        Web3.CallArguments args = new Web3.CallArguments();
        args.data = "";

        CallArgumentsToByteArray byteArrayArgs = new CallArgumentsToByteArray(args);

        Assert.assertNull(byteArrayArgs.getData());
    }
}
