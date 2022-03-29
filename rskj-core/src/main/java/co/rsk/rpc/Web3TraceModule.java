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

import co.rsk.rpc.modules.trace.TraceFilterRequest;
import co.rsk.rpc.modules.trace.TraceModule;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface Web3TraceModule {

    default JsonNode trace_transaction(String transactionHash) throws Exception {
        return getTraceModule().traceTransaction(transactionHash);
    }

    default JsonNode trace_block(String blockHash) throws Exception {
        return getTraceModule().traceBlock(blockHash);
    }

    default JsonNode trace_filter(TraceFilterRequest request) throws Exception {
        return getTraceModule().traceFilter(request);
    }

    default JsonNode trace_get(String transactionHash, List<String> positions) throws Exception {
        return getTraceModule().traceGet(transactionHash, positions);
    }

    TraceModule getTraceModule();
}

