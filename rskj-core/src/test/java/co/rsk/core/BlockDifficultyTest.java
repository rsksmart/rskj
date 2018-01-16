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

import org.junit.Test;

import java.math.BigInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class BlockDifficultyTest {
    @Test
    public void bytesZeroDifficulty() {
        BlockDifficulty difficulty = new BlockDifficulty(BigInteger.ZERO.toByteArray());
        assertThat(difficulty.asBigInteger(), is(BigInteger.ZERO));
        assertThat(difficulty, is(new BlockDifficulty(BigInteger.ZERO)));
        assertThat(difficulty.toString(), is(BigInteger.ZERO.toString()));
    }

    @Test
    public void bytesOneDifficulty() {
        BlockDifficulty difficulty = new BlockDifficulty(BigInteger.ONE.toByteArray());
        assertThat(difficulty.asBigInteger(), is(BigInteger.ONE));
        assertThat(difficulty, is(new BlockDifficulty(BigInteger.ONE)));
        assertThat(difficulty.toString(), is(BigInteger.ONE.toString()));
    }

    @Test
    public void bytesLargeDifficulty() {
        BigInteger largeValue = BigInteger.valueOf(1532098739382974L);
        BlockDifficulty difficulty = new BlockDifficulty(largeValue.toByteArray());
        assertThat(difficulty.asBigInteger(), is(largeValue));
        assertThat(difficulty, is(new BlockDifficulty(largeValue)));
        assertThat(difficulty.toString(), is(largeValue.toString()));
    }

    @Test(expected = NumberFormatException.class)
    public void bytesEmptyDifficultyFails() {
        new BlockDifficulty(new byte[0]);
    }

    @Test(expected = RuntimeException.class)
    public void bytesNegativeDifficultyFails() {
        BigInteger negativeValue = BigInteger.valueOf(-1532098739382974L);
        new BlockDifficulty(negativeValue.toByteArray());
    }
}