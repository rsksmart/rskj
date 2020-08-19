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

package co.rsk.net;

import co.rsk.core.bc.BlockUtils;
import co.rsk.crypto.Keccak256;
import co.rsk.util.FormatUtils;
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

/**
 * Created by mario on 07/02/17.
 */
public class BlockProcessResult {

    private static final Logger logger = LoggerFactory.getLogger("messagehandler");
    public static final Duration LOG_TIME_LIMIT = Duration.ofSeconds(1);

    private boolean additionalValidationsOk = false;

    private Map<Keccak256, ImportResult> result;

    public BlockProcessResult(boolean additionalValidations, Map<Keccak256, ImportResult> result, String blockHash, Duration processingTime) {
        this.additionalValidationsOk = additionalValidations;
        this.result = result;
        if (processingTime.compareTo(LOG_TIME_LIMIT) >= 0) {
            logResult(blockHash, processingTime);
        }
    }

    public boolean wasBlockAdded(Block block) {
        return additionalValidationsOk && !result.isEmpty() && importOk(result.get(block.getHash()));
    }

    public static boolean importOk(ImportResult blockResult) {
        return blockResult != null
                && (blockResult == ImportResult.IMPORTED_BEST || blockResult == ImportResult.IMPORTED_NOT_BEST);
    }

    public boolean isBest() {
        if (result == null) {
            return false;
        }
        return result.containsValue(ImportResult.IMPORTED_BEST);
    }

    public boolean isInvalidBlock() {
        return result != null && this.result.containsValue(ImportResult.INVALID_BLOCK);
    }

    private void logResult(String blockHash, Duration processingTime) {
        if(result == null || result.isEmpty()) {
            long processTime =  processingTime.toNanos();

            if (BlockUtils.tooMuchProcessTime(processTime)) {
                logger.warn("[MESSAGE PROCESS] Block[{}] After[{}] seconds, process result. No block connections were made", FormatUtils.formatNanosecondsToSeconds(processTime), blockHash);
            }
            else {
                logger.debug("[MESSAGE PROCESS] Block[{}] After[{}] seconds, process result. No block connections were made", FormatUtils.formatNanosecondsToSeconds(processTime), blockHash);
            }
        } else {
            StringBuilder sb = new StringBuilder("[MESSAGE PROCESS] Block[")
                    .append(blockHash).append("] After[").append(FormatUtils.formatNanosecondsToSeconds(processingTime.toNanos())).append("] nano, process result. Connections attempts: ").append(result.size()).append(" | ");

            for(Map.Entry<Keccak256, ImportResult> entry : this.result.entrySet()) {
                sb.append(entry.getKey().toString()).append(" - ").append(entry.getValue()).append(" | ");
            }
            logger.debug(sb.toString());
        }
    }
}
