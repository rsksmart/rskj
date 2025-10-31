/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

import co.rsk.RskContext;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import co.rsk.util.DataBytesFixture;
import co.rsk.util.HexUtils;
import co.rsk.util.OkHttpClientTestFixture;
import co.rsk.util.cli.CommandLineFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Response;
import org.ethereum.core.Block;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CliToolsIntegrationTest {

    private final int port = 9999;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String buildLibsPath;
    private String jarName;
    private String databaseDir;
    private String bloomsDbDir;
    @TempDir
    private Path tempDir;

    private String[] baseArgs;
    private String strBaseArgs;
    private String baseJavaCmd;

    @BeforeEach
    void setup() throws IOException {
        try {
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
                        .get();
            }
            Path databaseDirPath = tempDir.resolve("database");
            databaseDir = databaseDirPath.toString();
            bloomsDbDir = databaseDirPath.resolve("blooms").toString();
            baseArgs = new String[]{
                    String.format("-Xdatabase.dir=%s", databaseDir),
                    "--regtest",
                    "-Xkeyvalue.datasource=leveldb",
                    String.format("-Xrpc.providers.web.http.port=%s", port)
            };
            strBaseArgs = String.join(" ", baseArgs);
            baseJavaCmd = String.format("java %s %s", String.format("-Dlogback.configurationFile=%s", logbackXmlFile), String.format("-Drsk.conf.file=%s", rskConfFile));
        } catch (Exception e) {
            // Ensure cleanup on setup failure
            cleanupTempFiles();
            throw e;
        }
    }

    @Test
    void whenExportBlocksRuns_shouldExportSpecifiedBlocks() throws Exception {
        Path blocksFile = null;
        try {
            BlockInfo blockInfo = getLatestBlockInfo();
            long blockNumber = blockInfo.blockNumber;

            blocksFile = createTempFile("blocks.txt");

            String cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ExportBlocks --fromBlock 0 --toBlock %s --file %s %s",
                    baseJavaCmd, buildLibsPath, jarName, blockNumber, blocksFile, strBaseArgs);
            executeCommand(cmd, 1, TimeUnit.MINUTES);

            waitForFile(blocksFile, 5000);
            List<String> exportedBlocksLines = readAllLines(blocksFile);
            String exportedBlocksLine = exportedBlocksLines.stream()
                    .filter(l -> l.split(",")[0].equals(String.valueOf(blockNumber)))
                    .findFirst()
                    .get();
            String[] exportedBlocksLineParts = exportedBlocksLine.split(",");

            Assertions.assertFalse(exportedBlocksLines.isEmpty());
            Assertions.assertTrue(blockInfo.transactionsNode.get(0).get("blockHash").asText().contains(exportedBlocksLineParts[1]));
        } finally {
            safeDeleteFile(blocksFile);
        }
    }

    @Test
    void whenExportStateRuns_shouldExportSpecifiedBlockState() throws Exception {
        Path statesFile = null;
        RskContext rskContext = null;
        try {
            startRskNode();

            rskContext = createRskContext();
            Block block = rskContext.getBlockchain().getBestBlock();
            Optional<Trie> optionalTrie = rskContext.getTrieStore().retrieve(block.getStateRoot());
            byte[] bMessage = optionalTrie.get().toMessage();
            String strMessage = ByteUtil.toHexString(bMessage);
            long blockNumber = block.getNumber();

            closeRskContext(rskContext);
            rskContext = null;

            statesFile = createTempFile("states.txt");

            String cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ExportState --block %s --file %s %s",
                    baseJavaCmd, buildLibsPath, jarName, blockNumber, statesFile, strBaseArgs);
            executeCommand(cmd, 1, TimeUnit.MINUTES);

            validateFileContainsExact(statesFile, strMessage);
        } finally {
            closeRskContext(rskContext);
            safeDeleteFile(statesFile);
        }
    }

    @Test
    void whenShowStateInfoRuns_shouldShowSpecifiedState() throws Exception {
        BlockInfo blockInfo = getLatestBlockInfo();
        long blockNumber = blockInfo.blockNumber;

        String cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ShowStateInfo --block %s %s",
                baseJavaCmd, buildLibsPath, jarName, blockNumber, strBaseArgs);
        CommandLineFixture.CustomProcess showStateInfoProc = executeCommand(cmd, 1, TimeUnit.MINUTES);

        List<String> stateInfoLines = Arrays.asList(showStateInfoProc.getOutput().split("\\n"));

        Assertions.assertFalse(stateInfoLines.isEmpty());
        Assertions.assertTrue(stateInfoLines.stream().anyMatch(l -> l.contains(HexUtils.removeHexPrefix(blockInfo.blockHash))));
    }

    @Test
    void whenExecuteBlocksRuns_shouldReturnExpectedBestBlock() throws Exception {
        RskContext rskContext = null;
        try {
            BlockInfo blockInfo = getLatestBlockInfo();
            long blockNumber = blockInfo.blockNumber;
            long fromBlock = blockNumber - 10;

            String cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ExecuteBlocks --fromBlock %s --toBlock %s %s",
                    baseJavaCmd, buildLibsPath, jarName, fromBlock, blockNumber, strBaseArgs);
            executeCommand(cmd, 2, TimeUnit.MINUTES);

            rskContext = createRskContext();
            Block block = rskContext.getBlockchain().getBestBlock();

            Assertions.assertEquals(block.getNumber(), blockNumber);
        } finally {
            closeRskContext(rskContext);
        }
    }

    @Test
    void whenConnectBlocksRuns_shouldConnectSpecifiedBlocks() throws Exception {
        RskContext rskContext = null;
        Path blocksFile = null;
        try {
            startRskNode();

            rskContext = createRskContext();
            Block block1 = rskContext.getBlockchain().getBlockByNumber(1);
            Block block2 = rskContext.getBlockchain().getBlockByNumber(2);
            closeRskContext(rskContext);
            rskContext = null;

            blocksFile = createBlocksFile("blocks.txt", block1, block2);

            String cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ConnectBlocks --file %s %s",
                    baseJavaCmd, buildLibsPath, jarName, blocksFile, strBaseArgs);
            executeCommand(cmd, 1, TimeUnit.MINUTES);

            rskContext = createRskContext();
            Block block1AfterConnect = rskContext.getBlockchain().getBlockByNumber(1);
            Block block2AfterConnect = rskContext.getBlockchain().getBlockByNumber(2);

            Assertions.assertEquals(block1.getHash(), block1AfterConnect.getHash());
            Assertions.assertEquals(block2.getHash(), block2AfterConnect.getHash());
        } finally {
            closeRskContext(rskContext);
            safeDeleteFile(blocksFile);
        }
    }

    @Test
    void whenImportBlocksRuns_shouldImportAllExportedBlocks() throws Exception {
        Path blocksFile = null;
        RskContext rskContext = null;
        try {
            startRskNode();

            blocksFile = createTempFile("blocks.txt");

            String cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ExportBlocks --fromBlock 0 --toBlock 20 --file %s %s",
                    baseJavaCmd, buildLibsPath, jarName, blocksFile, strBaseArgs);
            executeCommand(cmd, 1, TimeUnit.MINUTES);

            waitForFile(blocksFile, 5000);

            FileUtil.recursiveDelete(databaseDir);

            cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ImportBlocks --file %s %s",
                    baseJavaCmd, buildLibsPath, jarName, blocksFile, strBaseArgs);
            executeCommand(cmd, 1, TimeUnit.MINUTES);

            rskContext = createRskContext();
            long maxNumber = rskContext.getBlockStore().getMaxNumber();

            Assertions.assertEquals(20, maxNumber);
        } finally {
            closeRskContext(rskContext);
            safeDeleteFile(blocksFile);
        }
    }

    @Test
    void whenImportStateRuns_shouldImportStateSuccessfully() throws Exception {
        Path statesFile = null;
        RskContext rskContext = null;
        try {
            startRskNode();

            rskContext = createRskContext();
            Block block = rskContext.getBlockchain().getBestBlock();
            Optional<Trie> optionalTrie = rskContext.getTrieStore().retrieve(block.getStateRoot());
            byte[] bMessage = optionalTrie.get().toMessage();
            String strMessage = ByteUtil.toHexString(bMessage);
            long blockNumber = block.getNumber();

            closeRskContext(rskContext);
            rskContext = null;

            statesFile = createTempFile("states.txt");

            String cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ExportState --block %s --file %s %s",
                    baseJavaCmd, buildLibsPath, jarName, blockNumber, statesFile, strBaseArgs);
            executeCommand(cmd, 1, TimeUnit.MINUTES);

            waitForFile(statesFile, 5000);

            FileUtil.recursiveDelete(databaseDir);

            cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ImportState --file %s %s",
                    baseJavaCmd, buildLibsPath, jarName, statesFile, strBaseArgs);
            executeCommand(cmd, 1, TimeUnit.MINUTES);

            rskContext = createRskContext();
            Optional<Trie> optionalTrieImported = rskContext.getTrieStore().retrieve(block.getStateRoot());
            byte[] bMessageImported = optionalTrieImported.get().toMessage();
            String strMessageImported = ByteUtil.toHexString(bMessageImported);

            Assertions.assertEquals(strMessage, strMessageImported);
        } finally {
            closeRskContext(rskContext);
            safeDeleteFile(statesFile);
        }
    }

    @Test
    void whenRewindBlocksRuns_shouldNotFindInconsistentBlocks() throws Exception {
        startRskNode();

        String cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.RewindBlocks -fmi %s",
                baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.CustomProcess proc = executeCommand(cmd, 1, TimeUnit.MINUTES);

        validateProcessOutput(proc, "No inconsistent block has been found");
    }

    @Test
    void whenRewindBlocksRuns_shouldRewindSpecifiedBlocks() throws Exception {
        RskContext rskContext = null;
        try {
            startRskNode();

            rskContext = createRskContext();
            Random random = new Random(100);

            Block bestBlock = rskContext.getBlockStore().getBestBlock();
            long blocksToGenerate = bestBlock.getNumber() + 14;
            Keccak256 parentHash = bestBlock.getHash();

            for (long i = bestBlock.getNumber() + 1; i < blocksToGenerate; i++) {
                Block block = mock(Block.class);
                Keccak256 blockHash = new Keccak256(DataBytesFixture.generateBytesFromRandom(random, 32));
                when(block.getHash()).thenReturn(blockHash);
                when(block.getParentHash()).thenReturn(parentHash);
                when(block.getNumber()).thenReturn(i);
                when(block.getEncoded()).thenReturn(bestBlock.getEncoded());

                rskContext.getBlockStore().saveBlock(block, BlockDifficulty.ZERO, true);
                parentHash = blockHash;
            }

            long maxNumber = rskContext.getBlockStore().getMaxNumber();
            closeRskContext(rskContext);
            rskContext = null;

            String cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.RewindBlocks --block %s %s",
                    baseJavaCmd, buildLibsPath, jarName, bestBlock.getNumber() + 2, strBaseArgs);
            executeCommand(cmd, 1, TimeUnit.MINUTES);

            rskContext = createRskContext();
            long maxNumberAfterRewind = rskContext.getBlockStore().getMaxNumber();

            Assertions.assertTrue(maxNumber > maxNumberAfterRewind);
            Assertions.assertEquals(bestBlock.getNumber() + 2, maxNumberAfterRewind);
        } finally {
            closeRskContext(rskContext);
        }
    }

    @Test
    void whenDbMigrateRuns_shouldMigrateLevelDbToRocksDbAndShouldStartNodeWithPrevDbKind() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES, false);

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.DbMigrate --targetDb rocksdb %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.CustomProcess dbMigrateProc = CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        cmd = String.format("%s -cp %s/%s co.rsk.Start --regtest %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.CustomProcess proc = CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES, false);

        List<String> logLines = Arrays.asList(proc.getOutput().split("\\n"));

        Assertions.assertTrue(dbMigrateProc.getOutput().contains("DbMigrate finished"));
        Assertions.assertTrue(logLines.stream().anyMatch(l -> l.contains("[minerserver] [miner client]  Mined block import result is IMPORTED_BEST")));
        Assertions.assertTrue(logLines.stream().noneMatch(l -> l.contains("Exception:")));
    }

    @Test
    void whenDbMigrateRuns_shouldMigrateLevelDbToRocksDbAndShouldStartNodeSuccessfully() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.DbMigrate --targetDb rocksdb %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.CustomProcess dbMigrateProc = CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        LinkedList<String> args = Stream.of(baseArgs)
                .map(arg -> arg.equals("-Xkeyvalue.datasource=leveldb") ? "-Xkeyvalue.datasource=rocksdb" : arg)
                .collect(Collectors.toCollection(LinkedList::new));

        cmd = String.format("%s -cp %s/%s co.rsk.Start %s", baseJavaCmd, buildLibsPath, jarName, String.join(" ", args));
        CommandLineFixture.CustomProcess proc = CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        List<String> logLines = Arrays.asList(proc.getOutput().split("\\n"));

        Assertions.assertTrue(dbMigrateProc.getOutput().contains("DbMigrate finished"));
        Assertions.assertTrue(logLines.stream().anyMatch(l -> l.contains("[minerserver] [miner client]  Mined block import result is IMPORTED_BEST")));
        Assertions.assertTrue(logLines.stream().noneMatch(l -> l.contains("Exception:")));
    }

    @Test
    void whenStartBootstrapRuns_shouldRunSuccessfully() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.StartBootstrap --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.CustomProcess proc = CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        Assertions.assertTrue(proc.getOutput().contains("Identified public IP"));
        Assertions.assertTrue(proc.getOutput().contains("Discovery UDPListener started"));
    }

    @Test
    void whenIndexBloomsRuns_shouldIndexBlockRangeSInBLoomsDbSuccessfully() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        FileUtil.recursiveDelete(bloomsDbDir);

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.IndexBlooms -fb %s -tb %s %s", baseJavaCmd, buildLibsPath, jarName, "earliest", "latest", strBaseArgs);
        CommandLineFixture.CustomProcess proc = CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        Assertions.assertTrue(proc.getOutput().contains("[c.r.c.t.IndexBlooms] [main]  Processed "));
    }

    private Path createTempFile(String fileName) throws IOException {
        Path filePath = tempDir.resolve(fileName);
        return createFileWithRetry(filePath);
    }

    /**
     * Creates a file with retry mechanism to handle potential race conditions
     */
    private Path createFileWithRetry(Path filePath) throws IOException {
        int maxRetries = 3;
        int retryDelayMs = 100;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                Files.createDirectories(filePath.getParent());

                Files.deleteIfExists(filePath);

                Files.createFile(filePath);

                if (Files.exists(filePath) && Files.isWritable(filePath)) {
                    return filePath;
                }
            } catch (IOException e) {
                if (attempt == maxRetries - 1) {
                    throw new IOException("Failed to create file " + filePath + " after " + maxRetries + " attempts", e);
                }
                try {
                    Thread.sleep(retryDelayMs * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while retrying file creation", ie);
                }
            }
        }
        throw new IOException("Failed to create file " + filePath + " after " + maxRetries + " attempts");
    }

    private void writeToFile(Path filePath, String content) throws IOException {
        try {
            Files.write(filePath, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IOException("Failed to write to file " + filePath, e);
        }
    }

    private List<String> readAllLines(Path filePath) throws IOException {
        try {
            return Files.readAllLines(filePath);
        } catch (IOException e) {
            throw new IOException("Failed to read lines from file " + filePath, e);
        }
    }

    /**
     * Safely deletes a file with retry mechanism
     */
    private void safeDeleteFile(Path filePath) {
        if (filePath != null && Files.exists(filePath)) {
            int maxRetries = 3;
            int retryDelayMs = 50;

            for (int attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    Files.delete(filePath);
                    return; // Success
                } catch (IOException e) {
                    if (attempt == maxRetries - 1) {
                        // Log warning but don't fail the test
                        System.err.println("Warning: Failed to delete file " + filePath + " after " + maxRetries + " attempts: " + e.getMessage());
                    } else {
                        try {
                            Thread.sleep(retryDelayMs * (attempt + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
        }
    }

    private void waitForFile(Path filePath, long timeoutMs) throws IOException, TimeoutException {
        long startTime = System.currentTimeMillis();
        long checkInterval = 100; // Check every 100ms

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (Files.exists(filePath) && Files.isReadable(filePath)) {
                return;
            }
            try {
                Thread.sleep(checkInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for file " + filePath, e);
            }
        }
        throw new TimeoutException("File " + filePath + " did not become available within " + timeoutMs + "ms");
    }

    /**
     * Ensures proper cleanup of temporary files and resources
     */
    private void cleanupTempFiles() {
        try {
            // Clean up any remaining files in temp directory
            if (tempDir != null && Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                // Log but don't fail - this is cleanup
                                System.err.println("Warning: Could not delete " + path + ": " + e.getMessage());
                            }
                        });
            }
        } catch (IOException e) {
            System.err.println("Warning: Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Safely manages RskContext lifecycle with proper cleanup
     */
    private RskContext createRskContext() {
        return new RskContext(baseArgs);
    }

    /**
     * Safely closes RskContext with error handling
     */
    private void closeRskContext(RskContext rskContext) {
        if (rskContext != null) {
            try {
                rskContext.close();
            } catch (Exception e) {
                System.err.println("Warning: Error closing RskContext: " + e.getMessage());
            }
        }
    }

    /**
     * Executes a command and waits for it to complete
     */
    private CommandLineFixture.CustomProcess executeCommand(String cmd, int timeout, TimeUnit timeUnit) throws InterruptedException, IOException {
        return CommandLineFixture.runCommand(cmd, timeout, timeUnit);
    }

    /**
     * Executes a command with a callback for processing
     */
    private void executeCommandWithCallback(String cmd, int timeout, TimeUnit timeUnit,
                                            java.util.function.Consumer<Process> callback) throws InterruptedException, IOException {
        CommandLineFixture.runCommand(cmd, timeout, timeUnit, callback);
    }

    /**
     * Starts RSK node and waits for it to be ready
     */
    private void startRskNode() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        executeCommand(cmd, 1, TimeUnit.MINUTES);
    }

    /**
     * Starts RSK node and waits for it to be ready with callback
     */
    private void startRskNodeWithCallback(java.util.function.Consumer<Process> callback) throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        executeCommandWithCallback(cmd, 1, TimeUnit.MINUTES, callback);
    }

    /**
     * Gets the latest block information from the running node
     */
    private BlockInfo getLatestBlockInfo() throws Exception {
        Map<String, Response> responseMap = new HashMap<>();
        startRskNodeWithCallback(proc -> {
            try {
                Response response = OkHttpClientTestFixture.sendJsonRpcGetBestBlockMessage(port);
                responseMap.put("latestProcessedBlock", response);
            } catch (IOException e) {
                Assertions.fail("Failed to get latest block info: " + e.getMessage());
            }
        });

        String responseBody = responseMap.get("latestProcessedBlock").body().string();
        JsonNode jsonRpcResponse = objectMapper.readTree(responseBody);
        JsonNode result = jsonRpcResponse.get(0).get("result");
        JsonNode transactionsNode = result.get("transactions");

        long blockNumber = HexUtils.jsonHexToLong(transactionsNode.get(0).get("blockNumber").asText());
        String blockHash = result.get("hash").asText();

        return new BlockInfo(blockNumber, blockHash, transactionsNode);
    }

    /**
     * Validates that a file contains expected content (exact match)
     */
    private void validateFileContainsExact(Path filePath, String expectedContent) throws Exception {
        waitForFile(filePath, 5000);
        List<String> lines = readAllLines(filePath);
        Assertions.assertFalse(lines.isEmpty(), "File should not be empty");
        Assertions.assertTrue(lines.stream().anyMatch(l -> l.equals(expectedContent)),
                "File should contain exact match: " + expectedContent);
    }

    /**
     * Creates a blocks file with specified blocks
     */
    private Path createBlocksFile(String fileName, Block... blocks) throws IOException {
        Path blocksFile = createTempFile(fileName);
        StringBuilder content = new StringBuilder();

        for (int i = 0; i < blocks.length; i++) {
            Block block = blocks[i];
            content.append(block.getNumber()).append(",");
            content.append(ByteUtil.toHexString(block.getHash().getBytes())).append(",");
            content.append("0").append(i + 2).append(",");
            content.append(ByteUtil.toHexString(block.getEncoded()));
            content.append("\n");
        }

        writeToFile(blocksFile, content.toString());
        return blocksFile;
    }

    /**
     * Validates that a process output contains expected text
     */
    private void validateProcessOutput(CommandLineFixture.CustomProcess process, String expectedText) {
        String output = process.getOutput();
        Assertions.assertTrue(output.contains(expectedText),
                "Process output should contain: " + expectedText + "\nActual output: " + output);
    }

    /**
     * Helper class to hold block information
     */
    private record BlockInfo(long blockNumber, String blockHash, JsonNode transactionsNode) {
    }
}
