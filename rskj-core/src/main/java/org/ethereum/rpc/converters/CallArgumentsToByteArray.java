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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;

/**
 * Created by martin.medina on 3/7/17.
 */
public class CallArgumentsToByteArray {
    private static final Logger LOGGER = LoggerFactory.getLogger(CallArgumentsToByteArray.class); //NOSONAR

    private final CallArguments args;

    public CallArgumentsToByteArray(CallArguments args) {
        this.args = args;
    }

    public byte[] getGasPrice() {
        byte[] gasPrice = new byte[] {0};
        if (args.getGasPrice() != null && args.getGasPrice().length() != 0) {
            gasPrice = HexUtils.strHexOrStrNumberToByteArray(args.getGasPrice());
        }

        return gasPrice;
    }

    public byte[] getGasLimit() {
        // maxGasLimit value is 100000000000000
        String maxGasLimit = "0x5AF3107A4000";
        byte[] gasLimit = HexUtils.strHexOrStrNumberToByteArray(maxGasLimit);
        if (args.getGas() != null && args.getGas().length() != 0) {
            gasLimit = HexUtils.strHexOrStrNumberToByteArray(args.getGas());
        }

        return gasLimit;
    }

    public byte[] getToAddress() {
        byte[] toAddress = null;
        if (args.getTo() != null) {
            toAddress = HexUtils.strHexOrStrNumberToByteArray(args.getTo());
        }

        return toAddress;
    }

    public byte[] getValue() {
        byte[] value = new byte[] { 0 };
        if (args.getValue() != null && args.getValue().length() != 0) {
            value = HexUtils.strHexOrStrNumberToByteArray(args.getValue());
        }

        return value;
    }

    public byte[] getData() {
        byte[] data = null;

        if (args.getData() != null && args.getData().length() != 0) {
            data = HexUtils.strHexOrStrNumberToByteArray(args.getData());
        }

        return data;
    }

    public RskAddress getFromAddress() {
        if (args.getFrom() == null || args.getFrom().isEmpty()) {

            return RskAddress.nullAddress();
        }

        return new RskAddress(HexUtils.strHexOrStrNumberToByteArray(args.getFrom()));
    }

    public byte[] gasLimitForGasEstimation(long gasCap) {
        long gasLimit = ByteUtil.byteArrayToLong(this.getGasLimit());

        if(gasLimit > gasCap) {
            LOGGER.warn("provided gasLimit ({}) exceeds the estimation cap," +
                    " using the estimation cap ({})", gasLimit, gasCap);
            return ByteUtil.longToBytes(gasCap);
        }

        return this.getGasLimit();
    }

    public byte[] gasLimitForCall(long gasCap) {
        long gasLimit = ByteUtil.byteArrayToLong(this.getGasLimit());

        if(gasLimit > gasCap) {
            LOGGER.warn("provided gasLimit ({}) exceeds the gas cap," +
                    " using the gas cap ({})", gasLimit, gasCap);
            return ByteUtil.longToBytes(gasCap);
        }

        return this.getGasLimit();
    }
}
