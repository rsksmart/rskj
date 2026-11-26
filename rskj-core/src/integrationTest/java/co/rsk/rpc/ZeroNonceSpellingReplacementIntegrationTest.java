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

import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import co.rsk.util.IntegrationTestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Response;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static co.rsk.util.OkHttpClientTestFixture.sendJsonRpcMessage;
import static org.awaitility.Awaitility.await;

/**
 * Nonce 0 has two RLP spellings in a legacy transaction: 0x80 and the single byte 0x00. Both
 * decode to the same nonce, so on a running node a higher-priced transaction sent with
 * eth_sendRawTransaction replaces a pending one whatever spelling each of them uses.
 */
class ZeroNonceSpellingReplacementIntegrationTest {

    private static final byte REGTEST_CHAIN_ID = 33;
    private static final BigInteger GWEI = BigInteger.TEN.pow(9);
    private static final BigInteger TRANSFER_GAS_LIMIT = BigInteger.valueOf(21_000);
    // the regtest genesis funds the account whose private key is keccak256("cow")
    private static final ECKey FUNDER = keyFromSeed("cow");
    private static final ECKey SENDER = keyFromSeed("zero-nonce-spelling-sender");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private int rpcPort;
    private String nodeCommand;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        rpcPort = IntegrationTestUtils.findFreePort();
        int peerPort = IntegrationTestUtils.findFreePort();

        String projectPath = System.getProperty("user.dir");
        String buildLibsPath = String.format("%s/build/libs", projectPath);
        String integrationTestResourcesPath = String.format("%s/src/integrationTest/resources", projectPath);
        String logbackXmlFile = String.format("%s/logback.xml", integrationTestResourcesPath);
        String rskConfFile = String.format("%s/integration-test-rskj.conf", integrationTestResourcesPath);

        String jarName;
        try (Stream<Path> pathsStream = Files.list(Paths.get(buildLibsPath))) {
            jarName = pathsStream.filter(p -> !p.toFile().isDirectory())
                    .map(p -> p.getFileName().toString())
                    .filter(fn -> fn.endsWith("-all.jar"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No *-all.jar found in " + buildLibsPath));
        }

        // The miner client stays off so that the pending transaction is still in the pool when
        // its replacement arrives; blocks are mined on demand with evm_mine.
        nodeCommand = String.join(" ",
                "java",
                String.format("-Dlogback.configurationFile=%s", logbackXmlFile),
                String.format("-Drsk.conf.file=%s", rskConfFile),
                "-cp", String.format("%s/%s", buildLibsPath, jarName),
                "co.rsk.Start", "--reset", "--regtest",
                String.format("-Xdatabase.dir=%s", tempDir.resolve("database")),
                String.format("-Xpeer.port=%d", peerPort),
                String.format("-Xrpc.providers.web.http.port=%d", rpcPort),
                "-Xminer.client.enabled=false");
    }

    @Test
    void replacementWithNonceSpelledAsZeroByte_replacesPendingCanonicalTransaction() throws Exception {
        Process node = startNode();
        try {
            waitForRpcReady();
            fundSenderForOneTransferAtGasPrice(GWEI.multiply(BigInteger.TWO));

            byte[] pending = signedTransfer(SENDER, BigInteger.ZERO, GWEI, FUNDER, BigInteger.ONE);
            byte[] replacementCanonical = signedTransfer(SENDER, BigInteger.ZERO, GWEI.multiply(BigInteger.TWO), FUNDER, BigInteger.ONE);
            byte[] replacement = withZeroNonceSpelledAsZeroByte(replacementCanonical);

            String pendingHash = keccakHex(pending);
            // The transaction hash is taken over the canonical encoding, whatever spelling arrives.
            String replacementHash = keccakHex(replacementCanonical);

            Assertions.assertEquals(pendingHash, resultOf(sendRawTransaction(pending)));
            Assertions.assertEquals(replacementHash, resultOf(sendRawTransaction(replacement)));

            Assertions.assertTrue(rpc("eth_getTransactionByHash", quoted(pendingHash)).get("result").isNull(),
                    "the replaced transaction must have left the pool");
            Assertions.assertFalse(rpc("eth_getTransactionByHash", quoted(replacementHash)).get("result").isNull(),
                    "the replacement must be in the pool");

            rpc("evm_mine", "");
            JsonNode receipt = waitForReceipt(replacementHash);
            Assertions.assertEquals("0x1", receipt.get("status").asText());
        } finally {
            destroyNode(node);
        }
    }

    // enough for one transfer at gasPrice, not for that transfer plus the one it replaces
    private void fundSenderForOneTransferAtGasPrice(BigInteger gasPrice) throws IOException {
        BigInteger amount = TRANSFER_GAS_LIMIT.multiply(gasPrice).add(BigInteger.ONE);
        BigInteger funderNonce = HexUtils.stringHexToBigInteger(
                rpc("eth_getTransactionCount", quoted(addressOf(FUNDER).toJsonString()) + ",\"pending\"").get("result").asText());

        String fundingHash = resultOf(sendRawTransaction(signedTransfer(FUNDER, funderNonce, BigInteger.ONE, SENDER, amount)));
        rpc("evm_mine", "");
        waitForReceipt(fundingHash);
    }

    private static byte[] signedTransfer(ECKey from, BigInteger nonce, BigInteger gasPrice, ECKey to, BigInteger value) {
        Transaction tx = Transaction.builder()
                .nonce(nonce)
                .gasPrice(gasPrice)
                .gasLimit(TRANSFER_GAS_LIMIT)
                .receiveAddress(addressOf(to))
                .value(value)
                .chainId(REGTEST_CHAIN_ID)
                .build();
        tx.sign(from.getPrivKeyBytes());
        return tx.getEncoded();
    }

    /**
     * Rewrites a signed legacy transaction whose nonce 0 is spelled 0x80 so the nonce is spelled
     * as the single byte 0x00. The signature covers the canonical form, so it stays valid.
     */
    private static byte[] withZeroNonceSpelledAsZeroByte(byte[] signedLegacyTx) {
        int listPrefix = signedLegacyTx[0] & 0xFF;
        int nonceOffset = listPrefix <= 0xF7 ? 1 : 1 + (listPrefix - 0xF7);
        Assertions.assertEquals((byte) 0x80, signedLegacyTx[nonceOffset], "expected nonce 0 encoded as 0x80");

        byte[] rewritten = signedLegacyTx.clone();
        rewritten[nonceOffset] = 0x00;
        return rewritten;
    }

    private JsonNode sendRawTransaction(byte[] rawTx) throws IOException {
        return rpc("eth_sendRawTransaction", quoted(HexUtils.toJsonHex(rawTx)));
    }

    private static String resultOf(JsonNode response) {
        Assertions.assertNull(response.get("error"), () -> "unexpected JSON-RPC error: " + response.get("error"));
        return response.get("result").asText();
    }

    private JsonNode waitForReceipt(String txHash) {
        return await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .ignoreExceptions()
                .until(() -> rpc("eth_getTransactionReceipt", quoted(txHash)).get("result"),
                        result -> result != null && !result.isNull());
    }

    private void waitForRpcReady() {
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .ignoreExceptions()
                .until(() -> rpc("eth_blockNumber", "").has("result"));
    }

    private JsonNode rpc(String method, String params) throws IOException {
        String content = String.format("{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[%s],\"id\":1}", method, params);
        Response response = sendJsonRpcMessage(content, rpcPort);
        try {
            return objectMapper.readTree(response.body().string());
        } finally {
            response.body().close();
        }
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }

    private static String keccakHex(byte[] data) {
        return HexUtils.toJsonHex(HashUtil.keccak256(data));
    }

    private static ECKey keyFromSeed(String seed) {
        return ECKey.fromPrivate(HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8)));
    }

    private static RskAddress addressOf(ECKey key) {
        return new RskAddress(key.getAddress());
    }

    private Process startNode() throws IOException {
        Process proc = Runtime.getRuntime().exec(nodeCommand);
        // drain the node's output so a full pipe buffer cannot block it
        drainInBackground(proc.getInputStream(), "node-stdout");
        drainInBackground(proc.getErrorStream(), "node-stderr");
        return proc;
    }

    private static void drainInBackground(InputStream stream, String threadName) {
        Thread drainer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    // discard
                }
            } catch (IOException e) {
                // the stream closes when the node is destroyed
            }
        }, threadName);
        drainer.setDaemon(true);
        drainer.start();
    }

    private static void destroyNode(Process proc) {
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
}
