/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

package co.rsk.core.bc;

import org.ethereum.core.Block;

public class BlockBundle<T> {

    private final Block block;
    private final T bundle;

    public static <T> BlockBundle<T> of(Block block, T bundle) {
        return new BlockBundle<>(block, bundle);
    }

    public BlockBundle(Block block, T bundle) {
        this.block = block;
        this.bundle = bundle;
    }

    public Block getBlock() {
        return block;
    }

    public T getBundle() {
        return bundle;
    }
}
