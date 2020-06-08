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

public class SuicideInvoke implements InvokeData {
    private final DataWord ownerAddress;
    private final DataWord callerAddress;
    private final DataWord balance;

    public SuicideInvoke(DataWord callerAddress, DataWord ownerAddress, DataWord balance) {
        this.callerAddress = callerAddress;
        this.ownerAddress = ownerAddress;
        this.balance = balance;
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
        return 0L;
    }

    @Override
    public DataWord getCallValue() {
        return this.balance;
    }

    @Override
    public DataWord getDataSize() {
        return DataWord.ZERO;
    }

    @Override
    public DataWord getDataValue(DataWord indexData) {
        return DataWord.ZERO;
    }

    @Override
    public byte[] getDataCopy(DataWord offsetData, DataWord lengthData) {
        return ByteUtil.EMPTY_BYTE_ARRAY;
    }
}
