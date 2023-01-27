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
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.util.TimeProvider;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Created by mario on 23/01/17.
 */
public class BlockTimeStampValidationRule implements BlockParentDependantValidationRule, BlockHeaderParentDependantValidationRule, BlockValidationRule, BlockHeaderValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    private final int validPeriodLength;
    private final ActivationConfig activationConfig;
    private final Constants constants;
    private final TimeProvider timeProvider;
    private final NetworkParameters bitcoinNetworkParameters;

    public BlockTimeStampValidationRule(int validPeriodLength, ActivationConfig activationConfig, Constants constants,
                                        TimeProvider timeProvider, NetworkParameters bitcoinNetworkParameters) {
        this.validPeriodLength = validPeriodLength;
        this.activationConfig = Objects.requireNonNull(activationConfig);
        this.constants = Objects.requireNonNull(constants);
        this.timeProvider = Objects.requireNonNull(timeProvider);
        this.bitcoinNetworkParameters = Objects.requireNonNull(bitcoinNetworkParameters);
    }

    public BlockTimeStampValidationRule(int validPeriodLength, ActivationConfig activationConfig, Constants constants, TimeProvider timeProvider) {
        this(validPeriodLength, activationConfig, constants, timeProvider, RegTestParams.get());
    }

    public BlockTimeStampValidationRule(int validPeriodLength, ActivationConfig activationConfig, Constants constants) {
        this(validPeriodLength, activationConfig, constants, System::currentTimeMillis, RegTestParams.get());
    }

    @Override
    public boolean isValid(Block block, BlockExecutor blockExecutor) {
        return isValid(block.getHeader());
    }

    @Override
    public boolean isValid(BlockHeader header) {
        if (this.validPeriodLength == 0) {
            return true;
        }

        final long currentTime = timeProvider.currentTimeMillis() / 1000L;
        final long blockTime = header.getTimestamp();

        boolean result = blockTime - currentTime <= this.validPeriodLength;

        if (!result) {
            logger.warn("Error validating block. Invalid timestamp {}.", blockTime);
        }

        return result && isBitcoinTimestampValid(header);
    }

    @Override
    public boolean isValid(BlockHeader header, Block parent) {
        if (this.validPeriodLength == 0) {
            return true;
        }

        boolean result = this.isValid(header);

        final long blockTime = header.getTimestamp();
        final long parentTime = parent.getTimestamp();
        result = result && (blockTime > parentTime);

        if (!result) {
            logger.warn("Error validating block. Invalid timestamp {} for parent timestamp {}", blockTime, parentTime);
        }

        return result;
    }

    @Override
    public boolean isValid(Block block, Block parent, BlockExecutor blockExecutor) {
        return isValid(block.getHeader(), parent);
    }

    private boolean isBitcoinTimestampValid(BlockHeader header) {
        if (!activationConfig.isActive(ConsensusRule.RSKIP179, header.getNumber())) {
            return true;
        }

        byte[] bitcoinMergedMiningHeader = header.getBitcoinMergedMiningHeader();
        if (bitcoinMergedMiningHeader == null) {
            return false;
        }

        BtcBlock btcBlock = makeBlock(bitcoinMergedMiningHeader);
        if (btcBlock == null) {
            return false;
        }

        long bitcoinTimestampInSecs = btcBlock.getTimeSeconds();

        long rskTimestampInSecs = header.getTimestamp();

        long maxTimestampsDiffInSecs = constants.getMaxTimestampsDiffInSecs(activationConfig.forBlock(header.getNumber()));
        boolean valid = Math.abs(bitcoinTimestampInSecs - rskTimestampInSecs) < maxTimestampsDiffInSecs;

        if (!valid) {
            logger.warn("Error validating block. RSK block timestamp {} and BTC block timestamp {} differ by more than {} secs.",
                    rskTimestampInSecs, bitcoinTimestampInSecs, maxTimestampsDiffInSecs);
        }

        return valid;
    }

    @Nullable
    private BtcBlock makeBlock(@Nonnull byte[] bitcoinMergedMiningHeader) {
        try {
            return bitcoinNetworkParameters.getDefaultSerializer().makeBlock(bitcoinMergedMiningHeader);
        } catch (RuntimeException e) {
            logger.error("Cannot make a BTC block from `{}`", bitcoinMergedMiningHeader, e);
            return null;
        }
    }
}
