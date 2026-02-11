package co.rsk.util.rpc;

import co.rsk.util.OkHttpClientTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Response;

public final class ContractDeployer {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final String GAS = "0x5b8d80";      // 6,000,000
    private static final String GAS_PRICE = "0x1";     // 1
    private static final String VALUE_ZERO = "0x0";

    private static final String ETH_SEND_CREATE_TX = """
        {
          "jsonrpc":"2.0",
          "method":"eth_sendTransaction",
          "id":1,
          "params":[{
            "from":"<FROM>",
            "gas":"<GAS>",
            "gasPrice":"<GAS_PRICE>",
            "value":"<VALUE>",
            "data":"<DATA>"
          }]
        }
        """;

    private static final String ETH_GET_RECEIPT = """
        {
          "jsonrpc":"2.0",
          "method":"eth_getTransactionReceipt",
          "id":1,
          "params":["<TX_HASH>"]
        }
        """;

    private ContractDeployer() {
        // utility class
    }

    /**
     * Deploy a contract using eth_sendTransaction (unlocked regtest node).
     *
     * @param rpcPort              JSON-RPC port
     * @param deployerAddress      unlocked account used as "from"
     * @param contractBytecodeHex  compiled bytecode (0x...)
     * @param timeoutMs            max time to wait for receipt
     * @return deployed contract address
     */
    public static String deploy(
            int rpcPort,
            String deployerAddress,
            String contractBytecodeHex,
            long timeoutMs
    ) throws Exception {

        if (deployerAddress == null || deployerAddress.isBlank()) {
            throw new IllegalArgumentException("deployerAddress is required");
        }
        if (contractBytecodeHex == null || contractBytecodeHex.isBlank() || contractBytecodeHex.equals("0x")) {
            throw new IllegalArgumentException("contractBytecodeHex is empty");
        }

        String bytecode = contractBytecodeHex.startsWith("0x")
                ? contractBytecodeHex
                : "0x" + contractBytecodeHex;

        // 1) send contract creation transaction
        String payload = ETH_SEND_CREATE_TX
                .replace("<FROM>", deployerAddress)
                .replace("<GAS>", GAS)
                .replace("<GAS_PRICE>", GAS_PRICE)
                .replace("<VALUE>", VALUE_ZERO)
                .replace("<DATA>", bytecode);

        String txHash;

        Response eth_sendTransactionResponse = OkHttpClientTestFixture.sendJsonRpcMessage(payload, rpcPort);
        assertHttpOk(eth_sendTransactionResponse, "eth_sendTransaction (deploy)");
        JsonNode eth_sendTransactionJSONResponse = parseJson(eth_sendTransactionResponse);
        txHash = extractResult(eth_sendTransactionJSONResponse, "deploy tx hash");

        // 2) poll for receipt
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String receiptPayload = ETH_GET_RECEIPT.replace("<TX_HASH>", txHash);

            Response eth_getTransactionReceiptResponse = OkHttpClientTestFixture.sendJsonRpcMessage(receiptPayload, rpcPort);
            assertHttpOk(eth_getTransactionReceiptResponse, "eth_getTransactionReceipt");
            JsonNode eth_getTransactionReceiptJSONResponse = parseJson(eth_getTransactionReceiptResponse);
            JsonNode result = eth_getTransactionReceiptJSONResponse.get("result");
            if (result != null && !result.isNull()) {
                JsonNode addr = result.get("contractAddress");
                if (addr != null && !addr.isNull() && !addr.asText().isBlank()) {
                    return addr.asText();
                }
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Timed out waiting for deployment receipt. tx=" + txHash);
    }

    private static void assertHttpOk(com.squareup.okhttp.Response resp, String label) {
        if (resp.code() != 200) {
            throw new IllegalStateException(label + " HTTP " + resp.code());
        }
    }

    private static JsonNode parseJson(com.squareup.okhttp.Response resp) throws Exception {
        String body = resp.body() != null ? resp.body().string() : "";
        JsonNode json = OM.readTree(body);
        JsonNode err = json.get("error");
        if (err != null && !err.isNull()) {
            throw new IllegalStateException("RPC error: " + err);
        }
        return json;
    }

    private static String extractResult(JsonNode json, String label) {
        JsonNode result = json.get("result");
        if (result == null || result.isNull() || result.asText().isBlank()) {
            throw new IllegalStateException("Missing " + label + ": " + json);
        }
        return result.asText();
    }
}
