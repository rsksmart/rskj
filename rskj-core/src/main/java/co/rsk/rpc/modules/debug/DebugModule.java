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

package co.rsk.rpc.modules.debug;

import co.rsk.net.handler.quota.TxQuota;
import co.rsk.rpc.modules.debug.trace.TracerType;
import com.fasterxml.jackson.databind.JsonNode;

public interface DebugModule {

    String wireProtocolQueueSize();

    JsonNode traceTransaction(String transactionHash) throws Exception;

    JsonNode traceTransaction(String transactionHash, TraceOptions traceOptions, TracerType tracerType) throws Exception;

    JsonNode traceBlockByHash(String blockHash, TraceOptions traceOptions, TracerType tracerType) throws Exception;
    JsonNode traceBlockByHash(String blockHash) throws Exception;

    JsonNode traceBlockByNumber(String bnOrId, TraceOptions traceOptions, TracerType tracerType) throws Exception;

    TxQuota accountTransactionQuota(String address);
}
