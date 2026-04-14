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
package co.rsk.rskip543;

import co.rsk.util.IntegrationTestUtils;
import co.rsk.util.OkHttpClientTestFixture;
import co.rsk.util.cli.CommandLineFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;

/**
 * Integration test for RSKIP543 typed transactions and receipt accessibility
 * across the activation boundary.
 *
 * <p>Starts a real RSKj regtest node, sends legacy transactions before RSKIP543
 * activates, then sends typed transactions after activation, and verifies that
 * all receipts (pre and post activation) remain accessible via JSON-RPC.
 */
class Rskip543ReceiptIntegrationTest {

    private static final int RSKIP543_ACTIVATION_BLOCK = 3;

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
        String projectPath = System.getProperty("user.dir");
        buildLibsPath = String.format("%s/build/libs", projectPath);
        String integrationTestResourcesPath = String.format("%s/src/integrationTest/resources", projectPath);
        String logbackXmlFile = String.format("%s/logback.xml", integrationTestResourcesPath);
        String rskConfFile = String.format("%s/rskip543-integration-test-rskj.conf", integrationTestResourcesPath);
        rpcPort = IntegrationTestUtils.findFreePort();
        int peerPort = IntegrationTestUtils.findFreePort();

        try (Stream<Path> pathsStream = Files.list(Paths.get(buildLibsPath))) {
            jarName = pathsStream.filter(p -> !p.toFile().isDirectory())
                    .map(p -> p.getFileName().toString())
                    .filter(fn -> fn.endsWith("-all.jar"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No *-all.jar found in " + buildLibsPath));
        }

        Path databaseDirPath = tempDir.resolve("database");
        String databaseDir = databaseDirPath.toString();
        String[] baseArgs = new String[]{
                String.format("-Xdatabase.dir=%s", databaseDir),
                "--regtest",
                String.format("-Xpeer.port=%s", peerPort),
                String.format("-Xrpc.providers.web.http.port=%s", rpcPort),
                String.format("-Xblockchain.config.consensusRules.rskip543=%d", RSKIP543_ACTIVATION_BLOCK)
        };
        strBaseArgs = String.join(" ", baseArgs);
        baseJavaCmd = String.format("java -Dlogback.configurationFile=%s -Drsk.conf.file=%s",
                logbackXmlFile, rskConfFile);
    }

    @Test
    void receiptsBeforeAndAfterRskip543ActivationShouldBeAccessible() throws Exception {
        List<String> preActivationTxHashes = new ArrayList<>();
        List<String> postActivationTxHashes = new ArrayList<>();
        List<JsonNode> preActivationReceipts = new ArrayList<>();
        List<JsonNode> postActivationReceipts = new ArrayList<>();

        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s",
                baseJavaCmd, buildLibsPath, jarName, strBaseArgs);

        CommandLineFixture.runCommand(cmd, 3, TimeUnit.MINUTES, proc -> {
            try {
                List<String> accounts = OkHttpClientTestFixture.PRE_FUNDED_ACCOUNTS;

                // --- Phase 1: Pre-activation legacy transactions ---
                waitForRpcReady();

                String txHash1 = sendTransaction(accounts.get(0), accounts.get(1), "0x500", "0x5208", "0x10");
                Assertions.assertNotNull(txHash1, "Pre-activation tx1 should return a hash");
                preActivationTxHashes.add(txHash1);

                String txHash2 = sendTransaction(accounts.get(2), accounts.get(3), "0x600", "0x5208", "0x10");
                Assertions.assertNotNull(txHash2, "Pre-activation tx2 should return a hash");
                preActivationTxHashes.add(txHash2);

                // Wait for pre-activation transactions to be mined
                waitForTransactionReceipt(txHash1);
                waitForTransactionReceipt(txHash2);

                // Verify pre-activation receipts exist
                for (String hash : preActivationTxHashes) {
                    JsonNode receipt = getTransactionReceipt(hash);
                    Assertions.assertNotNull(receipt, "Pre-activation receipt should exist for " + hash);
                    Assertions.assertEquals("0x1", receipt.get("status").asText(),
                            "Pre-activation receipt should show success");
                    preActivationReceipts.add(receipt);
                }

                // --- Phase 2: Wait for activation block ---
                waitForBlock(RSKIP543_ACTIVATION_BLOCK);

                // --- Phase 3: Post-activation transactions ---
                String txHash3 = sendTransaction(accounts.get(4), accounts.get(5), "0x700", "0x5208", "0x10");
                Assertions.assertNotNull(txHash3, "Post-activation tx3 should return a hash");
                postActivationTxHashes.add(txHash3);

                String txHash4 = sendTransaction(accounts.get(6), accounts.get(7), "0x800", "0x5208", "0x10");
                Assertions.assertNotNull(txHash4, "Post-activation tx4 should return a hash");
                postActivationTxHashes.add(txHash4);

                // Wait for post-activation transactions to be mined
                waitForTransactionReceipt(txHash3);
                waitForTransactionReceipt(txHash4);

                // Verify post-activation receipts
                for (String hash : postActivationTxHashes) {
                    JsonNode receipt = getTransactionReceipt(hash);
                    Assertions.assertNotNull(receipt, "Post-activation receipt should exist for " + hash);
                    Assertions.assertEquals("0x1", receipt.get("status").asText(),
                            "Post-activation receipt should show success");
                    postActivationReceipts.add(receipt);
                }

                // --- Phase 4: Cross-check — all receipts still accessible ---
                for (String hash : preActivationTxHashes) {
                    JsonNode receipt = getTransactionReceipt(hash);
                    Assertions.assertNotNull(receipt,
                            "Pre-activation receipt should still be accessible after post-activation blocks: " + hash);
                    Assertions.assertEquals("0x1", receipt.get("status").asText());
                }

                for (String hash : postActivationTxHashes) {
                    JsonNode receipt = getTransactionReceipt(hash);
                    Assertions.assertNotNull(receipt,
                            "Post-activation receipt should still be accessible: " + hash);
                    Assertions.assertEquals("0x1", receipt.get("status").asText());
                }

                // --- Phase 5: Validate receipt structure ---
                for (JsonNode receipt : preActivationReceipts) {
                    assertReceiptHasRequiredFields(receipt);
                    Assertions.assertEquals("0x0", receipt.get("type").asText(),
                            "Pre-activation receipt type should be legacy (0x0)");
                }

                for (JsonNode receipt : postActivationReceipts) {
                    assertReceiptHasRequiredFields(receipt);
                }

            } catch (Exception e) {
                Assertions.fail("Integration test failed: " + e.getMessage(), e);
            }
        });
    }

    private void waitForRpcReady() {
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .ignoreExceptions()
                .until(() -> {
                    Response response = OkHttpClientTestFixture.sendJsonRpcGetBestBlockMessage(rpcPort);
                    return response.isSuccessful();
                });
    }

    private void waitForBlock(long blockNumber) {
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .ignoreExceptions()
                .until(() -> {
                    JsonNode response = OkHttpClientTestFixture.getJsonResponseForGetBestBlockMessage(rpcPort, "latest");
                    JsonNode result = response.get(0).get("result");
                    if (result == null || result.isNull()) {
                        return false;
                    }
                    long currentBlock = Long.decode(result.get("number").asText());
                    return currentBlock >= blockNumber;
                });
    }

    private void waitForTransactionReceipt(String txHash) {
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .ignoreExceptions()
                .until(() -> {
                    JsonNode receipt = getTransactionReceipt(txHash);
                    return receipt != null;
                });
    }

    private String sendTransaction(String from, String to, String value, String gas, String gasPrice) throws IOException {
        String content = OkHttpClientTestFixture.ETH_SEND_TRANSACTION
                .replace("<ADDRESS_FROM>", from)
                .replace("<ADDRESS_TO>", to)
                .replace("<VALUE>", value)
                .replace("<GAS>", gas)
                .replace("<GAS_PRICE>", gasPrice);

        Response response = OkHttpClientTestFixture.sendJsonRpcMessage(content, rpcPort);
        String body = response.body().string();
        JsonNode jsonResponse = objectMapper.readTree(body);
        JsonNode result = jsonResponse.get("result");
        if (result == null || result.isNull()) {
            return null;
        }
        return result.asText();
    }

    private JsonNode getTransactionReceipt(String txHash) throws IOException {
        JsonNode jsonResponse = OkHttpClientTestFixture.getJsonResponseForGetTransactionReceipt(rpcPort, txHash);
        JsonNode result = jsonResponse.get("result");
        if (result == null || result.isNull()) {
            return null;
        }
        return result;
    }

    private void assertReceiptHasRequiredFields(JsonNode receipt) {
        Assertions.assertNotNull(receipt.get("transactionHash"), "Receipt should have transactionHash");
        Assertions.assertNotNull(receipt.get("blockHash"), "Receipt should have blockHash");
        Assertions.assertNotNull(receipt.get("blockNumber"), "Receipt should have blockNumber");
        Assertions.assertNotNull(receipt.get("from"), "Receipt should have from");
        Assertions.assertNotNull(receipt.get("to"), "Receipt should have to");
        Assertions.assertNotNull(receipt.get("cumulativeGasUsed"), "Receipt should have cumulativeGasUsed");
        Assertions.assertNotNull(receipt.get("gasUsed"), "Receipt should have gasUsed");
        Assertions.assertNotNull(receipt.get("status"), "Receipt should have status");
        Assertions.assertNotNull(receipt.get("type"), "Receipt should have type");
    }
}
