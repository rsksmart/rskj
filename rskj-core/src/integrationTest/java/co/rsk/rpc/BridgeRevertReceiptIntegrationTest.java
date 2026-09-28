/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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

import co.rsk.cli.RskCli;
import co.rsk.config.ConfigLoader;
import co.rsk.config.RskSystemProperties;
import co.rsk.util.OkHttpClientTestFixture;
import co.rsk.util.RpcTransactionAssertions;
import co.rsk.util.rpc.ContractCaller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Response;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static co.rsk.util.OkHttpClientTestFixture.ETH_BLOCK_NUMBER;
import static co.rsk.util.OkHttpClientTestFixture.PRE_FUNDED_ACCOUNTS;
import static co.rsk.util.OkHttpClientTestFixture.sendHealthProbe;
import static co.rsk.util.OkHttpClientTestFixture.sendJsonRpcMessage;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end receipt check for a failing direct call to a precompiled contract after RSKIP-692.
 *
 * <p>A transaction sent directly to the Bridge with an unknown 4-byte selector ({@code 0xdeadbeef}) makes the
 * Bridge throw, for any caller. With RSKIP-692 active, the receipt must report failure, the whole gas limit
 * must be consumed, and no logs may be emitted. The same call through {@code eth_call} must return a
 * JSON-RPC error.
 */
class BridgeRevertReceiptIntegrationTest {

    private static final String BRIDGE_ADDRESS = "0x0000000000000000000000000000000001000006";
    private static final String UNKNOWN_SELECTOR = "0xdeadbeef";
    private static final String GAS_1M = "0xf4240";
    private static final String GAS_PRICE = "0x1";
    private static final String VALUE_ZERO = "0x0";
    private static final String ZERO_LOGS_BLOOM = "0x" + "0".repeat(512);

    private static final String ETH_CALL_TO_BRIDGE = """
            {
                "jsonrpc": "2.0",
                "method": "eth_call",
                "id": 1,
                "params": [{
                    "from": "<FROM>",
                    "to": "<TO>",
                    "data": "<DATA>",
                    "gas": "<GAS>"
                }, "latest"]
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private int rpcPort;
    private String buildLibsPath;
    private String jarName;
    private String strBaseArgs;
    private String baseJavaCmd;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        // Allocate a free port per test to avoid collisions under parallel Gradle forks
        try (ServerSocket socket = new ServerSocket(0)) {
            rpcPort = socket.getLocalPort();
        }

        String projectPath = System.getProperty("user.dir");
        buildLibsPath = String.format("%s/build/libs", projectPath);
        String integrationTestResourcesPath = String.format("%s/src/integrationTest/resources", projectPath);
        String logbackXmlFile = String.format("%s/logback.xml", integrationTestResourcesPath);
        String rskConfFile = String.format("%s/integration-test-rskj.conf", integrationTestResourcesPath);
        try (Stream<Path> pathsStream = Files.list(Paths.get(buildLibsPath))) {
            jarName = pathsStream.filter(p -> !p.toFile().isDirectory())
                    .map(p -> p.getFileName().toString())
                    .filter(fn -> fn.endsWith("-all.jar"))
                    .findFirst()
                    .orElse("");
        }

        Path databaseDirPath = tempDir.resolve("database");
        String databaseDir = databaseDirPath.toString();
        String[] baseArgs = new String[]{
                String.format("-Xdatabase.dir=%s", databaseDir),
                "--regtest",
                String.format("-Xrpc.providers.web.http.port=%s", rpcPort)
        };
        strBaseArgs = String.join(" ", baseArgs);
        baseJavaCmd = String.format("java %s %s",
                String.format("-Dlogback.configurationFile=%s", logbackXmlFile),
                String.format("-Drsk.conf.file=%s", rskConfFile));
    }

    // RSKIP-692 test case 1 (receipt) and test case 11 (eth_call)
    @Test
    void failingDirectCallToBridge_reportsFailedReceiptAndConsumesGasLimit() throws Exception {
        assertRskip692ActiveFromGenesisOnRegtest();

        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s",
                baseJavaCmd, buildLibsPath, jarName, strBaseArgs);

        Process proc = startNode(cmd);
        try {
            waitForNodeReady(rpcPort, 60_000);

            ContractCaller bridge = new ContractCaller(rpcPort, BRIDGE_ADDRESS);
            String txHash = bridge.call(
                    PRE_FUNDED_ACCOUNTS.get(0),
                    UNKNOWN_SELECTOR,
                    GAS_1M,
                    GAS_PRICE,
                    VALUE_ZERO,
                    true
            ).orElseThrow(() -> new AssertionError("Expected the transaction to be accepted into the pool"));

            RpcTransactionAssertions.assertMined(rpcPort, 50, 200, txHash);

            JsonNode receipt = OkHttpClientTestFixture
                    .getJsonResponseForGetTransactionReceipt(rpcPort, txHash)
                    .get("result");

            Assertions.assertEquals("0x0", receipt.get("status").asText(), "receipt status");
            Assertions.assertEquals(GAS_1M, receipt.get("gasUsed").asText(), "receipt gasUsed must equal the gas limit");
            JsonNode logs = receipt.get("logs");
            Assertions.assertTrue(logs != null && logs.isArray() && logs.isEmpty(), "Expected no logs, got " + logs);
            Assertions.assertEquals(ZERO_LOGS_BLOOM, receipt.get("logsBloom").asText(), "receipt logsBloom");

            String ethCallPayload = ETH_CALL_TO_BRIDGE
                    .replace("<FROM>", PRE_FUNDED_ACCOUNTS.get(0))
                    .replace("<TO>", BRIDGE_ADDRESS)
                    .replace("<DATA>", UNKNOWN_SELECTOR)
                    .replace("<GAS>", GAS_1M);
            String ethCallBody = sendJsonRpcMessage(ethCallPayload, rpcPort).body().string();
            JsonNode ethCallResponse = objectMapper.readTree(ethCallBody);

            Assertions.assertFalse(ethCallResponse.has("result"), "Expected no eth_call result, got: " + ethCallBody);
            Assertions.assertTrue(ethCallResponse.has("error"), "Expected an eth_call error, got: " + ethCallBody);
            String errorMessage = ethCallResponse.get("error").get("message").asText();
            Assertions.assertTrue(errorMessage.endsWith("execution failed"), "eth_call error message: " + errorMessage);
            // The exception raised by the Bridge is logged by the node, never returned to the caller
            Assertions.assertFalse(errorMessage.contains("Invalid data given"), "eth_call error message: " + errorMessage);
        } finally {
            destroyNode(proc);
        }
    }

    /**
     * The node under test runs with {@code --regtest}. This reads the same regtest configuration in-process and
     * checks that RSKIP-692 is active from the first block, so the assertions above exercise the activated rule.
     */
    private static void assertRskip692ActiveFromGenesisOnRegtest() {
        RskCli rskCli = new RskCli();
        rskCli.load(new String[]{"--regtest"});
        RskSystemProperties regtestProperties = new RskSystemProperties(new ConfigLoader(rskCli.getCliArgs()));
        ActivationConfig activationConfig = regtestProperties.getActivationConfig();

        Assertions.assertTrue(activationConfig.isActive(ConsensusRule.RSKIP692, 0),
                "This test requires RSKIP692 to be active from genesis on regtest");
    }

    private Process startNode(String cmd) throws IOException {
        Process proc = Runtime.getRuntime().exec(cmd);
        // Drain stdout and stderr so the subprocess doesn't block on a full pipe buffer.
        drainStreamInBackground(proc.getInputStream(), "node-stdout");
        drainStreamInBackground(proc.getErrorStream(), "node-stderr");
        return proc;
    }

    private void drainStreamInBackground(InputStream stream, String threadName) {
        Thread drainer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                while (reader.readLine() != null) {
                    // discard output; we only need to keep the pipe flowing
                }
            } catch (IOException e) {
                // stream closed when the process is destroyed; expected
            }
        }, threadName);
        drainer.setDaemon(true);
        drainer.start();
    }

    private void destroyNode(Process proc) {
        proc.destroy();
        try {
            proc.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (proc.isAlive()) {
            proc.destroyForcibly();
        }
    }

    private void waitForNodeReady(int port, long maxWaitMs) {
        long start = System.currentTimeMillis();

        await().atMost(maxWaitMs, TimeUnit.MILLISECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .ignoreExceptions()
                .alias("Node RPC did not become available within " + maxWaitMs + "ms")
                .until(() -> sendHealthProbe(port, 2000).code() == 200);

        long remainingMs = maxWaitMs - (System.currentTimeMillis() - start);
        await().atMost(remainingMs, TimeUnit.MILLISECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .ignoreExceptions()
                .alias("Node did not mine first block within " + maxWaitMs + "ms")
                .until(() -> {
                    Response response = sendJsonRpcMessage(ETH_BLOCK_NUMBER, port, 2000);
                    String body = response.body().string();
                    JsonNode json = objectMapper.readTree(body);
                    if (json.has("result")) {
                        long blockNum = Long.parseLong(json.get("result").asText().substring(2), 16);
                        return blockNum >= 1;
                    }
                    return false;
                });
    }
}
