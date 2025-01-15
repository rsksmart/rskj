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

import co.rsk.net.handler.quota.TxQuota;
import co.rsk.rpc.modules.debug.DebugModule;
import com.fasterxml.jackson.databind.JsonNode;
import org.ethereum.rpc.parameters.DebugTracerParam;

@java.lang.SuppressWarnings("squid:S100")
public interface Web3DebugModule {

    default String debug_wireProtocolQueueSize() {
        return getDebugModule().wireProtocolQueueSize();
    }

    default JsonNode debug_traceTransaction(String transactionHash) throws Exception {
        return getDebugModule().traceTransaction(transactionHash);
    }

    default JsonNode debug_traceTransaction(String transactionHash, DebugTracerParam traceParams) throws Exception {
        return getDebugModule().traceTransaction(transactionHash, traceParams.getTraceOptions(), traceParams.getTracerType());
    }

    default JsonNode debug_traceBlockByHash(String blockHash) throws Exception {
        return getDebugModule().traceBlockByHash(blockHash);
    }

    default JsonNode debug_traceBlockByHash(String blockHash, DebugTracerParam debugTracerParam) throws Exception {
        return getDebugModule().traceBlockByHash(blockHash, debugTracerParam.getTraceOptions(), debugTracerParam.getTracerType());
    }

    default JsonNode debug_traceBlockByNumber(String bnOrId) throws Exception {

        return debug_traceBlockByNumber(bnOrId, new DebugTracerParam());
    }

    default JsonNode debug_traceBlockByNumber(String bnOrId, DebugTracerParam debugTracerParam) throws Exception {
        return getDebugModule().traceBlockByNumber(bnOrId, debugTracerParam.getTraceOptions(), debugTracerParam.getTracerType());
    }

    default TxQuota debug_accountTransactionQuota(String address) {
        return getDebugModule().accountTransactionQuota(address);
    }

    DebugModule getDebugModule();
}
