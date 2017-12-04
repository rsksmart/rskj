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

package co.rsk.validators;

import org.ethereum.core.Block;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Created by mario on 23/01/17.
 */
public class BlockTimeStampValidationRuleTest {

    @Test
    public void blockInThePast() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod);

        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) - 1000);

        Assert.assertTrue(validationRule.isValid(block));
    }

    @Test
    public void blockInTheFutureLimit() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod);

        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) + validPeriod);

        Assert.assertTrue(validationRule.isValid(block));
    }

    @Test
    public void blockInTheFuture() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod);

        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) + 2*validPeriod);

        Assert.assertFalse(validationRule.isValid(block));
    }

    @Test
    public void blockTimeLowerThanParentTime() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod);

        Block block = Mockito.mock(Block.class);
        Block parent = Mockito.mock(Block.class);

        Mockito.when(block.getTimestamp())
                .thenReturn(System.currentTimeMillis() / 1000);

        Mockito.when(parent.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) + 1000);

        Assert.assertFalse(validationRule.isValid(block, parent));
    }

    @Test
    public void blockTimeGreaterThanParentTime() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod);

        Block block = Mockito.mock(Block.class);
        Block parent = Mockito.mock(Block.class);

        Mockito.when(block.getTimestamp())
                .thenReturn(System.currentTimeMillis() / 1000);

        Mockito.when(parent.getTimestamp())
                .thenReturn((System.currentTimeMillis() / 1000) - 1000);

        Assert.assertTrue(validationRule.isValid(block, parent));
    }

    @Test
    public void blockTimeEqualsParentTime() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod);

        Block block = Mockito.mock(Block.class);
        Block parent = Mockito.mock(Block.class);

        Mockito.when(block.getTimestamp())
                .thenReturn(System.currentTimeMillis() / 1000);

        Mockito.when(parent.getTimestamp())
                .thenReturn(System.currentTimeMillis() / 1000);

        Assert.assertFalse(validationRule.isValid(block, parent));
    }
}
