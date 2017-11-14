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

import org.ethereum.rpc.Web3;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;

/**
 * Created by martin.medina on 3/7/17.
 */
public class CallArgumentsToByteArray {

    private Web3.CallArguments args;

    public CallArgumentsToByteArray(Web3.CallArguments args) {
        this.args = args;
    }

    public byte[] getGasPrice() {
        byte[] gasPrice = new byte[] {0};
        if (args.gasPrice != null && args.gasPrice.length() != 0)
            gasPrice = stringHexToByteArray(args.gasPrice);

        return gasPrice;
    }

    public byte[] getGasLimit() {
        // maxGasLimit value is 100000000000000
        String maxGasLimit = "0x5AF3107A4000";
        byte[] gasLimit = stringHexToByteArray(maxGasLimit);
        if (args.gas != null && args.gas.length() != 0)
            gasLimit = stringHexToByteArray(args.gas);

        return gasLimit;
    }

    public byte[] getToAddress() {
        byte[] toAddress = null;
        if (args.to != null)
            toAddress = stringHexToByteArray(args.to);

        return toAddress;
    }

    public byte[] getValue() {
        byte[] value = new byte[] { 0 };
        if (args.value != null && args.value.length() != 0)
            value = stringHexToByteArray(args.value);

        return value;
    }

    public byte[] getData() {
        byte[] data = null;

        if (args.data != null && args.data.length() != 0)
            data = stringHexToByteArray(args.data);

        return data;
    }

    public byte[] getFromAddress() {
        byte[] fromAddress = null;
        if (args.from != null)
            fromAddress = stringHexToByteArray(args.from);

        return fromAddress;
    }
}
