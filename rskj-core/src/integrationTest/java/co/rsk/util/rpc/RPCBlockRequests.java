package co.rsk.util.rpc;

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
