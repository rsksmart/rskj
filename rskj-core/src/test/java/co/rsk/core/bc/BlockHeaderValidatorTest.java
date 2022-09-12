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

package co.rsk.core.bc;

import co.rsk.crypto.Keccak256;
import co.rsk.validators.BlockHeaderValidationRule;
import org.ethereum.core.Block;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class BlockHeaderValidatorTest {

    private final BlockHeaderValidationRule blockHeaderValidator = mock(BlockHeaderValidationRule.class);

    private final BlockHeaderValidatorImpl blockRelayValidator = new BlockHeaderValidatorImpl(blockHeaderValidator);

    @Test
    void genesisCheck() {
        Block block = mock(Block.class);
        when(block.isGenesis()).thenReturn(true);

        boolean actualResult = blockRelayValidator.isValid(block);

        Assertions.assertFalse(actualResult);

        verify(block).isGenesis();
        verify(blockHeaderValidator, never()).isValid(any());
    }

    @Test
    void blockHeaderValidatorCheck() {
        Block block = mock(Block.class);

        when(blockHeaderValidator.isValid(any())).thenReturn(false);

        boolean actualResult = blockRelayValidator.isValid(block);

        Assertions.assertFalse(actualResult);

        verify(block).isGenesis();
        verify(blockHeaderValidator).isValid(any());
    }

    @Test
    void allValidatorsCheck() {
        Block block = mock(Block.class);
        Keccak256 parentHash = mock(Keccak256.class);

        when(block.getParentHash()).thenReturn(parentHash);
        when(blockHeaderValidator.isValid(any())).thenReturn(true);

        boolean actualResult = blockRelayValidator.isValid(block);

        Assertions.assertTrue(actualResult);

        verify(block).isGenesis();
        verify(blockHeaderValidator).isValid(any());
    }
}
