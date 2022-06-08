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

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.util.TimeProvider;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Created by mario on 23/01/17.
 */
@RunWith(MockitoJUnitRunner.class)
public class BlockTimeStampValidationRuleTest {

    private static final long DEFAULT_MAX_TIMESTAMPS_DIFF_IN_SECS = 5 * 60;

    private final ActivationConfig preRskip179Config = mock(ActivationConfig.class);
    private final ActivationConfig postRskip179Config = mock(ActivationConfig.class);

    private final TimeProvider timeProvider = mock(TimeProvider.class);

    private final NetworkParameters bitcoinNetworkParameters = mock(NetworkParameters.class);

    @Before
    public void setUp() {
        when(preRskip179Config.isActive(eq(ConsensusRule.RSKIP179), anyLong())).thenReturn(false);
        when(postRskip179Config.isActive(eq(ConsensusRule.RSKIP179), anyLong())).thenReturn(true);
    }

    @Test
    public void timestampsAreCloseEnough() {
        int validPeriod = 540;

        Function<byte[], BtcBlock> blockMakerMock = mock(Function.class);
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod, postRskip179Config, Constants.regtest(), timeProvider, blockMakerMock);

        byte[] bitcoinMergedMiningHeader = new byte[0];
        when(timeProvider.currentTimeMillis()).thenReturn(10_000_000L);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getBitcoinMergedMiningHeader()).thenReturn(bitcoinMergedMiningHeader);
        BtcBlock btcBlock = mock(BtcBlock.class);

        when(blockMakerMock.apply(eq(bitcoinMergedMiningHeader))).thenReturn(btcBlock);

        when(btcBlock.getTimeSeconds()).thenReturn(1_000L);
        when(header.getTimestamp()).thenReturn(1_000L);
        assertTrue(validationRule.isValid(header));

        when(btcBlock.getTimeSeconds()).thenReturn(1_000L + DEFAULT_MAX_TIMESTAMPS_DIFF_IN_SECS);
        when(header.getTimestamp()).thenReturn(1_000L);
        assertFalse(validationRule.isValid(header));

        when(btcBlock.getTimeSeconds()).thenReturn(1_000L + DEFAULT_MAX_TIMESTAMPS_DIFF_IN_SECS);
        when(header.getTimestamp()).thenReturn(1_001L);
        assertTrue(validationRule.isValid(header));

        when(btcBlock.getTimeSeconds()).thenReturn(1_001L);
        when(header.getTimestamp()).thenReturn(1_000L + DEFAULT_MAX_TIMESTAMPS_DIFF_IN_SECS);
        assertTrue(validationRule.isValid(header));
    }

    @Test
    public void blockInThePast() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod, preRskip179Config, Constants.regtest(), timeProvider);

        when(timeProvider.currentTimeMillis()).thenReturn(10_000_000L);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTimestamp()).thenReturn(10_000L - 1000);

        assertTrue(validationRule.isValid(header));
    }

    @Test
    public void blockInTheFutureLimit() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod, preRskip179Config, Constants.regtest(), timeProvider);

        when(timeProvider.currentTimeMillis()).thenReturn(10_000_000L);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTimestamp()).thenReturn(10_000L + validPeriod);

        assertTrue(validationRule.isValid(header));
    }

    @Test
    public void blockInTheFuture() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod, preRskip179Config, Constants.regtest(), timeProvider);

        when(timeProvider.currentTimeMillis()).thenReturn(10_000_000L);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTimestamp()).thenReturn(10_000L + 2*validPeriod);

        assertFalse(validationRule.isValid(header));
    }

    @Test
    public void blockTimeLowerThanParentTime() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod, preRskip179Config, Constants.regtest(), timeProvider);

        when(timeProvider.currentTimeMillis()).thenReturn(10_000_000L);
        BlockHeader header = mock(BlockHeader.class);
        Block parent = mock(Block.class);

        when(header.getTimestamp()).thenReturn(10_000L);

        when(parent.getTimestamp()).thenReturn(10_000L + 1000);

        assertFalse(validationRule.isValid(header, parent));
    }

    @Test
    public void blockTimeGreaterThanParentTime() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod, preRskip179Config, Constants.regtest(), timeProvider);

        when(timeProvider.currentTimeMillis()).thenReturn(10_000_000L);
        BlockHeader header = mock(BlockHeader.class);
        Block parent = mock(Block.class);

        when(header.getTimestamp()).thenReturn(10_000L);

        when(parent.getTimestamp()).thenReturn(10_000L - 1000);

        assertTrue(validationRule.isValid(header, parent));
    }

    @Test
    public void blockTimeEqualsParentTime() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod, preRskip179Config, Constants.regtest(), timeProvider);

        when(timeProvider.currentTimeMillis()).thenReturn(10_000_000L);
        BlockHeader header = mock(BlockHeader.class);
        Block parent = mock(Block.class);

        when(header.getTimestamp()).thenReturn(10_000L);

        when(parent.getTimestamp()).thenReturn(10_000L);

        assertFalse(validationRule.isValid(header, parent));
    }
}
