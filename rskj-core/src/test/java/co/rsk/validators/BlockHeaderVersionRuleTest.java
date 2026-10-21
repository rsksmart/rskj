/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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

import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockHeaderVersionRuleTest {

    private static final long BLOCK_NUMBER = 1L;

    /** A config that requires exactly this header version at {@link #BLOCK_NUMBER}. */
    private static ActivationConfig configRequiring(int version) {
        switch (version) {
            case 0:
                return ActivationConfigsForTest.allBut(ConsensusRule.RSKIP351, ConsensusRule.RSKIP144, ConsensusRule.RSKIP535);
            case 1:
                return ActivationConfigsForTest.allBut(ConsensusRule.RSKIP535);
            default:
                return ActivationConfigsForTest.all();
        }
    }

    /** A real header built the way the miner builds it under the config that requires {@code version}. */
    private static BlockHeader headerOfVersion(int version) {
        BlockHeader header = new BlockHeaderBuilder(configRequiring(version)).setNumber(BLOCK_NUMBER).build();
        assertEquals((byte) version, header.getVersion());
        return header;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void acceptsTheVersionTheHeightRequires(int version) {
        BlockHeaderVersionRule rule = new BlockHeaderVersionRule(configRequiring(version));

        assertTrue(rule.isValid(headerOfVersion(version)));
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "0, 2", "1, 0", "1, 2", "2, 0", "2, 1"})
    void rejectsEveryOtherVersion(int requiredVersion, int headerVersion) {
        BlockHeaderVersionRule rule = new BlockHeaderVersionRule(configRequiring(requiredVersion));

        assertFalse(rule.isValid(headerOfVersion(headerVersion)));
    }

    @Test
    void validatesTheHeaderOfABlock() {
        Block block = mock(Block.class);
        when(block.getHeader()).thenReturn(headerOfVersion(1));

        assertTrue(new BlockHeaderVersionRule(configRequiring(1)).isValid(block));
        assertFalse(new BlockHeaderVersionRule(configRequiring(2)).isValid(block));
    }

    @Test
    void skipsGenesisWhateverVersionTheConfigRequiresAtHeightZero() {
        // all() activates RSKIP351 and RSKIP535 at height 0, so it requires version 2 there,
        // while the genesis header is always version 0.
        BlockHeader genesis = new BlockGenerator().getGenesisBlock().getHeader();
        assertEquals((byte) 0, genesis.getVersion());
        assertEquals((byte) 2, ActivationConfigsForTest.all().getHeaderVersion(genesis.getNumber()));

        assertTrue(new BlockHeaderVersionRule(ActivationConfigsForTest.all()).isValid(genesis));
    }
}
