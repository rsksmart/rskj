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
package pte;

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
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class PteIntegrationTest {

    // THIS TEST REQUIRES A JAR FILE TO BE IN THE CORRECT PLACE BEFORE RUNNING

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
    public void setup() throws IOException {
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

        // Pre-funded Test Accounts on Regtest
        List<String> accounts = Arrays.asList(
                "0xcd2a3d9f938e13cd947ec05abc7fe734df8dd826",
                "0x7986b3df570230288501eea3d890bd66948c9b79",
                "0x0a3aa774752ec2042c46548456c094a76c7f3a79",
                "0xcf7cdbbb5f7ba79d3ffe74a0bba13fc0295f6036",
                "0x39b12c05e8503356e3a7df0b7b33efa4c054c409",
                "0xc354d97642faa06781b76ffb6786f72cd7746c97",
                "0xdebe71e1de41fc77c44df4b6db940026e31b0e71",
                "0x7857288e171c6159c5576d1bd9ac40c0c48a771c",
                "0xa4dea4d5c954f5fd9e87f0e9752911e83a3d18b3",
                "0x09a1eda29f664ac8f68106f6567276df0c65d859",
                "0xec4ddeb4380ad69b3e509baad9f158cdf4e4681d"
        );

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

                        Response txResponse = sendBulkTransactions(
                                accounts.get(0), accounts.get(1), accounts.get(2), accounts.get(3),
                                accounts.get(4), accounts.get(5), accounts.get(6), accounts.get(7));

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

    }

    private Response getBlockByNumber(String number) throws IOException {
        String content = "[{\n" +
                "    \"method\": \"eth_getBlockByNumber\",\n" +
                "    \"params\": [\n" +
                "        \"" + number + "\",\n" +
                "        true\n" +
                "    ],\n" +
                "    \"id\": 1,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "}]";

        return OkHttpClientTestFixture.sendJsonRpcMessage(content, RPC_PORT);
    }

    private Future<Map<Integer, String>> getBlocksAsync() {
        CompletableFuture<Map<Integer, String>> completableFuture = new CompletableFuture<>();

        Executors.newCachedThreadPool().submit(() -> {
            Map<Integer, String> results = new HashMap<>();

            for (int i = 0; i < MAX_BLOCKS_TO_GET; i++) {
                String response = getBlockByNumber("0x" + String.format("%02x", i)).body().string();

                results.put(i, response);
                Thread.sleep(500);
            }

            completableFuture.complete(results);
            return null;
        });

        return completableFuture;
    }

    private Response sendBulkTransactions(
            String addressFrom1, String addressTo1,
            String addressFrom2, String addressTo2,
            String addressFrom3, String addressTo3,
            String addressFrom4, String addressTo4) throws IOException {

        String content = "[\n" +
                "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"eth_sendTransaction\",\n" +
                "    \"id\": 1,\n" +
                "    \"params\": [{\n" +
                "        \"from\": \"" + addressFrom1 + "\",\n" +
                "        \"to\": \"" + addressTo1 + "\",\n" +
                "        \"gas\": \"0x9C40\",\n" +
                "        \"gasPrice\": \"0x10\",\n" +
                "        \"value\": \"0x500\"\n" +
                "    }]\n" +
                "},\n" +
                "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"eth_sendTransaction\",\n" +
                "    \"id\": 1,\n" +
                "    \"params\": [{\n" +
                "        \"from\": \"" + addressFrom2 + "\",\n" +
                "        \"to\": \"" + addressTo2 + "\",\n" +
                "        \"gas\": \"0x9C40\",\n" +
                "        \"gasPrice\": \"0x10\",\n" +
                "        \"value\": \"0x500\"\n" +
                "    }]\n" +
                "},\n" +
                "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"eth_sendTransaction\",\n" +
                "    \"id\": 1,\n" +
                "    \"params\": [{\n" +
                "        \"from\": \"" + addressFrom3 + "\",\n" +
                "        \"to\": \"" + addressTo3 + "\",\n" +
                "        \"gas\": \"0x9C40\",\n" +
                "        \"gasPrice\": \"0x10\",\n" +
                "        \"value\": \"0x500\"\n" +
                "    }]\n" +
                "},\n" +
                "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"eth_sendTransaction\",\n" +
                "    \"id\": 1,\n" +
                "    \"params\": [{\n" +
                "        \"from\": \"" + addressFrom4 + "\",\n" +
                "        \"to\": \"" + addressTo4 + "\",\n" +
                "        \"gas\": \"0x9C40\",\n" +
                "        \"gasPrice\": \"0x10\",\n" +
                "        \"value\": \"0x500\"\n" +
                "    }]\n" +
                "}\n" +
                "]";

        return OkHttpClientTestFixture.sendJsonRpcMessage(content, RPC_PORT);
    }

}