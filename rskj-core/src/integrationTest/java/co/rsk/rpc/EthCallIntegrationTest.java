/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package co.rsk.rpc;

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
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static co.rsk.util.OkHttpClientTestFixture.*;

class EthCallIntegrationTest {

    // SHA3 loop: JUMPDEST PUSH2(0xFFFF) PUSH1(0) SHA3 POP PUSH1(0) JUMP
    // Hashes 64KB of memory in a tight loop until gas exhaustion.
    // Each iteration costs ~12,338 gas.
    private static final String SHA3_LOOP_BYTECODE = "0x5b61ffff600020506000565b";

    private static final String GAS_10M = "0x989680";
    private static final String GAS_50M = "0x2FAF080";

    // Generous client-side HTTP timeout for expensive calls.
    private static final long EXPENSIVE_CALL_TIMEOUT_MS = 120_000;
    private static final long BATCH_CALL_TIMEOUT_MS = 300_000;

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

    /**
     * Verifies that an eth_call with high gas consumption executes successfully.
     */
    @Test
    void ethCallWithHighGas_shouldExecuteSuccessfully() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s",
                baseJavaCmd, buildLibsPath, jarName, strBaseArgs);

        Process proc = startNode(cmd);
        try {
            waitForNodeReady(rpcPort, 60_000);

            String payload = buildEthCallPayload(SHA3_LOOP_BYTECODE, GAS_10M, 1);

            long start = System.currentTimeMillis();
            Response response = sendJsonRpcMessage(payload, rpcPort, EXPENSIVE_CALL_TIMEOUT_MS);
            long elapsed = System.currentTimeMillis() - start;

            String body = response.body().string();
            JsonNode jsonResponse = objectMapper.readTree(body);
            Assertions.assertTrue(jsonResponse.has("result"),
                    "Expected 'result' field in response, got: " + body);
            Assertions.assertTrue(elapsed >= 200,
                    String.format("Expected complex execution to take >= 200ms, took %dms", elapsed));

            System.out.printf("High gas eth_call execution time: %dms%n", elapsed);
        } finally {
            destroyNode(proc);
        }
    }

    /**
     * Verifies that long-running calls complete successfully under a reasonable time.
     */
    @Test
    void defaultTimeoutZero_shouldCompleteSuccessfully() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s",
                baseJavaCmd, buildLibsPath, jarName, strBaseArgs);

        Process proc = startNode(cmd);
        try {
            waitForNodeReady(rpcPort, 60_000);

            String payload = buildEthCallPayload(SHA3_LOOP_BYTECODE, GAS_50M, 1);

            long start = System.currentTimeMillis();
            Response response = sendJsonRpcMessage(payload, rpcPort, EXPENSIVE_CALL_TIMEOUT_MS);
            long elapsed = System.currentTimeMillis() - start;

            String body = response.body().string();
            JsonNode jsonResponse = objectMapper.readTree(body);
            Assertions.assertTrue(jsonResponse.has("result"),
                    "Expected 'result' (no timeout error), got: " + body);
            Assertions.assertTrue(elapsed < 1000,
                    String.format("Expected execution to take < 1000ms, took %dms", elapsed));

            System.out.printf("Long running eth_call execution time: %dms%n", elapsed);
        } finally {
            destroyNode(proc);
        }
    }

    /**
     * Verifies that the callGasCap configuration parameter limits the execution time of eth_call.
     * When the gas parameter is greater than the callGasCap, the call should be executed with the callGasCap.
     * Execution times for 10M (the default callGasCap) and 50M gas should be similar.
     */
    @Test
    void callGasCap_limitsExecutionTime() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s",
                baseJavaCmd, buildLibsPath, jarName, strBaseArgs);

        Process proc = startNode(cmd);
        try {
            waitForNodeReady(rpcPort, 60_000);

            // Measure eth_call at 10M gas (within cap)
            String payload10M = buildEthCallPayload(SHA3_LOOP_BYTECODE, GAS_10M, 1);
            long start = System.currentTimeMillis();
            Response response10M = sendJsonRpcMessage(payload10M, rpcPort, EXPENSIVE_CALL_TIMEOUT_MS);
            long elapsed10M = System.currentTimeMillis() - start;
            Assertions.assertTrue(response10M.isSuccessful(), "10M gas call should succeed");

            // Measure eth_call at 50M gas (should be clamped to 10M by callGasCap)
            String payload50M = buildEthCallPayload(SHA3_LOOP_BYTECODE, GAS_50M, 2);
            start = System.currentTimeMillis();
            Response response50M = sendJsonRpcMessage(payload50M, rpcPort, EXPENSIVE_CALL_TIMEOUT_MS);
            long elapsed50M = System.currentTimeMillis() - start;
            Assertions.assertTrue(response50M.isSuccessful(), "50M gas call should succeed");

            // Execution time should be similar for 10M and 50M gas.
            Assertions.assertTrue(elapsed50M <= 1.5 * elapsed10M,
                    String.format("50M gas call (%dms) should take at most 1.5x the time of the 10M gas call (%dms).",
                            elapsed50M, elapsed10M));

            System.out.printf("Test: eth_call at 10M gas=%dms, at 50M gas=%dms, ratio=%.1fx%n",
                    elapsed10M, elapsed50M, (double) elapsed50M / elapsed10M);
        } finally {
            destroyNode(proc);
        }
    }

    /**
     * Verifies that the RPC timeout (5s default) interrupts long-running batch execution.
     * Uses a high callGasCap override to make each eth_call expensive (~1-2s), then
     * dynamically sizes the batch so total sequential processing would exceed 5s.
     * The timeout should interrupt the batch before all items complete.
     */
    @Test
    void rpcTimeout_interruptsLongRunningBatch() throws Exception {
        // Override callGasCap to 50M so each eth_call takes ~1-2s.
        // Override max_batch_requests_size to 100 so the batch validator doesn't reject
        // the request before the timeout has a chance to trigger.
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s -Xrpc.callGasCap=50000000 -Xrpc.providers.web.max_batch_requests_size=100",
                baseJavaCmd, buildLibsPath, jarName, strBaseArgs);

        Process proc = startNode(cmd);
        try {
            waitForNodeReady(rpcPort, 60_000);

            // Measure single eth_call time at 50M gas to calibrate batch size
            String singlePayload = buildEthCallPayload(SHA3_LOOP_BYTECODE, GAS_50M, 1);
            long start = System.currentTimeMillis();
            Response singleResponse = sendJsonRpcMessage(singlePayload, rpcPort, EXPENSIVE_CALL_TIMEOUT_MS);
            long singleCallTime = System.currentTimeMillis() - start;
            Assertions.assertTrue(singleResponse.isSuccessful(), "Single eth_call should succeed");

            // Calculate batch size so total sequential time would exceed 5s without timeout.
            // Target 10s for margin. Cap at 100 (overridden max_batch_requests_size).
            int batchSize = Math.max(5, (int) Math.ceil(10_000.0 / singleCallTime));
            batchSize = Math.min(batchSize, 100);
            long expectedSequentialTime = singleCallTime * batchSize;
            System.out.printf("Single call: %dms. Batch size: %d (expected without timeout: ~%ds)%n",
                    singleCallTime, batchSize, expectedSequentialTime / 1000);

            // Build and send batch
            String[] batchItems = new String[batchSize];
            for (int i = 0; i < batchSize; i++) {
                batchItems[i] = buildEthCallPayload(SHA3_LOOP_BYTECODE, GAS_50M, i + 1);
            }
            String batchPayload = getEnvelopedMethodCalls(batchItems);

            start = System.currentTimeMillis();
            Response batchResponse = sendJsonRpcMessage(batchPayload, rpcPort, BATCH_CALL_TIMEOUT_MS);
            long elapsed = System.currentTimeMillis() - start;
            String body = batchResponse.body().string();
            JsonNode jsonResponse = objectMapper.readTree(body);

            // With timeout=5s, the batch is interrupted before all items complete.
            Assertions.assertTrue(jsonResponse.has("error"),
                    "Expected timeout error response, got: " + body);
            Assertions.assertTrue(elapsed <= 10_000,
                    String.format("Batch should be capped by 5s timeout, took %dms", elapsed));

            System.out.printf("Batch of %d interrupted by timeout after %dms%n", batchSize, elapsed);
        } finally {
            destroyNode(proc);
        }
    }

    /**
     * Verifies RPC responsiveness under concurrent individual eth_call requests.
     * Each call is capped at callGasCap (10M gas), so individual calls
     * hold a Netty thread briefly. Brief transient probe failures under peak
     * saturation are expected — the defense against individual-call flooding
     * is network-level rate limiting, not thread availability.
     */
    @Test
    void concurrentHighGasCalls_shouldTestRpcResponsiveness() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s",
                baseJavaCmd, buildLibsPath, jarName, strBaseArgs);

        int totalProbes = 5;

        Process proc = startNode(cmd);
        try {
            waitForNodeReady(rpcPort, 60_000);

            // Verify baseline: health probe should succeed without heavy load
            Response baselineResponse = sendHealthProbe(rpcPort, 5000);
            Assertions.assertEquals(200, baselineResponse.code(),
                    "Baseline health probe should succeed without heavy load");

            // Launch concurrent high-gas calls to stress the RPC layer.
            // Each call is capped at callGasCap (10M, ~0.5s per call).
            // We launch many calls per thread to keep threads saturated during the probe window.
            int numThreads = Runtime.getRuntime().availableProcessors() * 2 + 2;
            int numCalls = numThreads * 12; // ~6s of work per thread at ~0.5s per call
            ExecutorService executor = Executors.newFixedThreadPool(numThreads, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });

            for (int i = 0; i < numCalls; i++) {
                final int id = i;
                executor.submit(() -> {
                    try {
                        String payload = buildEthCallPayload(SHA3_LOOP_BYTECODE, GAS_10M, id);
                        sendJsonRpcMessage(payload, rpcPort, EXPENSIVE_CALL_TIMEOUT_MS);
                    } catch (IOException e) {
                        // Expected — process may be destroyed while calls are in-flight
                    }
                    return null;
                });
            }

            // Wait for calls to process
            Thread.sleep(2000);

            // Send sequential health probes with short timeout.
            // In a normal state, eth_blockNumber responds quickly.
            // Probes failing indicates high load saturation.
            AtomicInteger failedProbes = new AtomicInteger(0);
            for (int i = 0; i < totalProbes; i++) {
                try {
                    Response probeResponse = sendHealthProbe(rpcPort, 2000);
                    if (probeResponse.code() != 200) {
                        failedProbes.incrementAndGet();
                    }
                } catch (IOException e) {
                    // SocketTimeoutException or ConnectException means RPC is unresponsive
                    failedProbes.incrementAndGet();
                }
            }

            executor.shutdownNow();

            Assertions.assertTrue(failedProbes.get() <= 2,
                    String.format("Expected at most 2 out of %d health probes to timeout due to load, but %d failed",
                            totalProbes, failedProbes.get()));

            System.out.printf("Load test: %d out of %d health probes failed during concurrent execution%n",
                    failedProbes.get(), totalProbes);
        } finally {
            destroyNode(proc);
        }
    }

    /**
     * Verifies that batch requests exceeding max_batch_requests_size are rejected.
     * The default limit is 50. A batch of 51 items should be rejected with error code -32600.
     */
    @Test
    void batchExceedingMaxSize_shouldBeRejected() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s",
                baseJavaCmd, buildLibsPath, jarName, strBaseArgs);

        Process proc = startNode(cmd);
        try {
            waitForNodeReady(rpcPort, 60_000);

            // Build a batch of 51 items — one more than the default limit of 50
            int batchSize = 51;
            String[] batchItems = new String[batchSize];
            for (int i = 0; i < batchSize; i++) {
                batchItems[i] = buildEthCallPayload(SHA3_LOOP_BYTECODE, GAS_10M, i + 1);
            }
            String batchPayload = getEnvelopedMethodCalls(batchItems);

            Response batchResponse = sendJsonRpcMessage(batchPayload, rpcPort, EXPENSIVE_CALL_TIMEOUT_MS);
            String body = batchResponse.body().string();
            JsonNode jsonResponse = objectMapper.readTree(body);

            Assertions.assertTrue(jsonResponse.has("error"),
                    "Expected error response for oversized batch, got: " + body);
            Assertions.assertEquals(-32600, jsonResponse.get("error").get("code").asInt(),
                    "Expected error code -32600 (invalid request)");
            Assertions.assertTrue(jsonResponse.get("error").get("message").asText().contains("max number of supported batch requests"),
                    "Expected batch limit error message, got: " + jsonResponse.get("error").get("message").asText());

            System.out.printf("Batch of %d correctly rejected: %s%n", batchSize,
                    jsonResponse.get("error").get("message").asText());
        } finally {
            destroyNode(proc);
        }
    }

    /**
     * Verifies that batch requests are processed sequentially.
     * A batch of N calls should take approximately N * single_call_time.
     */
    @Test
    void batchOfHighGasCalls_shouldProcessSequentially() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s",
                baseJavaCmd, buildLibsPath, jarName, strBaseArgs);

        Process proc = startNode(cmd);
        try {
            waitForNodeReady(rpcPort, 60_000);

            // Measure single eth_call time at 10M gas
            String singlePayload = buildEthCallPayload(SHA3_LOOP_BYTECODE, GAS_10M, 1);
            long start = System.currentTimeMillis();
            Response singleResponse = sendJsonRpcMessage(singlePayload, rpcPort, EXPENSIVE_CALL_TIMEOUT_MS);
            long singleCallTime = System.currentTimeMillis() - start;

            // Send batch of 5 eth_call items at 10M gas each
            int batchSize = 5;
            String[] batchItems = new String[batchSize];
            for (int i = 0; i < batchSize; i++) {
                batchItems[i] = buildEthCallPayload(SHA3_LOOP_BYTECODE, GAS_10M, i + 1);
            }
            String batchPayload = getEnvelopedMethodCalls(batchItems);

            start = System.currentTimeMillis();
            Response batchResponse = sendJsonRpcMessage(batchPayload, rpcPort, BATCH_CALL_TIMEOUT_MS);
            long batchCallTime = System.currentTimeMillis() - start;
            String batchBody = batchResponse.body().string();

            // Verify batch response structure
            JsonNode batchJson = objectMapper.readTree(batchBody);
            Assertions.assertTrue(batchJson.isArray(), "Batch response should be a JSON array");
            Assertions.assertEquals(5, batchJson.size(), "Batch response should have 5 elements");

            // Batch of 5 sequential calls should take at least 3x a single call
            // (using 3x instead of 5x for CI reliability margin)
            Assertions.assertTrue(batchCallTime >= 3 * singleCallTime,
                    String.format("Expected batch time (%dms) >= 3 * single call time (%dms = %dms)",
                            batchCallTime, singleCallTime, 3 * singleCallTime));

            System.out.printf("Batch processing: Single call=%dms, Batch of 5=%dms, Ratio=%.1fx%n",
                    singleCallTime, batchCallTime, (double) batchCallTime / singleCallTime);
        } finally {
            destroyNode(proc);
        }
    }

    /**
     * Verifies system behavior under high concurrency of batch requests.
     * Ensures the RPC layer handles saturation from batch processing gracefully.
     *
     * Each HTTP request carries a batch of high-gas eth_call items. Because batches are
     * processed sequentially on a single thread, each request occupies a thread for
     * batch_size * per_call_time.
     */
    @Test
    void concurrentBatchRequests_shouldTestRpcResponsiveness() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s",
                baseJavaCmd, buildLibsPath, jarName, strBaseArgs);

        int batchSize = 5;
        int totalProbes = 5;

        Process proc = startNode(cmd);
        try {
            waitForNodeReady(rpcPort, 60_000);

            // Verify baseline: health probe succeeds before load test
            long baselineStart = System.currentTimeMillis();
            Response baselineResponse = sendHealthProbe(rpcPort, 5000);
            long baselineElapsed = System.currentTimeMillis() - baselineStart;
            Assertions.assertEquals(200, baselineResponse.code(),
                    "Baseline health probe should succeed before load test");
            System.out.printf("[BASELINE] eth_blockNumber responded in %dms%n", baselineElapsed);

            // Build batch payload: 5 eth_call items at 50M gas each.
            // Each batch occupies one worker thread for ~5 * 1.8s = ~9s.
            String[] batchItems = new String[batchSize];
            for (int i = 0; i < batchSize; i++) {
                batchItems[i] = buildEthCallPayload(SHA3_LOOP_BYTECODE, GAS_50M, i + 1);
            }
            String batchPayload = getEnvelopedMethodCalls(batchItems);

            // Launch concurrent batch requests to stress all worker threads.
            // numBatches > thread count ensures the pool stays saturated even
            // if some batches complete during the probe window.
            int numBatches = Runtime.getRuntime().availableProcessors() * 2 + 4;
            ExecutorService executor = Executors.newFixedThreadPool(numBatches, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });

            long loadTestStart = System.currentTimeMillis();
            for (int i = 0; i < numBatches; i++) {
                executor.submit(() -> {
                    try {
                        sendJsonRpcMessage(batchPayload, rpcPort, BATCH_CALL_TIMEOUT_MS);
                    } catch (IOException e) {
                        // Expected — process may be destroyed while calls are in-flight
                    }
                    return null;
                });
            }

            // Wait for batch calls to start processing
            Thread.sleep(2000);

            // Send sequential health probes with short timeout.
            // In a normal state, eth_blockNumber responds quickly.
            AtomicInteger failedProbes = new AtomicInteger(0);
            List<String> probeResults = new ArrayList<>();
            for (int i = 1; i <= totalProbes; i++) {
                long probeStart = System.currentTimeMillis();
                try {
                    Response probeResponse = sendHealthProbe(rpcPort, 2000);
                    long probeElapsed = System.currentTimeMillis() - probeStart;
                    if (probeResponse.code() == 200) {
                        String result = String.format("[PROBE %d] OK in %dms", i, probeElapsed);
                        probeResults.add(result);
                        System.out.println(result);
                    } else {
                        failedProbes.incrementAndGet();
                        String result = String.format("[PROBE %d] HTTP %d in %dms",
                                i, probeResponse.code(), probeElapsed);
                        probeResults.add(result);
                        System.out.println(result);
                    }
                } catch (IOException e) {
                    long probeElapsed = System.currentTimeMillis() - probeStart;
                    failedProbes.incrementAndGet();
                    String result = String.format("[PROBE %d] FAILED in %dms (%s)",
                            i, probeElapsed, e.getClass().getSimpleName());
                    probeResults.add(result);
                    System.out.println(result);
                }
            }

            long loadTestElapsed = System.currentTimeMillis() - loadTestStart;
            executor.shutdownNow();

            // Print summary
            System.out.println();
            System.out.printf("========== LOAD TEST SUMMARY ==========%n");
            System.out.printf("Concurrent batch requests: %d (each with %d eth_calls at 50M gas)%n",
                    numBatches, batchSize);
            System.out.printf("Total eth_call invocations: %d%n", numBatches * batchSize);
            System.out.printf("Health probes failed: %d / %d%n", failedProbes.get(), totalProbes);
            for (String r : probeResults) {
                System.out.println("  " + r);
            }
            System.out.printf("Wall time: %dms%n", loadTestElapsed);
            System.out.printf("==========================================%n");

            Assertions.assertTrue(failedProbes.get() <= 2,
                    String.format("Expected at most 2 out of %d health probes to fail, but %d failed. "
                                    + "The RPC layer should be saturated during a batch load test.",
                            totalProbes, failedProbes.get()));

            System.out.printf("Batch Load Test: %d out of %d health probes failed during execution%n",
                    failedProbes.get(), totalProbes);
        } finally {
            destroyNode(proc);
        }
    }

    private Process startNode(String cmd) throws IOException {
        Process proc = Runtime.getRuntime().exec(cmd);
        // Drain stdout and stderr in background daemon threads to prevent
        // pipe buffer deadlock. The child process's VM/Gas logging can produce
        // tens of thousands of lines; if the 64KB pipe buffer fills up, the
        // Netty I/O thread blocks on System.out.println() mid-execution.
        drainStreamInBackground(proc.getInputStream(), "node-stdout");
        drainStreamInBackground(proc.getErrorStream(), "node-stderr");
        return proc;
    }

    private void drainStreamInBackground(InputStream stream, String threadName) {
        Thread drainer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                while (reader.readLine() != null) {
                    // discard output — we only need to keep the pipe flowing
                }
            } catch (IOException e) {
                // stream closed when process is destroyed — expected
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

        // Phase 1: Wait for RPC to accept connections
        boolean rpcUp = false;
        while (System.currentTimeMillis() - start < maxWaitMs) {
            try {
                Response response = sendHealthProbe(port, 2000);
                if (response.code() == 200) {
                    rpcUp = true;
                    break;
                }
            } catch (IOException e) {
                // Node not ready yet, keep polling
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (!rpcUp) {
            Assertions.fail("Node RPC did not become available within " + maxWaitMs + "ms");
        }

        // Phase 2: Wait until at least one block is mined, ensuring the node is fully operational
        while (System.currentTimeMillis() - start < maxWaitMs) {
            try {
                Response response = sendJsonRpcMessage(ETH_BLOCK_NUMBER, port, 2000);
                String body = response.body().string();
                JsonNode json = objectMapper.readTree(body);
                if (json.has("result")) {
                    String hexBlock = json.get("result").asText();
                    long blockNum = Long.parseLong(hexBlock.substring(2), 16);
                    if (blockNum >= 1) {
                        System.out.printf("Node ready after %dms (block #%d)%n",
                                System.currentTimeMillis() - start, blockNum);
                        return;
                    }
                }
            } catch (IOException e) {
                // keep polling
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        Assertions.fail("Node did not mine first block within " + maxWaitMs + "ms");
    }
}
