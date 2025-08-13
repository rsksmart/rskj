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
import co.rsk.crypto.Keccak256;
import co.rsk.util.HexUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.Optional;

class DifficultyCalculatorTest {
    private record BlocksResponse(Block current, Block parent, BlockStore blockStore, TestSystemProperties config) {
    }

    private static final String HASH1 = "0x5e20a0453cecd065ea59c37ac63e079ee08998b6045136a8ce6635c7912ec0b7";
    private static final String HASH2 = "0x5e20a0453cecd065ea59c37ac63e079ee08998b6045136a8ce6635c7912ec0b6";

    @Test
    void whenCalculateDifficultyForSuperBlock_thenReturnBlockDifficultyUnderMinCorrectionFactor() {
        // given
        var blocksResponse = mockBlocks(10L, true, 1, 2, 3000);
        var difficultyCalculator = new DifficultyCalculator(
                blocksResponse.config().getActivationConfig(),
                blocksResponse.config().getNetworkConstants()
        );

        // when
        var result = difficultyCalculator.calcDifficulty(
                blocksResponse.blockStore(),
                blocksResponse.current().getHeader(),
                blocksResponse.parent().getHeader()
        );

        // then
        Assertions.assertEquals(BigInteger.valueOf(5), result.asBigInteger());
    }

    @Test
    void whenCalculateDifficultyForSuperBlock_thenReturnBlockDifficultyOverMaxCorrectionFactor() {
        // given
        var blocksResponse = mockBlocks(150L, true, 1, 2, 3);
        var difficultyCalculator = new DifficultyCalculator(
                blocksResponse.config().getActivationConfig(),
                blocksResponse.config().getNetworkConstants()
        );

        // when
        var result = difficultyCalculator.calcDifficulty(
                blocksResponse.blockStore(),
                blocksResponse.current().getHeader(),
                blocksResponse.parent().getHeader()
        );

        // then
        Assertions.assertEquals(BigInteger.valueOf(225), result.asBigInteger());
    }

    @Test
    void whenCalculateDifficultyForSuperBlock_thenReturnSameBlockDifficulty() {
        // given
        var config = new TestSystemProperties();
        var difficultyCalculator = new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants());

        var block0 = buildBlock(4L, HASH1, null, 10L, 2, true);
        var block1 = buildBlock(5L, null, HASH1, 10L, 3, true);
        var blockStore = Mockito.mock(BlockStore.class);

        // when
        var result = difficultyCalculator.calcDifficulty(blockStore, block1.getHeader(), block0.getHeader());

        // then
        Assertions.assertEquals(BigInteger.valueOf(10), result.asBigInteger());
    }

    private BlocksResponse mockBlocks(long difficulty, boolean isSuper, long timestamp0, long timestamp1, long timestamp2) {
        var block0 = buildBlock(1L, HASH1, null, difficulty, timestamp0, isSuper);
        var block1 = buildBlock(2L, HASH2, HASH1, difficulty, timestamp1, isSuper);
        var block2 = buildBlock(3L, null, HASH2, difficulty, timestamp2, isSuper);

        var blockStore = Mockito.mock(BlockStore.class);
        var hash1 = block1.getHeader().getHash().getBytes();
        Mockito.doReturn(block1).when(blockStore).getBlockByHash(Mockito.eq(hash1));
        var hash0 = block0.getHeader().getHash().getBytes();
        Mockito.doReturn(block0).when(blockStore).getBlockByHash(Mockito.eq(hash0));

        return new BlocksResponse(block2, block1, blockStore, new TestSystemProperties());
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

        Mockito.doReturn(new BlockDifficulty(BigInteger.valueOf(difficulty))).when(header).getDifficulty();
        Mockito.doReturn(timestamp).when(header).getTimestamp();
        Mockito.doReturn(Optional.ofNullable(isSuper)).when(header).isSuper();

        var block = Mockito.mock(Block.class);
        Mockito.doReturn(header).when(block).getHeader();
        Mockito.doReturn(header.getHash()).when(block).getHash();
        Mockito.doReturn(header.getParentHash()).when(block).getParentHash();

        return block;
    }
}
