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
import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author martin.medina
 * @since 07.02.2017
 */
public class ExtraDataRuleTests {

    @Test
    public void blockWithValidExtraData() {

        BlockHeader blockHeader = Mockito.mock(BlockHeader.class);
        Mockito.when(blockHeader.getExtraData()).thenReturn(new byte[32]);

        Block block = new Block(blockHeader);

        ExtraDataRule rule = new ExtraDataRule(42);

        Assert.assertTrue(rule.isValid(block));
    }

    @Test
    public void blockWithValidNullExtraData() {

        BlockHeader blockHeader = Mockito.mock(BlockHeader.class);
        Mockito.when(blockHeader.getExtraData()).thenReturn(null);

        Block block = new Block(blockHeader);

        ExtraDataRule rule = new ExtraDataRule(42);

        Assert.assertTrue(rule.isValid(block));
    }

    @Test
    public void blockWithValidLongerExtraDataThanAccepted() {

        BlockHeader blockHeader = Mockito.mock(BlockHeader.class);
        Mockito.when(blockHeader.getExtraData()).thenReturn(new byte[43]);

        Block block = new Block(blockHeader);

        ExtraDataRule rule = new ExtraDataRule(42);

        Assert.assertFalse(rule.isValid(block));
    }
}
