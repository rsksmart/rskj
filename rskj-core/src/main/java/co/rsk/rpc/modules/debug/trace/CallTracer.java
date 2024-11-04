/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package co.rsk.rpc.modules.debug.trace;

import co.rsk.rpc.modules.debug.TraceOptions;
import com.fasterxml.jackson.databind.JsonNode;

public class CallTracer implements DebugTracer {

    public static final String UNSUPPORTED_OPERATION = "Operation not supported by this tracer.";

    @Override
    public JsonNode traceTransaction(String transactionHash, TraceOptions traceOptions) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION);
    }

    @Override
    public JsonNode traceBlockByHash(String blockHash, TraceOptions traceOptions) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION);

    }

    @Override
    public JsonNode traceBlockByNumber(String bnOrId, TraceOptions traceOptions) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION);

    }

    @Override
    public TracerType getTracerType() {
        return TracerType.CALL_TRACER;
    }
}
