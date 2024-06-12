package misc;

import co.rsk.util.HexUtils;
import co.rsk.util.OkHttpClientTestFixture;
import co.rsk.util.cli.CommandLineFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String buildLibsPath;
    private String jarName;
    private String databaseDir;
    private String bloomsDbDir;
    private String[] baseArgs;
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
                .get();
        Path databaseDirPath = tempDir.resolve("database");
        databaseDir = databaseDirPath.toString();
        bloomsDbDir = databaseDirPath.resolve("blooms").toString();
        baseArgs = new String[]{
                String.format("-Xdatabase.dir=%s", databaseDir),
                "--regtest",
                "-Xkeyvalue.datasource=leveldb",
                String.format("-Xrpc.providers.web.http.port=%s", RPC_PORT)
        };
        strBaseArgs = String.join(" ", baseArgs);
        baseJavaCmd = String.format("java %s %s", String.format("-Dlogback.configurationFile=%s", logbackXmlFile), String.format("-Drsk.conf.file=%s", rskConfFile));
    }

    @Test
    void whenTest_shouldTest() throws Exception {

        /*
            Regtest's Pre-funded Test Accounts

            0xcd2a3d9f938e13cd947ec05abc7fe734df8dd826
            0x7986b3df570230288501eea3d890bd66948c9b79
            0x0a3aa774752ec2042c46548456c094a76c7f3a79
            0xcf7cdbbb5f7ba79d3ffe74a0bba13fc0295f6036
            0x39b12c05e8503356e3a7df0b7b33efa4c054c409
            0xc354d97642faa06781b76ffb6786f72cd7746c97
            0xdebe71e1de41fc77c44df4b6db940026e31b0e71
            0x7857288e171c6159c5576d1bd9ac40c0c48a771c
            0xa4dea4d5c954f5fd9e87f0e9752911e83a3d18b3
            0x09a1eda29f664ac8f68106f6567276df0c65d859
            0xec4ddeb4380ad69b3e509baad9f158cdf4e4681d
        */

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

        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);

        CommandLineFixture.runCommand(
                cmd,
                1,
                TimeUnit.MINUTES,
                proc -> {
                    try {

                        // Check balances

                        List<String> balances = new ArrayList<>();

                        for (String account : accounts) {
                            balances.add(getBalance(account).body().string());
                        }

                        Assertions.assertEquals(null, balances);

                        // Send bulk transactions

                        Response txResponse = sendBulkTransactions(
                                "0xcd2a3d9f938e13cd947ec05abc7fe734df8dd826",
                                "0x7986b3df570230288501eea3d890bd66948c9b79",
                                "0x0a3aa774752ec2042c46548456c094a76c7f3a79",
                                "0xcf7cdbbb5f7ba79d3ffe74a0bba13fc0295f6036",
                                "0x39b12c05e8503356e3a7df0b7b33efa4c054c409",
                                "0xc354d97642faa06781b76ffb6786f72cd7746c97",
                                "0xdebe71e1de41fc77c44df4b6db940026e31b0e71",
                                "0x7857288e171c6159c5576d1bd9ac40c0c48a771c"
                        );
                        txResponseMap.put("txResponse", txResponse);

                        // Await for n blocks to be mined and return them

                        Future<Map<Integer, String>> future = getBlocksAsync();

                        try {

                            Map<Integer, String> result = future.get();

                            // TODO Check transaction edges on each block

                            Assertions.assertEquals(null, result);

                        } catch (ExecutionException | InterruptedException e) {
                            e.printStackTrace();
                            Assertions.fail(e);
                        }

                    } catch (IOException e) {
                        Assertions.fail(e);
                    }
                }
        );

        /*
        String blockResponseBody = blockResponseMap.get("latestProcessedBlock").body().string();
        JsonNode jsonRpcResponse = objectMapper.readTree(blockResponseBody);
        // JsonNode transactionsNode = jsonRpcResponse.get(0).get("result").get("transactions");
        JsonNode transactionsNode = jsonRpcResponse.get(0).get("result");

        Assertions.assertEquals("", transactionsNode);
        */

        /*
        long blockNumber = HexUtils.jsonHexToLong(transactionsNode.get(0).get("blockNumber").asText());

        Assertions.assertEquals(null , txResponseMap);
        Assertions.assertEquals(1, blockNumber);
        */
    }

    private Response getBalance(String account) throws IOException {
        String content = "[{\n" +
                "    \"method\": \"eth_getBalance\",\n" +
                "    \"params\": [\n" +
                "        \"" + account + "\",\n" +
                "        \"latest\"\n" +
                "    ],\n" +
                "    \"id\": 1,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "}]";

        return OkHttpClientTestFixture.sendJsonRpcMessage(content, RPC_PORT);
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

    private Response sendBulkTransactions(String address1, String address2,
                                          String address3, String address4,
                                          String address5, String address6,
                                          String address7, String address8) throws IOException {
        String content = "[\n" +
                "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"eth_sendTransaction\",\n" +
                "    \"id\": 1,\n" +
                "    \"params\": [{\n" +
                "        \"from\": \"" + address1 + "\",\n" +
                "        \"to\": \"" + address2 + "\",\n" +
                "        \"gas\": \"0x9C40\",\n" +
                "        \"gasPrice\": \"0x1\",\n" +
                "        \"value\": \"0x500\"\n" +
                "    }]\n" +
                "},\n" +
                "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"eth_sendTransaction\",\n" +
                "    \"id\": 1,\n" +
                "    \"params\": [{\n" +
                "        \"from\": \"" + address3 + "\",\n" +
                "        \"to\": \"" + address4 + "\",\n" +
                "        \"gas\": \"0x9C40\",\n" +
                "        \"gasPrice\": \"0x1\",\n" +
                "        \"value\": \"0x500\"\n" +
                "    }]\n" +
                "},\n" +
                "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"eth_sendTransaction\",\n" +
                "    \"id\": 1,\n" +
                "    \"params\": [{\n" +
                "        \"from\": \"" + address5 + "\",\n" +
                "        \"to\": \"" + address6 + "\",\n" +
                "        \"gas\": \"0x9C40\",\n" +
                "        \"gasPrice\": \"0x1\",\n" +
                "        \"value\": \"0x500\"\n" +
                "    }]\n" +
                "},\n" +
                "{\n" +
                "    \"jsonrpc\": \"2.0\",\n" +
                "    \"method\": \"eth_sendTransaction\",\n" +
                "    \"id\": 1,\n" +
                "    \"params\": [{\n" +
                "        \"from\": \"" + address7 + "\",\n" +
                "        \"to\": \"" + address8 + "\",\n" +
                "        \"gas\": \"0x9C40\",\n" +
                "        \"gasPrice\": \"0x1\",\n" +
                "        \"value\": \"0x500\"\n" +
                "    }]\n" +
                "}\n" +
                "]";

        return OkHttpClientTestFixture.sendJsonRpcMessage(content, RPC_PORT);
    }

    private Future<Map<Integer, String>> getBlocksAsync() {
        CompletableFuture<Map<Integer, String>> completableFuture = new CompletableFuture<>();

        Executors.newCachedThreadPool().submit(() -> {
            Map<Integer, String> results = new HashMap<>();

            for (int i = 0; i < 9; i++) {
                String response = getBlockByNumber("0x" + i).body().string();

                results.put(i, response);
                Thread.sleep(500);
            }

            completableFuture.complete(results);
            return null;
        });

        return completableFuture;
    }

}

/*

    Map<String, Response> blockResponseMap = new HashMap<>();
    blockResponseMap.put("latestProcessedBlock", response);

    String blockResponseBody = blockResponseMap.get("latestProcessedBlock").body().string();
    JsonNode jsonRpcResponse = objectMapper.readTree(blockResponseBody);
    JsonNode block = jsonRpcResponse.get(0).get("result");
    JsonNode txs = block.get("transactions");

    String number = "";
    for (int i = 0; i < 5; i++) {
        System.out.println(i);
        Response response = getBestBlock();
        Map<String, Response> blockResponseMap = new HashMap<>();
        blockResponseMap.put("latestProcessedBlock", response);

        String blockResponseBody = blockResponseMap.get("latestProcessedBlock").body().string();
        JsonNode jsonRpcResponse = objectMapper.readTree(blockResponseBody);
        // JsonNode transactionsNode = jsonRpcResponse.get(0).get("result").get("transactions");
        JsonNode block = jsonRpcResponse.get(0).get("result");
        JsonNode txs = block.get("transactions");

        if (!number.equals(block.get("number").asText())) {
            number = block.get("number").asText();
            System.out.println("different");
            System.out.println(number);
        } else {
            System.out.println("same");
        }

        if (txs.size() > 1) {
            Assertions.assertEquals("", txs);
        }

        // Sleep here, or wait for n blocks and then assert
     }
 */
