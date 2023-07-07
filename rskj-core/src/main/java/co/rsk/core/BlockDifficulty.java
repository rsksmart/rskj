/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.core;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * A block's difficulty, stored internally as a {@link java.math.BigInteger}.
 */
public class BlockDifficulty implements Comparable<BlockDifficulty>, Serializable {
    public static final BlockDifficulty ZERO = new BlockDifficulty(BigInteger.ZERO);
    public static final BlockDifficulty ONE = new BlockDifficulty(BigInteger.ONE);
    private static final long serialVersionUID = -2892109523890881103L;

    private final BigInteger value;

    /**
     * @param value the difficulty value, which should be positive.
     */
    public BlockDifficulty(BigInteger value) {
        if (value.signum() < 0) {
            throw new RuntimeException("A block difficulty must be positive or zero");
        }

        this.value = value;
    }

    public byte[] getBytes() {
        return value.toByteArray();
    }

    public BigInteger asBigInteger() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        BlockDifficulty otherDifficulty = (BlockDifficulty) other;
        return value.equals(otherDifficulty.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * @return a DEBUG representation of the difficulty, mainly used for logging.
     */
    @Override
    public String toString() {
        return value.toString();
    }

    public BlockDifficulty add(BlockDifficulty other) {
        return new BlockDifficulty(value.add(other.value));
    }

    public BlockDifficulty subtract(BlockDifficulty other) {
        return new BlockDifficulty(value.subtract(other.value));
    }


    @Override
    public int compareTo(@Nonnull BlockDifficulty other) {
        return value.compareTo(other.value);
    }
}
