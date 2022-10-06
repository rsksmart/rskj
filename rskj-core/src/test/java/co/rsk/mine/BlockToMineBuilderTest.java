/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.mine;

import co.rsk.config.GasLimitConfig;
import co.rsk.config.MiningConfig;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockResult;
import co.rsk.core.bc.FamilyUtils;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.validators.BlockValidationRule;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BlockToMineBuilderTest {

    private BlockToMineBuilder blockBuilder;
    private BlockValidationRule validationRules;
    private BlockExecutor blockExecutor;
    private ActivationConfig activationConfig;

    @Before
    public void setUp() {
        validationRules = mock(BlockValidationRule.class);

        RepositoryLocator repositoryLocator = mock(RepositoryLocator.class);
        StateRootHandler stateRootHandler = mock(StateRootHandler.class);
        MiningConfig miningConfig = mock(MiningConfig.class);
        DifficultyCalculator difficultyCalculator = mock(DifficultyCalculator.class);
        MinimumGasPriceCalculator minimumGasPriceCalculator = mock(MinimumGasPriceCalculator.class);
        MinerUtils minerUtils = mock(MinerUtils.class);
        activationConfig = mock(ActivationConfig.class);

        blockExecutor = mock(BlockExecutor.class);
        blockBuilder = new BlockToMineBuilder(
                activationConfig,
                miningConfig,
                repositoryLocator,
                mock(BlockStore.class),
                mock(TransactionPool.class),
                difficultyCalculator,
                new GasLimitCalculator(Constants.mainnet()),
                new ForkDetectionDataCalculator(),
                validationRules,
                mock(MinerClock.class),
                new BlockFactory(activationConfig),
                blockExecutor,
                minimumGasPriceCalculator,
                minerUtils
        );

        BlockDifficulty blockDifficulty = mock(BlockDifficulty.class);
        Repository snapshot = mock(Repository.class);
        GasLimitConfig gasLimitConfig = new GasLimitConfig(0,0,false);

        when(minerUtils.getAllTransactions(any())).thenReturn(new ArrayList<>());
        when(minerUtils.filterTransactions(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(new ArrayList<>());
        when(repositoryLocator.snapshotAt(any())).thenReturn(snapshot);
        when(minimumGasPriceCalculator.calculate(any())).thenReturn(mock(Coin.class));
        when(miningConfig.getGasLimit()).thenReturn(gasLimitConfig);
        when(miningConfig.getUncleListLimit()).thenReturn(10);
        when(miningConfig.getCoinbaseAddress()).thenReturn(TestUtils.randomAddress());
        when(difficultyCalculator.calcDifficulty(any(), any())).thenReturn(blockDifficulty);
    }

    @Test
    public void BuildBlockHasEmptyUnclesWhenCreateAnInvalidBlock() {
        Consumer<BlockHeader> test = (parent) -> {
            BlockResult expectedResult = mock(BlockResult.class);
            ArgumentCaptor<Block> blockCaptor = ArgumentCaptor.forClass(Block.class);

            when(validationRules.isValid(any())).thenReturn(false);
            when(blockExecutor.executeAndFill(blockCaptor.capture(), any())).thenReturn(expectedResult);

            blockBuilder.build(new ArrayList<>(Collections.singletonList(parent)), new byte[0]);

            assertThat(blockCaptor.getValue().getUncleList(), empty());
        };

        runMocked(test);
    }

    @Test
    public void BuildBlockHasUnclesWhenCreateAnInvalidBlock() {
        Consumer<BlockHeader> test = (parent) -> {
            BlockResult expectedResult = mock(BlockResult.class);
            ArgumentCaptor<Block> blockCaptor = ArgumentCaptor.forClass(Block.class);
            when(validationRules.isValid(any())).thenReturn(true);
            when(blockExecutor.executeAndFill(blockCaptor.capture(), any())).thenReturn(expectedResult);

            blockBuilder.build(new ArrayList<>(Collections.singletonList(parent)), new byte[0]);

            assertThat(blockCaptor.getValue().getUncleList(), hasSize(1));
        };

        runMocked(test);
    }

    private Block prepareForActivationTest() {
        Keccak256 parentHash = TestUtils.randomHash();

        BlockHeader parent = mock(BlockHeader.class);
        when(parent.getNumber()).thenReturn(500L);
        when(parent.getHash()).thenReturn(parentHash);
        when(parent.getGasLimit()).thenReturn(new byte[0]);
        when(parent.getMinimumGasPrice()).thenReturn(mock(Coin.class));

        when(validationRules.isValid(any())).thenReturn(true);

        BlockResult expectedResult = mock(BlockResult.class);
        ArgumentCaptor<Block> blockCaptor = ArgumentCaptor.forClass(Block.class);
        when(blockExecutor.executeAndFill(blockCaptor.capture(), any())).thenReturn(expectedResult);

        blockBuilder.build(new ArrayList<>(Collections.singletonList(parent)), new byte[0]);

        return blockCaptor.getValue();
    }

    @Test
    public void buildBlockBeforeUMMActivation() {
        when(activationConfig.isActive(ConsensusRule.RSKIPUMM, 501L)).thenReturn(false);
        Block actualBlock = this.prepareForActivationTest();
        assertNull(actualBlock.getHeader().getUmmRoot());
    }

    @Test
    public void buildBlockAfterUMMActivation() {
        when(activationConfig.isActive(ConsensusRule.RSKIPUMM, 501L)).thenReturn(true);
        Block actualBlock = this.prepareForActivationTest();
        assertThat(actualBlock.getHeader().getUmmRoot(), is(new byte[0]));
    }

    @Test
    public void buildBlockBeforeRskip351() {
        when(activationConfig.getHeaderVersion(501L)).thenReturn((byte) 0x0);
        Block actualBlock = this.prepareForActivationTest();
        assertEquals(0, actualBlock.getHeader().getVersion());
    }

    @Test
    public void buildBlockAfterRskip351() {
        when(activationConfig.getHeaderVersion(501L)).thenReturn((byte) 0x1);
        Block actualBlock = this.prepareForActivationTest();
        assertEquals(1, actualBlock.getHeader().getVersion());
    }

    private void runMocked(Consumer<BlockHeader> task) {
        BlockHeader blockHeader = mock(BlockHeader.class);
        long blockNumber = 42L;
        when(blockHeader.getNumber()).thenReturn(blockNumber);
        Keccak256 blockHash = TestUtils.randomHash();
        when(blockHeader.getHash()).thenReturn(blockHash);
        when(blockHeader.getMinimumGasPrice()).thenReturn(mock(Coin.class));
        when(blockHeader.getGasLimit()).thenReturn(new byte[0]);
        BlockHeader relative = createBlockHeader();

        try (MockedStatic<FamilyUtils> familyUtilsMocked = mockStatic(FamilyUtils.class)) {
            familyUtilsMocked.when(() -> FamilyUtils.getUnclesHeaders(any(), eq(blockNumber + 1L), eq(blockHash), anyInt()))
                    .thenReturn(Collections.singletonList(relative));

            task.accept(blockHeader);
        }
    }

    private BlockHeader createBlockHeader() {
        return new BlockHeader(
                (byte) 0x0,
                EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, TestUtils.randomAddress(),
                EMPTY_TRIE_HASH, null, EMPTY_TRIE_HASH,
                new Bloom().getData(), BlockDifficulty.ZERO, 1L,
                EMPTY_BYTE_ARRAY, 0L, 0L, EMPTY_BYTE_ARRAY, Coin.ZERO,
                EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY,
                Coin.ZERO, 0, false, true, false,
                new byte[0]
        );
    }
}
