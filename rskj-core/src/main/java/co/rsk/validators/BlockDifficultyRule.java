/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import co.rsk.core.BlockDifficulty;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.ConsensusValidationMainchainView;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Checks block's difficulty against calculated difficulty value
 *
 * @author Mikhail Kalinin
 * @since 02.09.2015
 */
public class BlockDifficultyRule implements BlockParentDependantValidationRule, BlockHeaderParentDependantValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    private final DifficultyCalculator difficultyCalculator;
    private final ConsensusValidationMainchainView mainchainView;

    public BlockDifficultyRule(DifficultyCalculator difficultyCalculator, ConsensusValidationMainchainView mainchainView) {
        this.difficultyCalculator = difficultyCalculator;
        this.mainchainView = mainchainView;
    }

    @Override
    public boolean isValid(BlockHeader header, BlockHeader parent) {
        if (header == null || parent == null) {
            logger.warn("BlockDifficultyRule - block or parent are null");
            return false;
        }
        List<BlockHeader> blockWindow = mainchainView.getFromBestBlock(DifficultyCalculator.BLOCK_COUNT_WINDOW);
        BlockDifficulty calcDifficulty = difficultyCalculator.calcDifficulty(header, parent, blockWindow);
        BlockDifficulty difficulty = header.getDifficulty();

        if (!difficulty.equals(calcDifficulty)) {
            logger.warn("#{}: difficulty != calcDifficulty", header.getNumber());
            return false;
        }
        return true;
    }

    @Override
    public boolean isValid(Block block, Block parent) {
        return isValid(block.getHeader(), parent);
    }
}
