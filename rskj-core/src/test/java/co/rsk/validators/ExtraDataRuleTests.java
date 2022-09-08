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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * @author martin.medina
 * @since 07.02.2017
 */
public class ExtraDataRuleTests {

    private BlockHeader blockHeader;
    private Block block;

    @BeforeEach
    public void setUp() {
        blockHeader = Mockito.mock(BlockHeader.class);
        block = Mockito.mock(Block.class);
        Mockito.when(block.getHeader()).thenReturn(blockHeader);
    }

    @Test
    public void blockWithValidExtraData() {
        Mockito.when(blockHeader.getExtraData()).thenReturn(new byte[32]);

        ExtraDataRule rule = new ExtraDataRule(42);

        Assertions.assertTrue(rule.isValid(block));
    }

    @Test
    public void blockWithValidNullExtraData() {
        Mockito.when(blockHeader.getExtraData()).thenReturn(null);

        ExtraDataRule rule = new ExtraDataRule(42);

        Assertions.assertTrue(rule.isValid(block));
    }

    @Test
    public void blockWithValidLongerExtraDataThanAccepted() {
        Mockito.when(blockHeader.getExtraData()).thenReturn(new byte[43]);

        ExtraDataRule rule = new ExtraDataRule(42);

        Assertions.assertFalse(rule.isValid(block));
    }
}
