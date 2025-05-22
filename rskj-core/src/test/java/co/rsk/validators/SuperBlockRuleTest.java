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

package co.rsk.validators;

import co.rsk.core.bc.SuperBlockFields;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SuperBlockRuleTest {

    private ActivationConfig activationConfig;
    private SuperBlockRule rule;

    @BeforeEach
    void setUp() {
        activationConfig = mock(ActivationConfig.class);
        BlockStore blockStore = mock(BlockStore.class);
        ReceiptStore receiptStore = mock(ReceiptStore.class);
        rule = new SuperBlockRule(activationConfig, blockStore, receiptStore);
    }

    @Test
    void testIsValidBlockHeader_RSKIP481Active() {
        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(100L);
        when(activationConfig.isActive(any(ConsensusRule.class), eq(100L))).thenReturn(true);
        when(header.getSuperChainDataHash()).thenReturn(new byte[]{1, 2, 3});

        assertTrue(rule.isValid(header));

        when(header.getSuperChainDataHash()).thenReturn(null);
        assertFalse(rule.isValid(header));
    }

    @Test
    void testIsValidBlockHeader_RSKIP481Inactive() {
        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(50L);
        when(activationConfig.isActive(any(ConsensusRule.class), eq(50L))).thenReturn(false);
        when(header.getSuperChainDataHash()).thenReturn(null);

        assertTrue(rule.isValid(header));

        when(header.getSuperChainDataHash()).thenReturn(new byte[]{1, 2, 3});
        assertFalse(rule.isValid(header));
    }

    @Test
    void testIsValidBlock_RSKIP481Inactive() {
        Block block = mock(Block.class);
        BlockHeader header = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(header);
        when(header.getNumber()).thenReturn(10L);
        when(activationConfig.isActive(any(ConsensusRule.class), eq(10L))).thenReturn(false);
        when(header.getSuperChainDataHash()).thenReturn(null);

        SuperBlockFields superBlockFields = null;
        when(block.getSuperBlockFields()).thenReturn(superBlockFields);
        when(block.isSuper()).thenReturn(Optional.of(false));

        assertTrue(rule.isValid(block));

        when(block.isSuper()).thenReturn(Optional.of(true));
        assertFalse(rule.isValid(block));
    }

    @Test
    void testIsValidBlock_RSKIP481Active_SuperBlock() {
        Block block = mock(Block.class);
        BlockHeader header = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(header);
        when(header.getNumber()).thenReturn(200L);
        when(activationConfig.isActive(any(ConsensusRule.class), eq(200L))).thenReturn(true);
        when(header.getSuperChainDataHash()).thenReturn(new byte[]{1, 2, 3});

        SuperBlockFields superBlockFields = mock(SuperBlockFields.class);
        when(block.getSuperBlockFields()).thenReturn(superBlockFields);
        when(block.isSuper()).thenReturn(Optional.of(true));

        // areSuperBlockFieldsValid returns false by default, so isValid should be false
        assertFalse(rule.isValid(block));
    }

    @Test
    void testIsValidBlock_RSKIP481Active_NonSuperBlock() {
        Block block = mock(Block.class);
        BlockHeader header = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(header);
        when(header.getNumber()).thenReturn(200L);
        when(activationConfig.isActive(any(ConsensusRule.class), eq(200L))).thenReturn(true);
        when(header.getSuperChainDataHash()).thenReturn(new byte[]{1, 2, 3});

        when(block.getSuperBlockFields()).thenReturn(null);
        when(block.isSuper()).thenReturn(Optional.of(false));

        assertTrue(rule.isValid(block));
    }

    @Test
    void testIsValidBlock_RSKIP481Active_EmptySuper() {
        Block block = mock(Block.class);
        BlockHeader header = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(header);
        when(block.getNumber()).thenReturn(200L);
        when(header.getNumber()).thenReturn(200L);
        when(activationConfig.isActive(any(ConsensusRule.class), eq(200L))).thenReturn(true);
        when(header.getSuperChainDataHash()).thenReturn(new byte[]{1, 2, 3});

        when(block.isSuper()).thenReturn(Optional.empty());
        when(block.getSuperBlockFields()).thenReturn(null);

        assertTrue(rule.isValid(block));
    }

    @Test
    void testIsValidBlock_HeaderInvalid_RSKIP481Active() {
        Block block = mock(Block.class);
        BlockHeader header = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(header);
        when(header.getNumber()).thenReturn(100L);
        when(activationConfig.isActive(any(ConsensusRule.class), eq(100L))).thenReturn(true);
        when(header.getSuperChainDataHash()).thenReturn(null); // Should be non-null when active

        assertFalse(rule.isValid(block));
    }

    @Test
    void testIsValidBlock_HeaderInvalid_RSKIP481Inactive() {
        Block block = mock(Block.class);
        BlockHeader header = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(header);
        when(header.getNumber()).thenReturn(50L);
        when(activationConfig.isActive(any(ConsensusRule.class), eq(50L))).thenReturn(false);
        when(header.getSuperChainDataHash()).thenReturn(new byte[]{1, 2, 3}); // Should be null when inactive

        assertFalse(rule.isValid(block));
    }
}
