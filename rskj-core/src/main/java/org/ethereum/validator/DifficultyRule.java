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

package org.ethereum.validator;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.ConsensusValidationMainchainView;
import org.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Checks block's difficulty against calculated difficulty value
 *
 * @author Mikhail Kalinin
 * @since 02.09.2015
 */
public class DifficultyRule extends DependentBlockHeaderRule {

    private static final Logger logger = LoggerFactory.getLogger(DifficultyRule.class);

    private final DifficultyCalculator difficultyCalculator;
    private final ConsensusValidationMainchainView consensusValidationMainchainView;

    public DifficultyRule(DifficultyCalculator difficultyCalculator, ConsensusValidationMainchainView mainchainView) {
        this.difficultyCalculator = difficultyCalculator;
        this.consensusValidationMainchainView = mainchainView;
    }

    @Override
    public boolean validate(BlockHeader header, BlockHeader parent) {
        // todo(fede) this is not production ready
        List<BlockHeader> blockWindow = consensusValidationMainchainView != null ? consensusValidationMainchainView
                .getFromBestBlock(DifficultyCalculator.BLOCK_COUNT_WINDOW) : Collections.emptyList();

        BlockDifficulty calcDifficulty = difficultyCalculator.calcDifficulty(header, parent, blockWindow);
        BlockDifficulty difficulty = header.getDifficulty();

        if (!difficulty.equals(calcDifficulty)) {
            logger.error("#{}: difficulty != calcDifficulty", header.getNumber());
            return false;
        }
        return true;
    }
}
