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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.bc.SuperBlockFields;
import co.rsk.crypto.Keccak256;
import co.rsk.util.HexUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.Keccak256Helper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.Optional;

class SuperDifficultyCalculatorTest {
    private record BlocksResponse(Block current, Block parent, TestSystemProperties config) {
    }

    private static final String HASH1 = "0x5e20a0453cecd065ea59c37ac63e079ee08998b6045136a8ce6635c7912ec0b7";
    private static final String HASH2 = "0x5e20a0453cecd065ea59c37ac63e079ee08998b6045136a8ce6635c7912ec0b6";

    @Test
    void whenCalculateDifficultyForSuperBlock_thenReturnBlockDifficultyUnderMinCorrectionFactor() {
        // given
        var blocksResponse = mockBlocks(10L, true, 1, 3000);
        var superDifficultyCalculator = new SuperDifficultyCalculator(
                blocksResponse.config().getNetworkConstants()
        );

        // when
        var result = superDifficultyCalculator.calcSuperDifficulty(
                blocksResponse.current(),
                blocksResponse.parent()
        );

        // then
        Assertions.assertEquals(BigInteger.valueOf(640), result.asBigInteger());
    }

    @Test
    void whenCalculateDifficultyForSuperBlock_thenReturnBlockDifficultyOverMaxCorrectionFactor() {
        // given
        var blocksResponse = mockBlocks(150L, true, 1, 3);
        var superDifficultyCalculator = new SuperDifficultyCalculator(
                blocksResponse.config().getNetworkConstants()
        );

        // when
        var result = superDifficultyCalculator.calcSuperDifficulty(
                blocksResponse.current(),
                blocksResponse.parent()
        );

        // then
        Assertions.assertEquals(BigInteger.valueOf(28800), result.asBigInteger());
    }

    @Test
    void whenCalculateDifficultyForSuperBlock_thenReturnSameBlockDifficulty() {
        // given
        var config = new TestSystemProperties();
        var superDifficultyCalculator = new SuperDifficultyCalculator(
                config.getNetworkConstants()
        );

        var block0 = buildBlock(4L, HASH1, null, 10L, 2, true);
        var block1 = buildBlock(5L, null, HASH1, 10L, 3, true);

        // when
        var result = superDifficultyCalculator.calcSuperDifficulty(block1, block0);

        // then
        Assertions.assertEquals(BigInteger.valueOf(10), result.asBigInteger());
    }

    @Test
    void whenCalculateDifficultyForSuperBlockWithSameTimestamp_thenReturnBlockDifficulty() {
        // given
        var config = new TestSystemProperties();
        var superDifficultyCalculator = new SuperDifficultyCalculator(
                config.getNetworkConstants()
        );

        var block0 = buildBlock(5L, HASH1, null, 10L, 2, true);
        var block1 = buildBlock(6L, null, HASH1, 10L, 2, true);

        // when
        var result = superDifficultyCalculator.calcSuperDifficulty(block1, block0);

        // then
        Assertions.assertEquals(BigInteger.valueOf(1920), result.asBigInteger());
    }

    @Test
    void whenCalculateDifficultyForSuperBlockWithNegativeTimestamp_thenReturnBlockDifficulty() {
        // given
        var config = new TestSystemProperties();
        var superDifficultyCalculator = new SuperDifficultyCalculator(
                config.getNetworkConstants()
        );

        var block0 = buildBlock(5L, HASH1, null, 10L, 3, true);
        var block1 = buildBlock(6L, null, HASH1, 10L, 2, true);

        // when
        var result = superDifficultyCalculator.calcSuperDifficulty(block1, block0);

        // then
        Assertions.assertEquals(BigInteger.valueOf(1920), result.asBigInteger());
    }

    private BlocksResponse mockBlocks(long difficulty, boolean isSuper, long timestamp1, long timestamp2) {
        var block1 = buildBlock(2L, HASH2, HASH1, difficulty, timestamp1, isSuper);
        var block2 = buildBlock(3L, null, HASH2, difficulty, timestamp2, isSuper);

        return new BlocksResponse(block2, block1, new TestSystemProperties());
    }

    private Block buildBlock(long number, String hash, String parentHash, long difficulty, long timestamp, boolean isSuper) {
        var header = Mockito.mock(BlockHeader.class);
        Mockito.doReturn(number).when(header).getNumber();

        if (parentHash != null) {
            Mockito.doReturn(new Keccak256(Keccak256Helper.keccak256(HexUtils.stringHexToByteArray(parentHash)))).when(header).getParentHash();
        }

        if (hash != null) {
            Mockito.doReturn(new Keccak256(Keccak256Helper.keccak256(HexUtils.stringHexToByteArray(hash)))).when(header).getHash();
        }

        Mockito.doReturn(timestamp).when(header).getTimestamp();
        Mockito.doReturn(Optional.ofNullable(isSuper)).when(header).isSuper();

        var block = Mockito.mock(Block.class);
        Mockito.doReturn(header).when(block).getHeader();
        Mockito.doReturn(header.getHash()).when(block).getHash();
        Mockito.doReturn(header.getParentHash()).when(block).getParentHash();
        Mockito.doReturn(header.isSuper()).when(block).isSuper();
        Mockito.doReturn(header.getTimestamp()).when(block).getTimestamp();
        Mockito.doReturn(header.getNumber()).when(block).getNumber();

        var superBlockFields = new SuperBlockFields(
                null, number, null, new BlockDifficulty(BigInteger.valueOf(difficulty))
        );

        Mockito.doReturn(superBlockFields).when(block).getSuperBlockFields();

        return block;
    }
}
