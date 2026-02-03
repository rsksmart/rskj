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
package co.rsk.pte;

import co.rsk.util.HexUtils;
import co.rsk.util.IntegrationTestUtils;
import co.rsk.util.OkHttpClientTestFixture;
import co.rsk.util.cli.CommandLineFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Response;
import org.ethereum.config.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static co.rsk.util.OkHttpClientTestFixture.ETH_GET_BLOCK_BY_NUMBER;
import static co.rsk.util.OkHttpClientTestFixture.FromToAddressPair.of;
import static co.rsk.util.OkHttpClientTestFixture.getEnvelopedMethodCalls;

class PteIntegrationTest {

    /*
        When running this test locally, don't forget to build the .jar for the code you're trying to
        test ('./gradlew clean' and './gradlew assemble' should be sufficient for most cases).
     */

    private static final int MAX_BLOCKS_TO_GET = 20;
    private static final int BULK_TRANSACTION_BATCHES = 5;
    private static final int MIN_EXPECTED_TX_COUNT = 20;
    private static final long MIN_TX_GAS = 21_000L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String buildLibsPath;
    private String jarName;
    private String strBaseArgs;
    private String baseJavaCmd;
    private int rpcPort;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        String projectPath = System.getProperty("user.dir");
        buildLibsPath = String.format("%s/build/libs", projectPath);
        String integrationTestResourcesPath = String.format("%s/src/integrationTest/resources", projectPath);
        String logbackXmlFile = String.format("%s/logback.xml", integrationTestResourcesPath);
        String rskConfFile = String.format("%s/pte-integration-test-rskj.conf", integrationTestResourcesPath);
        rpcPort = IntegrationTestUtils.findFreePort();
        Stream<Path> pathsStream = Files.list(Paths.get(buildLibsPath));
        jarName = pathsStream.filter(p -> !p.toFile().isDirectory())
                .map(p -> p.getFileName().toString())
                .filter(fn -> fn.endsWith("-all.jar"))
                .findFirst()
                .orElse("");

        Path databaseDirPath = tempDir.resolve("database");
        String databaseDir = databaseDirPath.toString();
        String[] baseArgs = new String[]{
                String.format("-Xdatabase.dir=%s", databaseDir),
                "--regtest",
                String.format("-Xrpc.providers.web.http.port=%s", rpcPort)
        };
        strBaseArgs = String.join(" ", baseArgs);
        baseJavaCmd = String.format("java %s %s", String.format("-Dlogback.configurationFile=%s", logbackXmlFile), String.format("-Drsk.conf.file=%s", rskConfFile));
    }

    @Test
    void whenParallelizableTransactionsAreSent_someAreExecutedInParallel() throws Exception {
        // Given

        List<Response> txResponses = new ArrayList<>();
        Map<String, Map<Integer, String>> blocksResponseMap =  new HashMap<>();

        String cmd = String.format(
                "%s -cp %s/%s co.rsk.Start --reset %s",
                baseJavaCmd,
                buildLibsPath,
                jarName,
                strBaseArgs);

        // When

        CommandLineFixture.runCommand(
                cmd,
                1,
                TimeUnit.MINUTES,
                proc -> {
                    try {

                        // Send bulk transactions

                        List<String> accounts = OkHttpClientTestFixture.PRE_FUNDED_ACCOUNTS;
                        OkHttpClientTestFixture.FromToAddressPair[] pairs = new OkHttpClientTestFixture.FromToAddressPair[]{
                                of(accounts.get(0), accounts.get(1)),
                                of(accounts.get(0), accounts.get(2)),
                                of(accounts.get(3), accounts.get(4)),
                                of(accounts.get(4), accounts.get(5)),
                                of(accounts.get(6), accounts.get(7)),
                                of(accounts.get(8), accounts.get(9))
                        };

                        for (int i = 0; i < BULK_TRANSACTION_BATCHES; i++) {
                            txResponses.add(OkHttpClientTestFixture.sendBulkTransactions(rpcPort, pairs));
                        }

                        // Await for n blocks to be mined and return them

                        Future<Map<Integer, String>> future = getBlocksAsync();

                        try {

                            blocksResponseMap.put("asyncBlocksResult", future.get());

                        } catch (ExecutionException | InterruptedException e) {
                            Assertions.fail(e);
                        }

                    } catch (IOException e) {
                        Assertions.fail(e);
                    }
                }
        );

        // Then

        for (Response response : txResponses) {
            Assertions.assertEquals(200, response.code());
        }

        Map<Integer, String> blocksResult = blocksResponseMap.get("asyncBlocksResult");
        Assertions.assertEquals(MAX_BLOCKS_TO_GET, blocksResult.size());

        boolean pteFound = false;
        int i = blocksResult.size(); // Start from the last element (optimization)
        while (!pteFound && i >= 0) {
            i -= 1;

            JsonNode blockResponse = objectMapper.readTree(blocksResult.get(i));
            JsonNode result = blockResponse.get(0).get("result");

            if (result.isNull()) {
                continue;
            }

            JsonNode pteEdges = result.get("rskPteEdges");
            if (!pteEdges.isArray() || pteEdges.isEmpty()) {
                // Skip blocks without PTE metadata; we only validate blocks with edges.
                continue;
            }

            JsonNode transactions = result.get("transactions");
            // Ensure we have full transaction objects, not just hashes.
            Assertions.assertTrue(transactions.isArray());
            int txCount = transactions.size();
            // Require enough transactions to make PTE validation meaningful.
            Assertions.assertTrue(txCount >= MIN_EXPECTED_TX_COUNT);

            int parallelSublistCount = pteEdges.size();
            // Each edge corresponds to a parallel sublist; size should match thread count.
            Assertions.assertEquals(Constants.getTransactionExecutionThreads(), parallelSublistCount);

            int previousEdge = 0;
            for (JsonNode edgeNode : pteEdges) {
                int edge = edgeNode.asInt();
                // Edges are cumulative end indices, so they must be strictly increasing
                Assertions.assertTrue(edge > previousEdge);
                // Edges must not exceed the total transaction count
                Assertions.assertTrue(edge <= txCount);
                previousEdge = edge;
            }

            int parallelTxCount = previousEdge;
            int sequentialTxCount = txCount - parallelTxCount;
            // Verify both parallel and sequential sublists processed transactions.
            Assertions.assertTrue(parallelTxCount > 0);
            Assertions.assertTrue(sequentialTxCount > 0);

            long gasUsed = parseQuantity(result.get("gasUsed"));
            long gasLimit = parseQuantity(result.get("gasLimit"));
            // Gas used should be non-zero and within the block limit
            Assertions.assertTrue(gasUsed > 0);
            Assertions.assertTrue(gasUsed <= gasLimit);
            int minTxCountForGas = Math.min(txCount, MIN_EXPECTED_TX_COUNT / 2);
            long minExpectedGas = MIN_TX_GAS * minTxCountForGas;
            Assertions.assertTrue(
                    // Expect a sensible minimum gas consumption for the tx executed.
                    gasUsed >= minExpectedGas,
                    String.format("gasUsed=%d below expected minimum=%d for txCount=%d", gasUsed, minExpectedGas, txCount)
            );

            pteFound = true;
        }

        Assertions.assertTrue(pteFound);

    }

    private Response getBlockByNumber(String number) throws IOException {
        String content = getEnvelopedMethodCalls(
                ETH_GET_BLOCK_BY_NUMBER.replace(
                        "<BLOCK_NUM_OR_TAG>",
                        number)
        );

        return OkHttpClientTestFixture.sendJsonRpcMessage(content, rpcPort);
    }

    private Future<Map<Integer, String>> getBlocksAsync() {
        CompletableFuture<Map<Integer, String>> completableFuture = new CompletableFuture<>();
        ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        Map<Integer, String> results = new ConcurrentHashMap<>();
        AtomicInteger index = new AtomicInteger(0);

        scheduler.scheduleWithFixedDelay(() -> {
            if (completableFuture.isDone()) {
                scheduler.shutdown();
                return;
            }

            int i = index.getAndIncrement();
            if (i >= MAX_BLOCKS_TO_GET) {
                completableFuture.complete(results);
                scheduler.shutdown();
                return;
            }

            Response response = null;
            try {
                response = getBlockByNumber("0x" + String.format("%02x", i));
                results.put(i, response.body().string());
            } catch (Exception e) {
                completableFuture.completeExceptionally(e);
                scheduler.shutdown();
            } finally {
                if (response != null && response.body() != null) {
                    try {
                        response.body().close();
                    } catch (IOException e) {
                        completableFuture.completeExceptionally(e);
                        scheduler.shutdown();
                    }
                }
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

        return completableFuture;
    }

    private long parseQuantity(JsonNode node) {
        return HexUtils.jsonHexToLong(node.asText());
    }
}
