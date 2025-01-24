/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.vm.program.invoke;

import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;

public class TransferInvoke implements InvokeData {
    private static BigInteger maxMsgData = BigInteger.valueOf(Integer.MAX_VALUE);

    private final DataWord ownerAddress;
    private final DataWord callerAddress;
    private final long gas;
    private final DataWord callValue;
    private final byte[] msgData;

    public TransferInvoke(DataWord callerAddress, DataWord ownerAddress, long gas, DataWord callValue) {
        this(callerAddress, ownerAddress, gas, callValue, ByteUtil.EMPTY_BYTE_ARRAY);
    }

    public TransferInvoke(DataWord callerAddress, DataWord ownerAddress, long gas, DataWord callValue, byte[] msgData) {
        this.callerAddress = callerAddress;
        this.ownerAddress = ownerAddress;
        this.gas = gas;
        this.callValue = callValue;
        this.msgData = msgData;
    }

    @Override
    public DataWord getOwnerAddress() {
        return this.ownerAddress;
    }

    @Override
    public DataWord getCallerAddress() {
        return this.callerAddress;
    }

    @Override
    public long getGas() {
        return this.gas;
    }

    @Override
    public DataWord getCallValue() {
        return this.callValue;
    }

    @Override
    public DataWord getDataSize() {
        if (msgData == null || msgData.length == 0) {
            return DataWord.ZERO;
        }
        int size = msgData.length;
        return DataWord.valueOf(size);
    }

    @Override
    public DataWord getDataValue(DataWord indexData) {
        if (msgData == null || msgData.length == 0) {
            return DataWord.ZERO;
        }
        BigInteger tempIndex = indexData.value();
        int index = tempIndex.intValue(); // possible overflow is caught below
        int size = 32; // maximum datavalue size

        if (index >= msgData.length
                || tempIndex.compareTo(maxMsgData) > 0) {
            return DataWord.ZERO;
        }
        if (index + size > msgData.length) {
            size = msgData.length - index;
        }

        byte[] data = new byte[32];
        System.arraycopy(msgData, index, data, 0, size);
        return DataWord.valueOf(data);
    }

    @Override
    public byte[] getDataCopy(DataWord offsetData, DataWord lengthData) {
        int offset = offsetData.intValueSafe();
        int length = lengthData.intValueSafe();

        byte[] data = new byte[length];

        if (msgData == null) {
            return data;
        }

        if (offset > msgData.length) {
            return data;
        }
        if (offset + length > msgData.length) {
            length = msgData.length - offset;
        }

        System.arraycopy(msgData, offset, data, 0, length);

        return data;
    }
}
