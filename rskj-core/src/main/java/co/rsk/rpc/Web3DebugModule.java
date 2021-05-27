/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.rpc;

import co.rsk.rpc.modules.debug.DebugModule;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface Web3DebugModule {

    default String debug_wireProtocolQueueSize() {
        return getDebugModule().wireProtocolQueueSize();
    }

    default JsonNode debug_traceTransaction(String transactionHash) throws Exception {
        return debug_traceTransaction(transactionHash, null);
    }

    default JsonNode debug_traceTransaction(String transactionHash, Map<String, String> traceOptions) throws Exception {
        return getDebugModule().traceTransaction(transactionHash, traceOptions);
    }

    default JsonNode debug_traceBlockByHash(String blockHash) throws Exception {
        return debug_traceBlockByHash(blockHash, null);
    }

    default JsonNode debug_traceBlockByHash(String blockHash, Map<String, String> traceOptions) throws Exception {
        return getDebugModule().traceBlock(blockHash, traceOptions);
    }

    DebugModule getDebugModule();
}
