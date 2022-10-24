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
import co.rsk.bitcoinj.core.MessageSerializer;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.util.TimeProvider;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * Created by mario on 23/01/17.
 */
@ExtendWith(MockitoExtension.class)
class BlockTimeStampValidationRuleTest {

    private static final long DEFAULT_MAX_TIMESTAMPS_DIFF_IN_SECS = 5 * 60;

    private final ActivationConfig preRskip179Config = mock(ActivationConfig.class);
    private final ActivationConfig postRskip179Config = mock(ActivationConfig.class);

    private final TimeProvider timeProvider = mock(TimeProvider.class);

    private final NetworkParameters bitcoinNetworkParameters = mock(NetworkParameters.class);

    @BeforeEach
    void setUp() {
        when(preRskip179Config.isActive(eq(ConsensusRule.RSKIP179), anyLong())).thenReturn(false);
        when(postRskip179Config.isActive(eq(ConsensusRule.RSKIP179), anyLong())).thenReturn(true);
    }

    @Test
    void timestampsAreCloseEnough() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod, postRskip179Config, Constants.regtest(), timeProvider, bitcoinNetworkParameters);

        byte[] bitcoinMergedMiningHeader = new byte[0];
        when(timeProvider.currentTimeMillis()).thenReturn(10_000_000L);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getBitcoinMergedMiningHeader()).thenReturn(bitcoinMergedMiningHeader);
        BtcBlock btcBlock = mock(BtcBlock.class);
        MessageSerializer messageSerializer = mock(MessageSerializer.class);
        when(messageSerializer.makeBlock(bitcoinMergedMiningHeader)).thenReturn(btcBlock);
        when(bitcoinNetworkParameters.getDefaultSerializer()).thenReturn(messageSerializer);

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
    void blockInThePast() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod, preRskip179Config, Constants.regtest(), timeProvider);

        when(timeProvider.currentTimeMillis()).thenReturn(10_000_000L);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTimestamp()).thenReturn(10_000L - 1000);

        assertTrue(validationRule.isValid(header));
    }

    @Test
    void blockInTheFutureLimit() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod, preRskip179Config, Constants.regtest(), timeProvider);

        when(timeProvider.currentTimeMillis()).thenReturn(10_000_000L);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTimestamp()).thenReturn(10_000L + validPeriod);

        assertTrue(validationRule.isValid(header));
    }

    @Test
    void blockInTheFuture() {
        int validPeriod = 540;
        BlockTimeStampValidationRule validationRule = new BlockTimeStampValidationRule(validPeriod, preRskip179Config, Constants.regtest(), timeProvider);

        when(timeProvider.currentTimeMillis()).thenReturn(10_000_000L);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTimestamp()).thenReturn(10_000L + 2*validPeriod);

        assertFalse(validationRule.isValid(header));
    }

    @Test
    void blockTimeLowerThanParentTime() {
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
    void blockTimeGreaterThanParentTime() {
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
    void blockTimeEqualsParentTime() {
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
