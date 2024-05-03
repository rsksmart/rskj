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
import co.rsk.util.cli.CommandLineFixture;
import co.rsk.util.DataBytesFixture;
import co.rsk.util.HexUtils;
import co.rsk.util.OkHttpClientTestFixture;
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CliToolsIntegrationTest  {

    private String buildLibsPath;
    private String jarName;
    private String databaseDir;
    private String bloomsDbDir;
    private final int port = 9999;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @TempDir
    private Path tempDir;

    private String[] baseArgs;
    private String strBaseArgs;
    private String baseJavaCmd;


    @BeforeEach
    public void setup() throws IOException {
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
    }

    @Test
    void whenExportBlocksRuns_shouldExportSpecifiedBlocks() throws Exception {
        Map<String, Response> responseMap = new HashMap<>();
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.runCommand(
                cmd,
                1,
                TimeUnit.MINUTES,
                proc -> {
                    try {
                        Response response = OkHttpClientTestFixture.sendJsonRpcGetBestBlockMessage(port);
                        responseMap.put("latestProcessedBlock", response);
                    } catch (IOException e) {
                        Assertions.fail(e);
                    }
                }
        );

        String responseBody = responseMap.get("latestProcessedBlock").body().string();
        JsonNode jsonRpcResponse = objectMapper.readTree(responseBody);
        JsonNode transactionsNode = jsonRpcResponse.get(0).get("result").get("transactions");

        long blockNumber = HexUtils.jsonHexToLong(transactionsNode.get(0).get("blockNumber").asText());

        File blocksFile = tempDir.resolve("blocks.txt").toFile();
        Files.deleteIfExists(Paths.get(blocksFile.getAbsolutePath()));

        Assertions.assertTrue(blocksFile.createNewFile());

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ExportBlocks --fromBlock 0 --toBlock %s --file %s %s", baseJavaCmd, buildLibsPath, jarName, blockNumber, blocksFile.getAbsolutePath(), strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        List<String> exportedBlocksLines = Files.readAllLines(Paths.get(blocksFile.getAbsolutePath()));
        String exportedBlocksLine = exportedBlocksLines.stream()
                .filter(l -> l.split(",")[0].equals(String.valueOf(blockNumber)))
                .findFirst()
                .get();
        String[] exportedBlocksLineParts = exportedBlocksLine.split(",");

        Files.delete(Paths.get(blocksFile.getAbsolutePath()));

        Assertions.assertFalse(exportedBlocksLines.isEmpty());
        Assertions.assertTrue(transactionsNode.get(0).get("blockHash").asText().contains(exportedBlocksLineParts[1]));
    }

    @Test
    void whenExportStateRuns_shouldExportSpecifiedBlockState() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        RskContext rskContext = new RskContext(baseArgs);

        Block block = rskContext.getBlockchain().getBestBlock();
        Optional<Trie> optionalTrie = rskContext.getTrieStore().retrieve(block.getStateRoot());
        byte[] bMessage = optionalTrie.get().toMessage();
        String strMessage = ByteUtil.toHexString(bMessage);
        long blockNumber = block.getNumber();

        rskContext.close();

        File statesFile = tempDir.resolve("states.txt").toFile();
        Files.deleteIfExists(Paths.get(statesFile.getAbsolutePath()));

        Assertions.assertTrue(statesFile.createNewFile());

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ExportState --block %s --file %s %s", baseJavaCmd, buildLibsPath, jarName, blockNumber, statesFile.getAbsolutePath(), strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        List<String> exportedStateLines = Files.readAllLines(Paths.get(statesFile.getAbsolutePath()));

        Files.delete(Paths.get(statesFile.getAbsolutePath()));

        Assertions.assertFalse(exportedStateLines.isEmpty());
        Assertions.assertTrue(exportedStateLines.stream().anyMatch(l -> l.equals(strMessage)));
    }

    @Test
    void whenShowStateInfoRuns_shouldShowSpecifiedState() throws Exception {
        Map<String, Response> responseMap = new HashMap<>();
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.runCommand(
                cmd,
                1,
                TimeUnit.MINUTES, proc -> {
                    try {
                        Response response = OkHttpClientTestFixture.sendJsonRpcGetBestBlockMessage(port);
                        responseMap.put("latestProcessedBlock", response);
                    } catch (IOException e) {
                        Assertions.fail(e);
                    }
                }
        );

        String responseBody = responseMap.get("latestProcessedBlock").body().string();
        JsonNode jsonRpcResponse = objectMapper.readTree(responseBody);
        JsonNode result = jsonRpcResponse.get(0).get("result");
        JsonNode transactionsNode = result.get("transactions");

        long blockNumber = HexUtils.jsonHexToLong(transactionsNode.get(0).get("blockNumber").asText());

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ShowStateInfo --block %s %s", baseJavaCmd, buildLibsPath, jarName, blockNumber, strBaseArgs);
        CommandLineFixture.CustomProcess showStateInfoProc = CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        List<String> stateInfoLines = Arrays.asList(showStateInfoProc.getOutput().split("\\n"));

        Assertions.assertFalse(stateInfoLines.isEmpty());
        Assertions.assertTrue(stateInfoLines.stream().anyMatch(l -> l.contains(HexUtils.removeHexPrefix(result.get("hash").asText()))));
    }

    @Test
    void whenExecuteBlocksRuns_shouldReturnExpectedBestBlock() throws Exception {
        Map<String, Response> responseMap = new HashMap<>();
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.runCommand(
                cmd,
                1,
                TimeUnit.MINUTES, proc -> {
                    try {
                        Response response = OkHttpClientTestFixture.sendJsonRpcGetBestBlockMessage(port);
                        responseMap.put("latestProcessedBlock", response);
                    } catch (IOException e) {
                        Assertions.fail(e);
                    }
                }
        );

        String responseBody = responseMap.get("latestProcessedBlock").body().string();
        JsonNode jsonRpcResponse = objectMapper.readTree(responseBody);
        JsonNode result = jsonRpcResponse.get(0).get("result");
        JsonNode transactionsNode = result.get("transactions");

        long blockNumber = HexUtils.jsonHexToLong(transactionsNode.get(0).get("blockNumber").asText());
        long fromBlock = blockNumber - 10;
        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ExecuteBlocks --fromBlock %s --toBlock %s %s", baseJavaCmd, buildLibsPath, jarName, fromBlock, blockNumber, strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        RskContext rskContext = new RskContext(baseArgs);

        Block block = rskContext.getBlockchain().getBestBlock();

        rskContext.close();

        Assertions.assertEquals(block.getNumber(), blockNumber);
    }

    @Test
    void whenConnectBlocksRuns_shouldConnectSpecifiedBlocks() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        RskContext rskContext = new RskContext(baseArgs);

        Block block1 = rskContext.getBlockchain().getBlockByNumber(1);
        Block block2 = rskContext.getBlockchain().getBlockByNumber(2);
        rskContext.close();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("1,");
        stringBuilder.append(ByteUtil.toHexString(block1.getHash().getBytes()));
        stringBuilder.append(",02,");
        stringBuilder.append(ByteUtil.toHexString(block1.getEncoded()));
        stringBuilder.append("\n");
        stringBuilder.append("1,");
        stringBuilder.append(ByteUtil.toHexString(block2.getHash().getBytes()));
        stringBuilder.append(",03,");
        stringBuilder.append(ByteUtil.toHexString(block2.getEncoded()));
        stringBuilder.append("\n");

        File blocksFile = tempDir.resolve("blocks.txt").toFile();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(blocksFile))) {
            writer.write(stringBuilder.toString());
        }

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ConnectBlocks --file %s %s", baseJavaCmd, buildLibsPath, jarName, blocksFile.getAbsolutePath(), strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        Files.delete(Paths.get(blocksFile.getAbsolutePath()));

        rskContext = new RskContext(baseArgs);

        Block block1AfterConnect = rskContext.getBlockchain().getBlockByNumber(1);
        Block block2AfterConnect = rskContext.getBlockchain().getBlockByNumber(2);

        rskContext.close();

        Assertions.assertEquals(block1.getHash(), block1AfterConnect.getHash());
        Assertions.assertEquals(block2.getHash(), block2AfterConnect.getHash());
    }

    @Test
    void whenImportBlocksRuns_shouldImportAllExportedBlocks() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        File blocksFile = tempDir.resolve("blocks.txt").toFile();

        Assertions.assertTrue(blocksFile.createNewFile());

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ExportBlocks --fromBlock 0 --toBlock 20 --file %s %s", baseJavaCmd, buildLibsPath, jarName, blocksFile.getAbsolutePath(), strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        FileUtil.recursiveDelete(databaseDir);

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ImportBlocks --file %s %s", baseJavaCmd, buildLibsPath, jarName, blocksFile.getAbsolutePath(), strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        RskContext rskContext = new RskContext(baseArgs);

        long maxNumber = rskContext.getBlockStore().getMaxNumber();

        rskContext.close();

        Assertions.assertEquals(20, maxNumber);
    }

    @Test
    void whenImportStateRuns_shouldImportStateSuccessfully() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        RskContext rskContext = new RskContext(baseArgs);

        Block block = rskContext.getBlockchain().getBestBlock();
        Optional<Trie> optionalTrie = rskContext.getTrieStore().retrieve(block.getStateRoot());
        byte[] bMessage = optionalTrie.get().toMessage();
        String strMessage = ByteUtil.toHexString(bMessage);
        long blockNumber = block.getNumber();

        rskContext.close();

        File statesFile = tempDir.resolve("states.txt").toFile();
        Files.deleteIfExists(Paths.get(statesFile.getAbsolutePath()));

        Assertions.assertTrue(statesFile.createNewFile());

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ExportState --block %s --file %s %s", baseJavaCmd, buildLibsPath, jarName, blockNumber, statesFile.getAbsolutePath(), strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        FileUtil.recursiveDelete(databaseDir);

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.ImportState --file %s %s", baseJavaCmd, buildLibsPath, jarName, statesFile.getAbsolutePath(), strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        Files.delete(Paths.get(statesFile.getAbsolutePath()));

        rskContext = new RskContext(baseArgs);

        Optional<Trie> optionalTrieImported = rskContext.getTrieStore().retrieve(block.getStateRoot());
        byte[] bMessageImported = optionalTrieImported.get().toMessage();
        String strMessageImported = ByteUtil.toHexString(bMessageImported);

        rskContext.close();

        Assertions.assertEquals(strMessage, strMessageImported);
    }

    @Test
    void whenRewindBlocksRuns_shouldNotFindInconsistentBlocks() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.RewindBlocks -fmi %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.CustomProcess proc = CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        Assertions.assertTrue(proc.getOutput().contains("No inconsistent block has been found"));
    }

    @Test
    void whenRewindBlocksRuns_shouldRewindSpecifiedBlocks() throws Exception {
        String cmd = String.format("%s -cp %s/%s co.rsk.Start --reset %s", baseJavaCmd, buildLibsPath, jarName, strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        RskContext rskContext = new RskContext(baseArgs);

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

        rskContext.close();

        cmd = String.format("%s -cp %s/%s co.rsk.cli.tools.RewindBlocks --block %s %s", baseJavaCmd, buildLibsPath, jarName, bestBlock.getNumber() + 2, strBaseArgs);
        CommandLineFixture.runCommand(cmd, 1, TimeUnit.MINUTES);

        rskContext = new RskContext(baseArgs);

        long maxNumberAfterRewind = rskContext.getBlockStore().getMaxNumber();

        rskContext.close();

        Assertions.assertTrue(maxNumber > maxNumberAfterRewind);
        Assertions.assertEquals(bestBlock.getNumber() + 2, maxNumberAfterRewind);
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
    public void whenDbMigrateRuns_shouldMigrateLevelDbToRocksDbAndShouldStartNodeSuccessfully() throws Exception {
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
}
