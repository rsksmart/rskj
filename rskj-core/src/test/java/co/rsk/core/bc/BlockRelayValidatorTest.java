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
import co.rsk.validators.BlockHeaderParentDependantValidationRule;
import co.rsk.validators.BlockValidationRule;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class BlockRelayValidatorTest {

    private final BlockStore blockStore = mock(BlockStore.class);
    private final BlockHeaderParentDependantValidationRule blockParentValidator = mock(BlockHeaderParentDependantValidationRule.class);
    private final BlockValidationRule blockValidator = mock(BlockValidationRule.class);

    private final BlockRelayValidatorImpl blockRelayValidator = new BlockRelayValidatorImpl(blockStore, blockParentValidator, blockValidator);

    @Test
    public void genesisCheck() {
        Block block = mock(Block.class);
        when(block.isGenesis()).thenReturn(true);

        boolean actualResult = blockRelayValidator.isValid(block);

        Assert.assertFalse(actualResult);

        verify(block).isGenesis();
        verify(blockValidator, never()).isValid(any());
        verify(blockParentValidator, never()).isValid(any(), any());
    }

    @Test
    public void blockValidatorCheck() {
        Block block = mock(Block.class);

        when(blockValidator.isValid(any())).thenReturn(false);

        boolean actualResult = blockRelayValidator.isValid(block);

        Assert.assertFalse(actualResult);

        verify(block).isGenesis();
        verify(blockValidator).isValid(any());
        verify(blockParentValidator, never()).isValid(any(), any());
    }

    @Test
    public void blockParentValidatorCheck() {
        Block block = mock(Block.class);
        Keccak256 parentHash = mock(Keccak256.class);
        Block parentBlock = mock(Block.class);

        when(block.getParentHash()).thenReturn(parentHash);
        when(blockStore.getBlockByHash(any())).thenReturn(parentBlock);
        when(blockValidator.isValid(any())).thenReturn(true);
        when(blockParentValidator.isValid(any(), any())).thenReturn(false);

        boolean actualResult = blockRelayValidator.isValid(block);

        Assert.assertFalse(actualResult);

        verify(block).isGenesis();
        verify(blockValidator).isValid(any());
        verify(blockParentValidator).isValid(any(), any());
    }

    @Test
    public void allValidatorsCheck() {
        Block block = mock(Block.class);
        Keccak256 parentHash = mock(Keccak256.class);
        Block parentBlock = mock(Block.class);

        when(block.getParentHash()).thenReturn(parentHash);
        when(blockStore.getBlockByHash(any())).thenReturn(parentBlock);
        when(blockValidator.isValid(any())).thenReturn(true);
        when(blockParentValidator.isValid(any(), any())).thenReturn(true);

        boolean actualResult = blockRelayValidator.isValid(block);

        Assert.assertTrue(actualResult);

        verify(block).isGenesis();
        verify(blockValidator).isValid(any());
        verify(blockParentValidator).isValid(any(), any());
    }
}
