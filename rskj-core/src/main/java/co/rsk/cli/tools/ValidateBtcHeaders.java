/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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
package co.rsk.cli.tools;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.rsk.config.RskMiningConstants;
import co.rsk.util.HexUtils;
import co.rsk.validators.BtcHeaderSizeRule;
import picocli.CommandLine;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Standalone CLI tool that validates the bitcoinMergedMiningHeader size for
 * blocks fetched via JSON-RPC from a remote RSK node. Writes invalid blocks to an
 * output file.
 *
 * The BTC merged mining header must be exactly 80 bytes for all non-genesis
 * blocks and when the RSKIP98 isn't active.
 */
@CommandLine.Command(name = "validate-btc-headers", mixinStandardHelpOptions = true, version = "validate-btc-headers 1.0", description = "Validates bitcoinMergedMiningHeader size for blocks fetched via JSON-RPC.", footer = {
        "",
        "Usage examples:",
        "",
        "  Validate all mainnet blocks (from block 1 to latest):",
        "    java -cp rskj-core.jar co.rsk.cli.tools.ValidateBtcHeaders -n mainnet -f invalid-blocks.csv",
        "",
        "  Validate a specific block range on testnet:",
        "    java -cp rskj-core.jar co.rsk.cli.tools.ValidateBtcHeaders -n testnet -f invalid-blocks.csv -fb 1 -tb 1000000",
        "",
        "  Use a custom RPC endpoint with a larger batch size:",
        "    java -cp rskj-core.jar co.rsk.cli.tools.ValidateBtcHeaders -n mainnet -u http://my-node:4444 -f out.csv -bs 200",
        ""
})
public class ValidateBtcHeaders implements Callable<Integer>, BtcHeaderSizeRule {

    private static final Logger logger = LoggerFactory.getLogger("clitool");

    private static final int PROGRESS_INTERVAL = 10000;
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final String MAINNET_RPC_URL = "https://public-node.rsk.co";
    private static final String TESTNET_RPC_URL = "https://public-node.testnet.rsk.co";
    private static final long MAINNET_RSKIP98_ACTIVATION_HEIGHT = 729_000;
    private static final long TESTNET_RSKIP98_ACTIVATION_HEIGHT = 0;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final String CSV_HEADER = "blockNumber,btcHeaderByteCount,btcHeaderHex";

    @CommandLine.Option(names = { "-n",
            "--network" }, description = "Network to connect to: mainnet or testnet", required = true)
    private String network;

    @CommandLine.Option(names = { "-u", "--rpcUrl" }, description = "Custom RPC URL (overrides --network default)")
    private String rpcUrl;

    @CommandLine.Option(names = { "-f",
            "--file" }, description = "Path to the output file for invalid blocks", required = true)
    private String filePath;

    @CommandLine.Option(names = { "-fb", "--fromBlock" }, description = "From block number (default: 1)")
    private Long fromBlockNumber;

    @CommandLine.Option(names = { "-tb", "--toBlock" }, description = "To block number (default: latest block)")
    private Long toBlockNumber;

    @CommandLine.Option(names = { "-bs",
            "--batchSize" }, description = "Number of blocks to fetch per RPC batch request (default: ${DEFAULT-VALUE})")
    private int batchSize = DEFAULT_BATCH_SIZE;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private long rskip98ActivationHeight;
    private int invalidCount;
    private int progressInvalidCount;
    private long progressStart;

    public ValidateBtcHeaders() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ValidateBtcHeaders()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        String resolvedUrl = resolveRpcUrl();
        rskip98ActivationHeight = resolveRskip98ActivationHeight();
        logger.info("Using RPC endpoint: {}", resolvedUrl);
        logger.info("RSKIP98 activation height for {}: {}", network, rskip98ActivationHeight);

        long from = fromBlockNumber != null ? fromBlockNumber : 1;
        long to = toBlockNumber != null ? toBlockNumber : fetchLatestBlockNumber(resolvedUrl);

        try (PrintStream writer = new PrintStream(new BufferedOutputStream(new FileOutputStream(filePath)))) {
            writer.println(CSV_HEADER);
            validateBlocks(resolvedUrl, from, to, writer);
            printSummary(from, to);
        }

        return 0;
    }

    private void validateBlocks(String url, long from, long to, PrintStream writer)
            throws IOException, InterruptedException {
        logger.info("Starting BTC header validation from block {} to block {} (batch size: {})", from, to, batchSize);

        invalidCount = 0;
        progressInvalidCount = 0;
        progressStart = from;

        for (long batchFrom = from; batchFrom <= to; batchFrom += batchSize) {
            long batchTo = Math.min(batchFrom + batchSize - 1, to);
            JsonNode[] results = fetchBlocks(url, batchFrom, batchTo);
            processBatchResults(results, batchFrom, writer);
        }

        printCurrentProgress(to);
    }

    private void processBatchResults(JsonNode[] results, long batchFrom, PrintStream writer) {
        for (int i = 0; i < results.length; i++) {
            long blockNumber = batchFrom + i;
            JsonNode blockResult = results[i];

            if (isBlockMissing(blockResult, blockNumber)) {
                continue;
            }

            byte[] headerBytes = extractBtcHeader(blockResult);
            String headerHex = extractBtcHeaderHex(blockResult);

            boolean isRskip98Active = blockNumber >= rskip98ActivationHeight;
            if (!isValidBtcMergedMiningHeaderSize(headerBytes, blockNumber, isRskip98Active)) {
                reportInvalidBlock(blockNumber, headerBytes, headerHex, writer);
            }

            if (shouldPrintProgress(blockNumber)) {
                printCurrentProgress(blockNumber);
                progressStart = blockNumber + 1;
                progressInvalidCount = 0;
            }
        }
    }

    private boolean isBlockMissing(JsonNode blockResult, long blockNumber) {
        if (blockResult == null || blockResult.isNull()) {
            logger.warn("Block {} not found via RPC, skipping", blockNumber);
            return true;
        }
        return false;
    }

    private byte[] extractBtcHeader(JsonNode blockResult) {
        String headerHex = extractBtcHeaderHex(blockResult);
        if (headerHex == null || headerHex.isEmpty()) {
            return EMPTY_BYTE_ARRAY;
        }
        return HexUtils.stringHexToByteArray(headerHex);
    }

    private String extractBtcHeaderHex(JsonNode blockResult) {
        JsonNode headerNode = blockResult.get("bitcoinMergedMiningHeader");
        return (headerNode != null && !headerNode.isNull()) ? headerNode.asText() : null;
    }

    private void reportInvalidBlock(long blockNumber, byte[] headerBytes, String headerHex, PrintStream writer) {
        int actualBytes = headerBytes != null ? headerBytes.length : 0;
        String hexDisplay = headerHex != null ? headerHex : "null";

        logger.warn("Invalid BTC header at block {}: expected {} bytes, got {} bytes, header hex: {}",
                blockNumber, RskMiningConstants.BTC_HEADER_SIZE, actualBytes, hexDisplay);

        writer.println(blockNumber + "," + actualBytes + "," + hexDisplay);

        invalidCount++;
        progressInvalidCount++;
    }

    private boolean shouldPrintProgress(long blockNumber) {
        return (blockNumber - progressStart + 1) % PROGRESS_INTERVAL == 0;
    }

    private void printCurrentProgress(long currentBlock) {
        if (progressStart > currentBlock) {
            return;
        }
        String status = progressInvalidCount == 0
                ? "OK"
                : "FOUND " + progressInvalidCount + " INVALID BLOCKS";
        logger.info("Processed blocks {}-{}: {}", progressStart, currentBlock, status);
    }

    private void printSummary(long from, long to) {
        logger.info("Validation complete. Processed {} blocks, found {} invalid blocks. Output written to {}",
                to - from + 1, invalidCount, filePath);
    }

    // --- RPC communication ---

    private String resolveRpcUrl() {
        if (rpcUrl != null && !rpcUrl.isBlank()) {
            return rpcUrl;
        }
        return switch (network.toLowerCase()) {
            case "mainnet" -> MAINNET_RPC_URL;
            case "testnet" -> TESTNET_RPC_URL;
            default ->
                throw new IllegalArgumentException("Unknown network: " + network + ". Use 'mainnet' or 'testnet'.");
        };
    }

    private long resolveRskip98ActivationHeight() {
        return switch (network.toLowerCase()) {
            case "mainnet" -> MAINNET_RSKIP98_ACTIVATION_HEIGHT;
            case "testnet" -> TESTNET_RSKIP98_ACTIVATION_HEIGHT;
            default ->
                throw new IllegalArgumentException("Unknown network: " + network + ". Use 'mainnet' or 'testnet'.");
        };
    }

    private long fetchLatestBlockNumber(String url) throws IOException, InterruptedException {
        String payload = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\",\"params\":[],\"id\":1}";
        JsonNode response = sendRpcRequest(url, payload);
        String hexNumber = response.get("result").asText();
        return Long.parseLong(hexNumber.substring(2), 16);
    }

    private JsonNode[] fetchBlocks(String url, long from, long to) throws IOException, InterruptedException {
        int count = (int) (to - from + 1);
        String payload = buildBatchPayload(from, to);

        JsonNode response = sendRpcRequest(url, payload);

        return parseBatchResponse(response, from, count);
    }

    private String buildBatchPayload(long from, long to) {
        StringBuilder payload = new StringBuilder("[");
        for (long n = from; n <= to; n++) {
            if (n > from) {
                payload.append(",");
            }
            String hexBlock = "0x" + Long.toHexString(n);
            payload.append(String.format(
                    "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getBlockByNumber\",\"params\":[\"%s\",false],\"id\":%d}",
                    hexBlock, n));
        }
        payload.append("]");
        return payload.toString();
    }

    private JsonNode[] parseBatchResponse(JsonNode response, long from, int count) {
        JsonNode[] results = new JsonNode[count];
        if (response.isArray()) {
            for (JsonNode item : response) {
                long id = item.get("id").asLong();
                int index = (int) (id - from);
                if (index >= 0 && index < count) {
                    results[index] = item.get("result");
                }
            }
        }
        return results;
    }

    private JsonNode sendRpcRequest(String url, String payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(REQUEST_TIMEOUT)
                .build();

        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new IOException(
                            "RPC request failed with HTTP status " + response.statusCode() + ": " + response.body());
                }

                return objectMapper.readTree(response.body());
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long backoff = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
                    logger.warn("RPC request failed (attempt {}/{}): {}. Retrying in {}ms...",
                            attempt, MAX_RETRIES, e.getMessage(), backoff);
                    Thread.sleep(backoff);
                }
            }
        }

        throw new IOException("RPC request failed after " + MAX_RETRIES + " attempts", lastException);
    }

}
