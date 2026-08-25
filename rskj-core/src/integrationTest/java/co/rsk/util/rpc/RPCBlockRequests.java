package co.rsk.util.rpc;
/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import co.rsk.util.HexUtils;
import co.rsk.util.OkHttpClientTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Response;

import java.io.IOException;

public final class RPCBlockRequests {

    private static final String EVM_MINE = """
            {
                "jsonrpc": "2.0",
                "method": "evm_mine",
                "params": [],
                "id": 1
            }
            """;

    /**
     * Mines exactly one block on demand via the {@code evm_mine} RPC. Used to make
     * mining deterministic: callers submit all transactions into the pending pool
     * and then trigger a single block that contains them.
     */
    public static void mineBlock(int rpcPort) throws IOException {
        Response response = OkHttpClientTestFixture.sendJsonRpcMessage(EVM_MINE, rpcPort);
        try {
            if (response.code() != 200) {
                throw new IOException("evm_mine failed: HTTP " + response.code());
            }
            JsonNode json = new ObjectMapper().readTree(response.body().string());
            JsonNode error = json.get("error");
            if (error != null && !error.isNull()) {
                throw new IOException("evm_mine RPC error: " + error);
            }
        } finally {
            if (response.body() != null) {
                response.body().close();
            }
        }
    }

    public static long getLatestBlockNumber(int rpcPort) throws IOException {
        JsonNode response = OkHttpClientTestFixture
                .getJsonResponseForGetBestBlockMessage(rpcPort, "latest");

        if (response == null || !response.isArray() || response.isEmpty()) {
            throw new IOException("Invalid RPC response: expected non-empty JSON array");
        }

        JsonNode resultNode = response.get(0).path("result");
        JsonNode numberNode = resultNode.path("number");

        if (numberNode.isMissingNode() || numberNode.isNull()) {
            throw new IOException("Invalid RPC response: missing block number");
        }

        String blockHex = numberNode.asText();
        return HexUtils.jsonHexToLong(blockHex);
    }
}
