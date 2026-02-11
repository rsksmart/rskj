package co.rsk.util.rpc;

import co.rsk.util.OkHttpClientTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Response;
import java.util.Optional;

public final class ContractCaller {

    private static final ObjectMapper OM = new ObjectMapper();

    // Defaults (tweak if you want)
    private static final String DEFAULT_GAS = "0x7a120";     // 500_000
    private static final String DEFAULT_GAS_PRICE = "0x1";   // 1
    private static final String DEFAULT_VALUE = "0x0";

    private final int rpcPort;
    private final String contractAddr;

    // eth_sendTransaction template with data
    private static final String ETH_SEND_TX_WITH_DATA = """
            {
              "jsonrpc":"2.0",
              "method":"eth_sendTransaction",
              "id":1,
              "params":[{
                "from":"<FROM>",
                "to":"<TO>",
                "gas":"<GAS>",
                "gasPrice":"<GAS_PRICE>",
                "value":"<VALUE>",
                "data":"<DATA>"
              }]
            }
            """;

    public ContractCaller(int rpcPort, String contractAddr) {
        if (contractAddr == null || contractAddr.isBlank()) {
            throw new IllegalArgumentException("contractAddr is required");
        }
        this.rpcPort = rpcPort;
        this.contractAddr = contractAddr;
    }

    /**
     * Sends a contract call and enforces success (throws on RPC error).
     */
    public Optional<String> call(String from, String dataHex) throws Exception {
        return call(from, dataHex, DEFAULT_GAS, DEFAULT_GAS_PRICE, DEFAULT_VALUE, true);
    }

    /**
     * Same as call(), but ignores failures (best-effort).
     */
    public Optional<String> callIgnoreFailure(String from, String dataHex) throws Exception {
        try {
            return call(from, dataHex, DEFAULT_GAS, DEFAULT_GAS_PRICE, DEFAULT_VALUE, false);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<String> call(
            String from,
            String dataHex,
            String gasHex,
            String gasPriceHex,
            String valueHex,
            boolean failOnError
    ) throws Exception {

        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("from is required");
        }
        if (dataHex == null || dataHex.isBlank()) {
            throw new IllegalArgumentException("dataHex is required");
        }

        String payload = ETH_SEND_TX_WITH_DATA
                .replace("<FROM>", from)
                .replace("<TO>", contractAddr)
                .replace("<GAS>", gasHex)
                .replace("<GAS_PRICE>", gasPriceHex)
                .replace("<VALUE>", valueHex)
                .replace("<DATA>", dataHex.startsWith("0x") ? dataHex : ("0x" + dataHex));

        Response resp = OkHttpClientTestFixture.sendJsonRpcMessage(payload, rpcPort);
        try {
            if (resp.code() != 200) {
                if (failOnError) {
                    throw new IllegalStateException("HTTP " + resp.code());
                }
                return Optional.empty();
            }

            String body = resp.body() != null ? resp.body().string() : "";
            JsonNode json = OM.readTree(body);

            JsonNode err = json.get("error");
            if (err != null && !err.isNull()) {
                if (failOnError) {
                    throw new IllegalStateException("RPC error: " + err);
                }
                return Optional.empty();
            }

            JsonNode result = json.get("result");
            if (result == null || result.isNull() || result.asText().isBlank()) {
                if (failOnError) {
                    throw new IllegalStateException("Missing tx hash in RPC response");
                }
                return Optional.empty();
            }

            return Optional.of(result.asText());

        } finally {
            if (resp.body() != null) {
                resp.body().close();
            }
        }
    }
}
