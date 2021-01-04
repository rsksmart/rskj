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

import java.util.Optional;

/**
 * Helper class to provide Block access to the BlockHeaderContract native methods.
 *
 * @author Diego Masini
 */
public class BlockAccessor {
    private final short maximumBlockDepth;

    public BlockAccessor(short maximumBlockDepth) {
        this.maximumBlockDepth = maximumBlockDepth;
    }

    public Optional<Block> getBlock(short blockDepth, ExecutionEnvironment environment) throws NativeContractIllegalArgumentException {
        if (blockDepth < 0) {
            throw new NativeContractIllegalArgumentException(String.format(
                    "Invalid block depth '%d' (should be a non-negative value)",
                    blockDepth
            ));
        }

        // If blockDepth is bigger or equal to the max depth, return an empty optional instead of throwing an exception
        // to allow users of this method to decide if they want to throw an exception or return a default
        // (probably empty) value (e.g. an empty byte array).
        if (blockDepth >= maximumBlockDepth) {
            return Optional.empty();
        }

        // If block is null, return an empty optional instead of throwing an exception to allow users of this method to
        // decide if they want to throw an exception or return a default (probably empty) value (e.g. an empty byte array).
        Block block = environment.getBlockStore().getBlockAtDepthStartingAt(blockDepth, environment.getBlock().getParentHash().getBytes());
        if (block == null) {
            return Optional.empty();
        }

        return Optional.of(block);
    }
}
