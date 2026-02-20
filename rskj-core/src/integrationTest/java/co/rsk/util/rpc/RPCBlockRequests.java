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

import java.io.IOException;

public final class RPCBlockRequests {



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
