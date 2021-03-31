/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.pcc.blockheader;

import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;
import java.util.List;

/**
 * This implements the "getUncleCoinbaseAddress" method
 * that belongs to the BlockHeaderContract native contract.
 *
 * @author Diego Masini
 */
public class GetUncleCoinbaseAddress extends BlockHeaderContractMethod {
    private final CallTransaction.Function function = CallTransaction.Function.fromSignature(
            "getUncleCoinbaseAddress",
            new String[]{"int256", "int256"},
            new String[]{"bytes"}
    );

    public GetUncleCoinbaseAddress(ExecutionEnvironment executionEnvironment, BlockAccessor blockAccessor) {
        super(executionEnvironment, blockAccessor);
    }

    @Override
    public CallTransaction.Function getFunction() {
        return function;
    }

    @Override
    protected Object internalExecute(Block block, Object[] arguments) throws NativeContractIllegalArgumentException {
        List<BlockHeader> uncles = block.getUncleList();
        if (uncles == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        short uncleIndex;
        try {
            uncleIndex = ((BigInteger) arguments[1]).shortValueExact();
        } catch (ArithmeticException e) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        if (uncleIndex < 0) {
            throw new NativeContractIllegalArgumentException(String.format(
                    "Invalid uncle index '%d' (should be a non-negative value)",
                    uncleIndex
            ));
        }

        if (uncleIndex >= uncles.size()) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        BlockHeader uncle = uncles.get(uncleIndex);
        return uncle.getCoinbase().getBytes();
    }
}

