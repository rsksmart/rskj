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

import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.mine.ForkDetectionDataCalculator;
import co.rsk.panic.PanicProcessor;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Created by martin.medina on 14/05/19.
 */
public class ForkDetectionDataRule implements BlockValidationRule, BlockHeaderValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private static final String PANIC_LOGGING_TOPIC = "invalidForkDetectionDataRule";

    private final ActivationConfig activationConfig;
    private final ForkDetectionDataCalculator forkDetectionDataCalculator;
    private final ConsensusValidationMainchainView mainchainView;
    private final int requiredBlocksForForkDetectionDataCalculation;

    public ForkDetectionDataRule(
            ActivationConfig activationConfig,
            ConsensusValidationMainchainView mainchainView,
            ForkDetectionDataCalculator forkDetectionDataCalculator,
            int requiredBlocksForForkDetectionDataCalculation) {
        this.activationConfig = activationConfig;
        this.mainchainView = mainchainView;
        this.forkDetectionDataCalculator = forkDetectionDataCalculator;
        this.requiredBlocksForForkDetectionDataCalculation = requiredBlocksForForkDetectionDataCalculation;
    }

    @Override
    public boolean isValid(Block block) {
        return isValid(block.getHeader());
    }

    @Override
    public boolean isValid(BlockHeader header) {
        if(activationConfig.isActive(ConsensusRule.RSKIP110, header.getNumber()) )
        {
            if (hasForkDetectionDataWhereItShouldNotHave(header)){
                return false;
            }

            if(requiredBlocksForForkDetectionDataCalculation < header.getNumber()) {
                List<BlockHeader> headersView = mainchainView.get(
                        header.getParentHash(),
                        requiredBlocksForForkDetectionDataCalculation
                );
                if (!hasEnoughBlocksToCalculateForkDetectionData(header, headersView)) {
                    return false;
                }

                return isForkDetectionDataEqual(header, headersView);
            }
        }

        return true;
    }

    private boolean hasForkDetectionDataWhereItShouldNotHave(BlockHeader header) {
        if(header.getNumber() < requiredBlocksForForkDetectionDataCalculation
                && header.getMiningForkDetectionData().length > 0) {
            logger.warn("Header for block number {} includes fork detection data but it should not.",
                    header.getNumber());
            panicProcessor.panic(
                    PANIC_LOGGING_TOPIC,
                    "Header includes fork detection data but it should not."
            );
            return true;
        }
        return false;
    }

    private boolean isForkDetectionDataEqual(BlockHeader header, List<BlockHeader> headersView) {
        byte[] expectedForkDetectionData = forkDetectionDataCalculator.calculateWithBlockHeaders(headersView);
        if (!Arrays.equals(expectedForkDetectionData, header.getMiningForkDetectionData())) {
            logger.warn("Fork detection data does not match. Expected {} Actual: {}",
                    ByteUtil.toHexStringOrEmpty(expectedForkDetectionData),
                    ByteUtil.toHexStringOrEmpty(header.getMiningForkDetectionData())
            );
            panicProcessor.panic(
                    PANIC_LOGGING_TOPIC,
                    "Block hash for merged mining does not match."
            );
            return false;
        }

        return true;
    }

    private boolean hasEnoughBlocksToCalculateForkDetectionData(BlockHeader header, List<BlockHeader> headersView) {
        if (headersView.size() != requiredBlocksForForkDetectionDataCalculation) {
            logger.error("Missing blocks to calculate fork detection data. Block hash {} ", header.getPrintableHash());
            panicProcessor.panic(
                    PANIC_LOGGING_TOPIC,
                    "Missing blocks to calculate fork detection data."
            );
            return false;
        }

        return true;
    }
}
