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

package org.ethereum.vm;

import org.ethereum.core.CallTransaction;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Diego Masini
 * 
 * This enum holds the basic information of the BlockHeader contract methods: the ABI, the cost and the implementation.
 */
public enum BlockHeaderContractMethod {
    GET_COINBASE(
            CallTransaction.Function.fromSignature(
                    "getCoinbaseAddress",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            500L,
            (BlockHeaderMethodExecutorTyped) BlockHeaderContract::getCoinbaseAddress
    ),
    GET_BLOCK_HASH(
            CallTransaction.Function.fromSignature(
                    "getBlockHash",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            500L,
            (BlockHeaderMethodExecutorTyped) BlockHeaderContract::getBlockHash
    ),
    GET_MIN_GAS_PRICE(
            CallTransaction.Function.fromSignature(
                    "getMinGasPrice",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            500L,
            (BlockHeaderMethodExecutorTyped) BlockHeaderContract::getMinGasPrice
    ),
    GET_MERGED_MINING_TAGS(
            CallTransaction.Function.fromSignature(
                    "getMergedMiningTags",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            500L,
            (BlockHeaderMethodExecutorTyped) BlockHeaderContract::getMergedMiningTags
    ),
    GET_GAS_LIMIT(
            CallTransaction.Function.fromSignature(
                    "getGasLimit",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            500L,
            (BlockHeaderMethodExecutorTyped) BlockHeaderContract::getGasLimit
    ),
    GET_GAS_USED(
            CallTransaction.Function.fromSignature(
                    "getGasUsed",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            500L,
            (BlockHeaderMethodExecutorTyped) BlockHeaderContract::getGasUsed
    ),
    GET_RSK_DIFFICULTY(
            CallTransaction.Function.fromSignature(
                    "getRSKDifficulty",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            500L,
            (BlockHeaderMethodExecutorTyped) BlockHeaderContract::getRSKDifficulty
    ),
    GET_BITCOIN_HEADER(
            CallTransaction.Function.fromSignature(
                    "getBitcoinHeader",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            500L,
            (BlockHeaderMethodExecutorTyped) BlockHeaderContract::getBitcoinHeader
    );

    private static final Map<ByteArrayWrapper, BlockHeaderContractMethod> SIGNATURES = Stream.of(BlockHeaderContractMethod.values())
            .collect(Collectors.toMap(
                    m -> new ByteArrayWrapper(m.getFunction().encodeSignature()),
                    Function.identity()
            ));

    private final CallTransaction.Function function;
    private final long cost;
    private final BlockHeaderMethodExecutor executor;

    BlockHeaderContractMethod(CallTransaction.Function function, long cost, BlockHeaderMethodExecutor executor) {
        this.function = function;
        this.cost = cost;
        this.executor = executor;
    }

    public static Optional<BlockHeaderContractMethod> findBySignature(byte[] encoding) {
        return Optional.ofNullable(SIGNATURES.get(new ByteArrayWrapper(encoding)));
    }

    public CallTransaction.Function getFunction() {
        return function;
    }

    public long getCost() {
        return cost;
    }

    public BlockHeaderMethodExecutor getExecutor() {
        return executor;
    }

    public interface BlockHeaderMethodExecutor {
        Optional<?> execute(BlockHeaderContract self, Object[] args) throws Exception;
    }

    private interface BlockHeaderMethodExecutorTyped<T> extends BlockHeaderMethodExecutor {
        @Override
        default Optional<T> execute(BlockHeaderContract self, Object[] args) throws Exception {
            return Optional.ofNullable(executeTyped(self, args));
        }

        T executeTyped(BlockHeaderContract self, Object[] args) throws Exception;
    }
}
