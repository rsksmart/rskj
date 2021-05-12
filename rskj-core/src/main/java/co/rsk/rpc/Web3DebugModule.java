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
import co.rsk.rpc.docs.annotation.JsonRpcDoc;
import co.rsk.rpc.docs.annotation.JsonRpcDocRequestParameter;
import co.rsk.rpc.docs.annotation.JsonRpcDocResponse;

import java.util.Map;

public interface Web3DebugModule {

    default String debug_wireProtocolQueueSize() {
        return getDebugModule().wireProtocolQueueSize();
    }

    default JsonNode debug_traceTransaction(String transactionHash) throws Exception {
        return debug_traceTransaction(transactionHash, null);
    }

    @JsonRpcDoc(
            description = "The traceTransaction debugging method will attempt to run the transaction in the exact same" +
                    " manner as it was executed on the network. It will replay any transaction that may have been executed" +
                    " prior to this one before it will finally attempt to execute the transaction that corresponds to the given hash.",
            summary = "Executes a new message call immediately without creating a transaction on the block chain.",
            isWriteMethod = false,
            requestExamples = {"debug_traceTransaction.yaml/request/default"},
            requestParams = {
                    @JsonRpcDocRequestParameter(
                            name = "transactionHash",
                            description = "**TAG** - the hash of the transaction"
                    ),
                    @JsonRpcDocRequestParameter(
                            name = "traceOptions",
                            description = "While the input parameter of type JSON {string: string} is allowed, the params are ignored"
                    )
            },
            responses = {
                    @JsonRpcDocResponse(
                            description = "**DATA** - the return value of executed contract.",
                            code = "Success",
                            examplePath = "debug_traceTransaction.yaml/response/success"
                    ),
                    @JsonRpcDocResponse(
                            description = "Method parameters invalid.",
                            code = "-32602",
                            examplePath = "generic.yaml/response/methodInvalid",
                            success = false
                    ),
                    @JsonRpcDocResponse(
                            description = "Something unexpected happened.",
                            code = "-32603",
                            examplePath = "generic.yaml/response/internalServerError",
                            success = false
                    )
            }
    )
    default JsonNode debug_traceTransaction(String transactionHash, Map<String, String> traceOptions) throws Exception {
        return getDebugModule().traceTransaction(transactionHash, traceOptions);
    }

    DebugModule getDebugModule();
}
