/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.jmh.web3.e2e;

import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.JsonRpc2_0Web3j;
import org.web3j.protocol.core.Request;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class RskDebugModuleWeb3j extends JsonRpc2_0Web3j {

    public RskDebugModuleWeb3j(Web3jService web3jService) {
        super(web3jService);
    }

    public Request<?, RskModuleWeb3j.GenericJsonResponse> debugTraceTransaction(String txHash) {
        return new Request<>("debug_traceTransaction", Collections.singletonList(txHash), web3jService, RskModuleWeb3j.GenericJsonResponse.class);
    }

    public Request<?, RskModuleWeb3j.GenericJsonResponse> debugTraceTransaction(String txHash, Map<String, String> params) {
        return new Request<>("debug_traceTransaction", Arrays.asList(txHash, params), web3jService, RskModuleWeb3j.GenericJsonResponse.class);
    }

    public Request<?, RskModuleWeb3j.GenericJsonResponse> debugTraceBlockByHash(String txHash) {
        return new Request<>("debug_traceBlockByHash", Collections.singletonList(txHash), web3jService, RskModuleWeb3j.GenericJsonResponse.class);
    }

    public Request<?, RskModuleWeb3j.GenericJsonResponse> debugTraceBlockByHash(String txHash, Map<String, String> params) {
        return new Request<>("debug_traceBlockByHash", Arrays.asList(txHash, params), web3jService, RskModuleWeb3j.GenericJsonResponse.class);
    }

    public Request<?, RskModuleWeb3j.GenericJsonResponse> debugTraceBlockByNumber(String bnOrId) {
        return new Request<>("debug_traceBlockByNumber", Collections.singletonList(bnOrId), web3jService, RskModuleWeb3j.GenericJsonResponse.class);
    }

    public Request<?, RskModuleWeb3j.GenericJsonResponse> debugTraceBlockByNumber(String bnOrId, Map<String, String> params) {
        return new Request<>("debug_traceBlockByNumber", Arrays.asList(bnOrId, params), web3jService, RskModuleWeb3j.GenericJsonResponse.class);
    }
}
