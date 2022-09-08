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

import org.ethereum.util.RLP;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BlockDifficultyTest {
    @Test
    public void bytesZeroDifficulty() {
        BlockDifficulty difficulty = RLP.parseBlockDifficulty(BigInteger.ZERO.toByteArray());
        assertThat(difficulty.asBigInteger(), is(BigInteger.ZERO));
        assertThat(difficulty, is(new BlockDifficulty(BigInteger.ZERO)));
        assertThat(difficulty.toString(), is(BigInteger.ZERO.toString()));
    }

    @Test
    public void bytesOneDifficulty() {
        BlockDifficulty difficulty = RLP.parseBlockDifficulty(BigInteger.ONE.toByteArray());
        assertThat(difficulty.asBigInteger(), is(BigInteger.ONE));
        assertThat(difficulty, is(new BlockDifficulty(BigInteger.ONE)));
        assertThat(difficulty.toString(), is(BigInteger.ONE.toString()));
    }

    @Test
    public void bytesLargeDifficulty() {
        BigInteger largeValue = BigInteger.valueOf(1532098739382974L);
        BlockDifficulty difficulty = RLP.parseBlockDifficulty(largeValue.toByteArray());
        assertThat(difficulty.asBigInteger(), is(largeValue));
        assertThat(difficulty, is(new BlockDifficulty(largeValue)));
        assertThat(difficulty.toString(), is(largeValue.toString()));
    }

    @Test
    public void bytesNullIsNull() {
        BlockDifficulty difficulty = RLP.parseBlockDifficulty(null);
        assertThat(difficulty, nullValue());
    }

    @Test
    public void bytesNegativeDifficultyFails() {
        BigInteger negativeValue = BigInteger.valueOf(-1532098739382974L);
        Assertions.assertThrows(RuntimeException.class, () -> RLP.parseBlockDifficulty(negativeValue.toByteArray()));
    }

    @Test
    public void addOneToZero() {
        BlockDifficulty d1 = new BlockDifficulty(BigInteger.ZERO);
        BlockDifficulty d2 = new BlockDifficulty(BigInteger.ONE);
        assertThat(d1.add(d2), is(d2));
    }

    @Test
    public void addOneToOne() {
        BlockDifficulty d1 = new BlockDifficulty(BigInteger.ONE);
        BlockDifficulty d2 = new BlockDifficulty(BigInteger.ONE);
        assertThat(d1.add(d2), is(new BlockDifficulty(BigInteger.valueOf(2))));
    }

    @Test
    public void addLargeValues() {
        BlockDifficulty d1 = new BlockDifficulty(BigInteger.valueOf(Long.MAX_VALUE));
        BlockDifficulty d2 = new BlockDifficulty(BigInteger.valueOf(Integer.MAX_VALUE));
        assertThat(d1.add(d2), is(new BlockDifficulty(new BigInteger("9223372039002259454"))));
    }

    @Test
    public void testComparable() {
        BlockDifficulty zero = BlockDifficulty.ZERO;
        BlockDifficulty d1 = new BlockDifficulty(BigInteger.valueOf(Long.MAX_VALUE));
        BlockDifficulty d2 = new BlockDifficulty(BigInteger.valueOf(Integer.MAX_VALUE));
        assertThat(zero.compareTo(zero), is(0));
        assertThat(d1.compareTo(d1), is(0));
        assertThat(d2.compareTo(d2), is(0));
        assertThat(zero.compareTo(d1), is(-1));
        assertThat(zero.compareTo(d2), is(-1));
        assertThat(d1.compareTo(zero), is(1));
        assertThat(d2.compareTo(zero), is(1));
        assertThat(d1.compareTo(d2), is(1));
        assertThat(d2.compareTo(d1), is(-1));
    }
}
