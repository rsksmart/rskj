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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static co.rsk.util.OkHttpClientTestFixture.ETH_GET_BLOCK_BY_NUMBER;
import static co.rsk.util.OkHttpClientTestFixture.FromToAddressPair.of;
import static co.rsk.util.OkHttpClientTestFixture.getEnvelopedMethodCalls;

class PteIntegrationTest {

    /*
        When running this test locally, don't forget to build the .jar for the code you're trying to
        test ('./gradlew clean' and './gradlew assemble' should be sufficient for most cases).
     */

    private static final int RPC_PORT = 9999;
    private static final int MAX_BLOCKS_TO_GET = 20;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        String rskConfFile = String.format("%s/pte-integration-test-rskj.conf", integrationTestResourcesPath);
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
                String.format("-Xrpc.providers.web.http.port=%s", RPC_PORT)
        };
        strBaseArgs = String.join(" ", baseArgs);
        baseJavaCmd = String.format("java %s %s", String.format("-Dlogback.configurationFile=%s", logbackXmlFile), String.format("-Drsk.conf.file=%s", rskConfFile));
    }

    @Test
    void whenParallelizableTransactionsAreSent_someAreExecutedInParallel() throws Exception {
        // Given

        Map<String, Response> txResponseMap = new HashMap<>();
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
                        Response txResponse = OkHttpClientTestFixture.sendBulkTransactions(
                                RPC_PORT,
                                of(accounts.get(0), accounts.get(1)),
                                of(accounts.get(2), accounts.get(3)),
                                of(accounts.get(4), accounts.get(5)),
                                of(accounts.get(6), accounts.get(7)));

                        txResponseMap.put("bulkTransactionsResponse", txResponse);

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

        Assertions.assertEquals(200, txResponseMap.get("bulkTransactionsResponse").code());

        Map<Integer, String> blocksResult = blocksResponseMap.get("asyncBlocksResult");
        Assertions.assertEquals(MAX_BLOCKS_TO_GET, blocksResult.size());

        boolean pteFound = false;
        int i = blocksResult.size(); // Start from the last element (optimization)
        while (!pteFound && i >= 0) {
            i -= 1;

            JsonNode blockResponse = objectMapper.readTree(blocksResult.get(i));
            JsonNode result = blockResponse.get(0).get("result");

            if (!result.isNull()) {
                JsonNode pteEdges = result.get("rskPteEdges");
                if (pteEdges.isArray() && pteEdges.size() > 0) {
                    Assertions.assertTrue(result.get("transactions").isArray());
                    Assertions.assertTrue(result.get("transactions").size() > 1);
                    pteFound = true;
                }
            }
        }

        Assertions.assertTrue(pteFound);
        txResponseMap.values().forEach(r -> {
            try {
                r.body().close();
            } catch (IOException e) {
                Assertions.fail(e);
            }
        });
    }

    private Response getBlockByNumber(String number) throws IOException {
        String content = getEnvelopedMethodCalls(
                ETH_GET_BLOCK_BY_NUMBER.replace(
                        "<BLOCK_NUM_OR_TAG>",
                        number)
        );

        return OkHttpClientTestFixture.sendJsonRpcMessage(content, RPC_PORT);
    }

    private Future<Map<Integer, String>> getBlocksAsync() {
        CompletableFuture<Map<Integer, String>> completableFuture = new CompletableFuture<>();

        Executors.newCachedThreadPool().submit(() -> {
            Map<Integer, String> results = new HashMap<>();

            for (int i = 0; i < MAX_BLOCKS_TO_GET; i++) {
                String response = OkHttpClientTestFixture.responseBody(getBlockByNumber("0x" + String.format("%02x", i)));

                results.put(i, response);
                Thread.sleep(500);
            }

            completableFuture.complete(results);
            return null;
        });

        return completableFuture;
    }
}
