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
import com.google.common.annotations.VisibleForTesting;
import java.time.Instant;
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

/**
 * Created by mario on 07/02/17.
 */
public class BlockProcessResult {

    private static final Logger logger = LoggerFactory.getLogger("messagehandler");
    private static final Duration LOG_TIME_LIMIT = Duration.ofSeconds(1);

    private final boolean additionalValidationsOk;
    private final Map<Keccak256, ImportResult> result;

    protected BlockProcessResult(boolean additionalValidations, Map<Keccak256, ImportResult> result, String blockHash, Duration processingTime) {
        this.additionalValidationsOk = additionalValidations;
        this.result = result;

        if (processingTime.compareTo(LOG_TIME_LIMIT) >= 0) {
            logResult(blockHash, processingTime);
        }
    }

    public static BlockProcessResult ignoreBlockResult(Block block, Instant start) {
        return new BlockProcessResult(false, null, block.getPrintableHash(), Duration.between(start, Instant.now()));
    }

    public static BlockProcessResult connectResult(Block block, Instant start, Map<Keccak256, ImportResult> connectResult) {
        return new BlockProcessResult(true, connectResult, block.getPrintableHash(), Duration.between(start, Instant.now()));
    }

    public boolean isScheduledForProcessing() {
        return additionalValidationsOk && result == null;
    }

    public boolean wasBlockAdded(Block block) {
        return additionalValidationsOk && result != null && !result.isEmpty() && importOk(result.get(block.getHash()));
    }

    public static boolean importOk(@Nullable ImportResult blockResult) {
        return blockResult == ImportResult.IMPORTED_BEST || blockResult == ImportResult.IMPORTED_NOT_BEST;
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
        String message = buildLogMessage(blockHash, processingTime, result);

        if (BlockUtils.tooMuchProcessTime(processingTime.getNano())) {
            logger.warn(message);
        }
        else {
            logger.debug(message);
        }
    }

    @VisibleForTesting
    public static String buildLogMessage(String blockHash, Duration processingTime, Map<Keccak256, ImportResult> result) {
        StringBuilder sb = new StringBuilder("[MESSAGE PROCESS] Block[")
                .append(blockHash)
                .append("] After[")
                .append(FormatUtils.formatNanosecondsToSeconds(processingTime.toNanos()))
                .append("] seconds, process result.");

        if (result == null || result.isEmpty()) {
            sb.append(" No block connections were made");
        } else {
            sb.append(" Connections attempts: ")
                    .append(result.size())
                    .append(" | ");

            for (Map.Entry<Keccak256, ImportResult> entry : result.entrySet()) {
                sb.append(entry.getKey().toString())
                        .append(" - ")
                        .append(entry.getValue())
                        .append(" | ");
            }
        }

        return sb.toString();
    }
}
