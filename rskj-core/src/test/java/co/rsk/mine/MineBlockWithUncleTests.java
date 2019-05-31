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

package co.rsk.mine;

import co.rsk.config.GasLimitConfig;
import co.rsk.config.MiningConfig;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.FamilyUtils;
import co.rsk.db.StateRootHandler;
import co.rsk.validators.BlockValidationRule;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(FamilyUtils.class)
public class BlockMineTest {

    private BlockToMineBuilder blockBuilder;
    private BlockValidationRule validationRules;
    private Repository repository;
    private StateRootHandler stateRootHandler;
    private MiningConfig miningConfig;
    private TestSystemProperties config;
    private DifficultyCalculator difficultyCalculator;
    private MinimumGasPriceCalculator mininumGasPriceCalculator;

    @Before
    public void setUp() throws Exception {
        validationRules = mock(BlockValidationRule.class);
        config = spy(new TestSystemProperties());
        when(config.getNetworkConstants()).thenReturn(Constants.mainnet());

        repository = mock(Repository.class);
        stateRootHandler = mock(StateRootHandler.class);
        miningConfig = mock(MiningConfig.class);
        difficultyCalculator = mock(DifficultyCalculator.class);
        mininumGasPriceCalculator = mock(MinimumGasPriceCalculator.class);

        blockBuilder = new BlockToMineBuilder(
                mock(ActivationConfig.class),
                miningConfig,
                repository,
                stateRootHandler,
                mock(BlockStore.class),
                mock(TransactionPool.class),
                difficultyCalculator,
                new GasLimitCalculator(config.getNetworkConstants()),
                validationRules,
                mock(MinerClock.class),
                new BlockFactory(config.getActivationConfig()),
                mock(BlockExecutor.class),
                mininumGasPriceCalculator
        );
    }

    @Test
    public void EmptyUnclesBlocksWhenCreateAnInvalidNextBlock() {
        PowerMockito.mockStatic(FamilyUtils.class);
        Block block = mock(Block.class);
        Coin coin = mock(Coin.class);
        BlockDifficulty blockDifficulty = mock(BlockDifficulty.class);
        Repository snapshot = mock(Repository.class);
        BlockHeader blockHeader = mock(BlockHeader.class);

        GasLimitConfig gasLimitConfig = new GasLimitConfig(0,0,false);

        List<BlockHeader> uncles = Collections.singletonList(blockHeader);
        PowerMockito.when(FamilyUtils.getUnclesHeaders(any(), anyLong(), any(), anyInt())).thenReturn(uncles);

        when(mininumGasPriceCalculator.calculate(any(), any())).thenReturn(coin);
        when(block.getHash()).thenReturn(TestUtils.randomHash());
        when(block.getHeader()).thenReturn(blockHeader);
        when(block.getMinimumGasPrice()).thenReturn(coin);
        when(block.getGasLimit()).thenReturn(new byte[0]);

        when(validationRules.isValid(any())).thenReturn(false);
        when(repository.getSnapshotTo(any())).thenReturn(snapshot);
        when(stateRootHandler.translate(any())).thenReturn(TestUtils.randomHash());
        when(miningConfig.getGasLimit()).thenReturn(gasLimitConfig);
        when(miningConfig.getCoinbaseAddress()).thenReturn(TestUtils.randomAddress());
        when(difficultyCalculator.calcDifficulty(any(), any())).thenReturn(blockDifficulty);

        Block nextBLock = blockBuilder.build(block, new byte[0]);

        //We say that next block has 1 uncle but because validationRule.isValid was false we get a block without uncles
        assertThat(nextBLock.getUncleList(), empty());
    }
}