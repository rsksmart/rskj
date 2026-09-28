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
package co.rsk.cli.tools;

import co.rsk.RskContext;
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.util.NodeStopper;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.DbKind;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class DecodeDifferentialTest {

    @TempDir
    private Path tempDir;

    @Test
    void cleanRangeReportsNoMismatches() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("clean.jsonl");

        runTool(world, world.getBlockStore(), report);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertFalse(lines.isEmpty(), "the tool must emit a digest stream");
        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"kind\":\"tx\"")),
                "the range must contain at least one transaction, otherwise the run proves nothing");
        Assertions.assertTrue(lines.stream().allMatch(DecodeDifferentialTest::isClean),
                "every record must be clean: " + lines);
    }

    /**
     * Sensitivity probe: proves the transactions-trie-root comparison is load-bearing rather
     * than decorative. Without it a green run would carry no information.
     */
    @Test
    void tamperedTxTrieRootIsDetected() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("tampered-root.jsonl");

        Block tampered = spy(world.getBlockStore().getChainBlockByNumber(1));
        doReturn(new byte[32]).when(tampered).getTxTrieRoot();

        runToolExpectingFailure(world, blockStoreReturning(world, tampered), report);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"txTrieRootOk\":false")),
                "a tampered stored root must be reported: " + lines);
    }

    /**
     * Sensitivity probe for the per-transaction check: a transaction whose hash does not survive
     * a decode/encode cycle must be reported, which is what localizes a divergence to one
     * transaction rather than to a whole block.
     */
    @Test
    void unstableTransactionHashIsDetected() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("unstable-hash.jsonl");

        Block block = world.getBlockStore().getChainBlockByNumber(1);
        Transaction unstable = spy(block.getTransactionsList().get(0));
        doReturn(new Keccak256(new byte[32])).when(unstable).getHash();

        Block tampered = spy(block);
        doReturn(Collections.singletonList(unstable)).when(tampered).getTransactionsList();

        runToolExpectingFailure(world, blockStoreReturning(world, tampered), report);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"hashStable\":false")),
                "an unstable transaction hash must be reported: " + lines);
    }

    /**
     * A block whose transactions no longer re-encode faithfully throws inside the Block
     * constructor, before any per-transaction check runs. That failure is the result and must be
     * recorded, and the range must continue: it is the only way to learn how many blocks are
     * affected rather than just the first.
     */
    @Test
    void blockLoadFailureIsRecordedAndDoesNotAbortTheRun() throws FileNotFoundException, DslProcessorException, IOException {
        World world = buildWorld();
        Path report = tempDir.resolve("load-failure.jsonl");

        BlockStore blockStore = spy(world.getBlockStore());
        doThrow(new IllegalArgumentException("Transactions trie root validation failed for block 1"))
                .when(blockStore).getChainBlockByNumber(1L);

        runToolExpectingFailure(world, blockStore, report);

        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);

        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"blockLoadFailed\":true")),
                "the failing block must be recorded: " + lines);
        Assertions.assertTrue(lines.stream().anyMatch(l -> l.contains("\"number\":2")),
                "the run must continue to the next block: " + lines);
    }

    private static boolean isClean(String line) {
        return !line.contains("\"txTrieRootOk\":false")
                && !line.contains("\"roundTrip\":false")
                && !line.contains("\"hashStable\":false")
                && !line.contains("\"missing\":true");
    }

    private static World buildWorld() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        World world = new World();
        new WorldDslProcessor(world).processCommands(parser);

        return world;
    }

    private BlockStore blockStoreReturning(World world, Block block) {
        long number = block.getNumber();
        BlockStore blockStore = spy(world.getBlockStore());
        doReturn(block).when(blockStore).getChainBlockByNumber(number);

        return blockStore;
    }

    private void runTool(World world, BlockStore blockStore, Path report) {
        NodeStopper stopper = run(world, blockStore, report);

        verify(stopper).stop(0);
    }

    private void runToolExpectingFailure(World world, BlockStore blockStore, Path report) {
        NodeStopper stopper = run(world, blockStore, report);

        verify(stopper).stop(1);
    }

    private NodeStopper run(World world, BlockStore blockStore, Path report) {
        String[] args = new String[]{
                "--fromBlock", "1",
                "--toBlock", "2",
                "--report", report.toString(),
                "--skip-senders"
        };

        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(world.getConfig().getActivationConfig()).when(rskSystemProperties).getActivationConfig();
        doReturn(tempDir.toString()).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();

        RskContext rskContext = mock(RskContext.class);
        doReturn(blockStore).when(rskContext).getBlockStore();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();
        doReturn(world.getBlockTxSignatureCache()).when(rskContext).getBlockTxSignatureCache();

        NodeStopper stopper = mock(NodeStopper.class);

        new DecodeDifferential().execute(args, () -> rskContext, stopper);

        return stopper;
    }
}
