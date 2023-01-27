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

import co.rsk.core.bc.BlockExecutor;
import co.rsk.panic.PanicProcessor;
import org.ethereum.core.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule to check that extra data length is less than the maximum accepted size for it.
 *
 * Created by martin.medina on 07/02/17.
 */
public class ExtraDataRule implements BlockValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private int maximumExtraDataSize;

    public ExtraDataRule(int maximumExtraDataSize) {
        this.maximumExtraDataSize = maximumExtraDataSize;
    }

    @Override
    public boolean isValid(Block block, BlockExecutor blockExecutor) {
        if (block.getHeader().getExtraData() != null && block.getHeader().getExtraData().length > this.maximumExtraDataSize) {

            String logMessage = String.format("#%d: header.getExtraData().length > MAXIMUM_EXTRA_DATA_SIZE", block.getHeader().getNumber());
            logger.warn(logMessage);
            panicProcessor.panic("invalidExtraData", logMessage);

            return false;
        }

        return true;
    }
}
