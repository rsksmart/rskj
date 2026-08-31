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

import co.rsk.util.OkHttpClientTestFixture;
import co.rsk.util.RpcTransactionAssertions;
import co.rsk.util.rpc.ContractCaller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Response;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static co.rsk.util.OkHttpClientTestFixture.ETH_BLOCK_NUMBER;
import static co.rsk.util.OkHttpClientTestFixture.PRE_FUNDED_ACCOUNTS;
import static co.rsk.util.OkHttpClientTestFixture.sendHealthProbe;
import static co.rsk.util.OkHttpClientTestFixture.sendJsonRpcMessage;
import static org.awaitility.Awaitility.await;

/**
 * Regression test for receipt status of failing precompile invocations.
 *
 * A transaction whose call to a precompiled contract throws a VMException must
 * produce a receipt with status=0x0 (Failed), matching the behavior of failing
 * EVM-contract calls. Historically the precompile execution path in
 * {@code TransactionExecutor.call()} caught the VMException, stored it on the
 * ProgramResult, but never set the executor's {@code executionError} field —
 * so {@code getReceipt()} wrote SUCCESS_STATUS instead of FAILED_STATUS.
 *
 * Reproduction here uses an unknown 4-byte selector (0xdeadbeef) sent to the
 * Bridge precompile. The Bridge dispatcher throws BridgeIllegalArgumentException
 * (a VMException subtype) for any unrecognized selector regardless of caller
 * authorization, making the test deterministic across regtest configurations.
 */
class BridgeRevertReceiptIntegrationTest {

    private static final String BRIDGE_ADDRESS = "0x0000000000000000000000000000000001000006";
    private static final String BOGUS_SELECTOR = "0xdeadbeef";
    private static final String GAS_1M = "0xf4240";
    private static final String GAS_PRICE = "0x1";
    private static final String VALUE_ZERO = "0x0";

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

    /**
     * Sends a tx to the Bridge precompile with a non-existent selector and
     * asserts the receipt reports {@code status=0x0}.
     *
     * Without the fix in {@code TransactionExecutor.call()}'s precompile catch
     * block, this test fails: the receipt comes back with {@code status=0x1}.
     * With the fix applied, the receipt reports {@code status=0x0} and the
     * test passes.
     */
    @Test
    void failingPrecompileCall_shouldReportFailedReceiptStatus() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s",
                baseJavaCmd, buildLibsPath, jarName, strBaseArgs);

        Process proc = startNode(cmd);
        try {
            waitForNodeReady(rpcPort, 60_000);

            ContractCaller bridge = new ContractCaller(rpcPort, BRIDGE_ADDRESS);
            Optional<String> txHashOpt = bridge.call(
                    PRE_FUNDED_ACCOUNTS.get(0),
                    BOGUS_SELECTOR,
                    GAS_1M,
                    GAS_PRICE,
                    VALUE_ZERO,
                    true);

            String txHash = txHashOpt.orElseThrow(() ->
                    new AssertionError("Expected the bogus-selector tx to be accepted into the pool"));

            RpcTransactionAssertions.assertMined(rpcPort, 50, 200, txHash);

            JsonNode receipt = OkHttpClientTestFixture
                    .getJsonResponseForGetTransactionReceipt(rpcPort, txHash)
                    .get("result");

            String status = receipt.get("status").asText();
            JsonNode logs = receipt.get("logs");

            Assertions.assertEquals(
                    "0x0",
                    status,
                    "Failing precompile call must report receipt status=0x0; "
                            + "got " + status + ". This indicates the missing execError(...) "
                            + "in TransactionExecutor.call()'s precompile catch block. "
                            + "See RSKCORE-5410.");

            // Sanity-check the failure shape: a real revert produces no logs.
            // If logs are present, the call may not have actually failed and the
            // test would be measuring something else.
            Assertions.assertTrue(
                    logs != null && logs.isArray() && logs.size() == 0,
                    "Expected no logs on a failing precompile call; got " + logs);

            System.out.printf("Bridge tx %s — status=%s, logs=%s%n", txHash, status, logs);
        } finally {
            destroyNode(proc);
        }
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
                    // discard
                }
            } catch (IOException e) {
                // expected when the process is destroyed
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
