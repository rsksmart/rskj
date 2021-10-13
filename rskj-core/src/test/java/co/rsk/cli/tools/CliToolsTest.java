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

import co.rsk.RskContext;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.db.HashMapBlocksIndex;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.trie.Trie;
import co.rsk.util.NodeStopper;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Random;

import static co.rsk.core.BlockDifficulty.ZERO;
import static org.ethereum.TestUtils.randomHash;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 26/04/2020.
 */
public class CliToolsTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void exportBlocks() throws IOException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/blocks01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        File blocksFile = new File(tempFolder.getRoot(), "blocks.txt");
        String[] args = new String[] { "0", "2", blocksFile.getAbsolutePath() };

        RskContext rskContext = mock(RskContext.class);
        doReturn(world.getBlockStore()).when(rskContext).getBlockStore();
        NodeStopper stopper = mock(NodeStopper.class);

        ExportBlocks exportBlocksCliTool = new ExportBlocks();
        exportBlocksCliTool.execute(args, () -> rskContext, stopper);

        String data = new String(Files.readAllBytes(blocksFile.toPath()), StandardCharsets.UTF_8);

        Blockchain blockchain = world.getBlockChain();
        BlockStore blockStore = world.getBlockStore();

        for (long n = 0; n < 3; n++) {
            Block block = blockchain.getBlockByNumber(n);
            BlockDifficulty totalDifficulty = blockStore.getTotalDifficultyForHash(block.getHash().getBytes());

            String line = block.getNumber() + "," + block.getHash().toHexString() + "," + ByteUtil.toHexString(totalDifficulty.getBytes()) + "," + ByteUtil.toHexString(block.getEncoded());

            Assert.assertTrue(data.contains(line));
        }

        verify(stopper).stop(0);
    }

    @Test
    public void exportState() throws IOException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        File stateFile = new File(tempFolder.getRoot(), "state.txt");
        String[] args = new String[] { "2", stateFile.getAbsolutePath() };

        RskContext rskContext = mock(RskContext.class);
        doReturn(world.getBlockStore()).when(rskContext).getBlockStore();
        doReturn(world.getTrieStore()).when(rskContext).getTrieStore();
        NodeStopper stopper = mock(NodeStopper.class);

        ExportState exportStateCliTool = new ExportState();
        exportStateCliTool.execute(args, () -> rskContext, stopper);

        String data = new String(Files.readAllBytes(stateFile.toPath()), StandardCharsets.UTF_8);

        Block block = world.getBlockByName("b02");

        Optional<Trie> otrie = world.getTrieStore().retrieve(block.getStateRoot());

        Assert.assertTrue(otrie.isPresent());

        Trie trie = otrie.get();

        byte[] encoded = trie.toMessage();

        String line = ByteUtil.toHexString(encoded);

        Assert.assertTrue(data.contains(line));

        verify(stopper).stop(0);
    }

    @Test
    public void showStateInfo() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String[] args = new String[] { "best" };

        RskContext rskContext = mock(RskContext.class);
        doReturn(world.getBlockStore()).when(rskContext).getBlockStore();
        doReturn(world.getTrieStore()).when(rskContext).getTrieStore();
        NodeStopper stopper = mock(NodeStopper.class);

        StringBuilder output = new StringBuilder();
        ShowStateInfo showStateInfoCliTool = new ShowStateInfo(output::append);
        showStateInfoCliTool.execute(args, () -> rskContext, stopper);

        String data = output.toString();

        Block block = world.getBlockByName("b02");

        String blockLine = "Block hash: " + ByteUtil.toHexString(block.getHash().getBytes());

        Assert.assertTrue(data.contains(blockLine));

        String longValueLine = "Trie long values: 1";

        Assert.assertTrue(data.contains(longValueLine));

        verify(stopper).stop(0);
    }

    @Test
    public void executeBlocks() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String[] args = new String[] { "1", "2" };

        RskContext rskContext = mock(RskContext.class);
        doReturn(world.getBlockExecutor()).when(rskContext).getBlockExecutor();
        doReturn(world.getBlockStore()).when(rskContext).getBlockStore();
        doReturn(world.getTrieStore()).when(rskContext).getTrieStore();
        doReturn(world.getStateRootHandler()).when(rskContext).getStateRootHandler();
        NodeStopper stopper = mock(NodeStopper.class);

        ExecuteBlocks executeBlocksCliTool = new ExecuteBlocks();
        executeBlocksCliTool.execute(args, () -> rskContext, stopper);

        Assert.assertEquals(2, world.getBlockChain().getBestBlock().getNumber());

        verify(stopper).stop(0);
    }

    @Test
    public void connectBlocks() throws IOException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/blocks01b.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Blockchain blockchain = world.getBlockChain();

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        Block block1 = world.getBlockByName("b01");
        Block block2 = world.getBlockByName("b02");

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

        File blocksFile = new File(tempFolder.getRoot(), "blocks.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(blocksFile))) {
            writer.write(stringBuilder.toString());
        }

        String[] args = new String[] { blocksFile.getAbsolutePath() };

        RskContext rskContext = mock(RskContext.class);
        doReturn(blockchain).when(rskContext).getBlockchain();
        doReturn(world.getBlockStore()).when(rskContext).getBlockStore();
        doReturn(world.getTrieStore()).when(rskContext).getTrieStore();
        doReturn(receiptStore).when(rskContext).getReceiptStore();
        doReturn(new BlockFactory(ActivationConfigsForTest.all())).when(rskContext).getBlockFactory();
        NodeStopper stopper = mock(NodeStopper.class);

        ConnectBlocks connectBlocksCliTool = new ConnectBlocks();
        connectBlocksCliTool.execute(args, () -> rskContext, stopper);

        Assert.assertEquals(2, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(block1.getHash(), blockchain.getBlockByNumber(1).getHash());
        Assert.assertEquals(block2.getHash(), blockchain.getBlockByNumber(2).getHash());

        verify(stopper).stop(0);
    }

    @Test
    public void importBlocks() throws IOException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/blocks01b.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Blockchain blockchain = world.getBlockChain();

        Assert.assertEquals(0, blockchain.getBestBlock().getNumber());

        Block block1 = world.getBlockByName("b01");
        Block block2 = world.getBlockByName("b02");

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

        File blocksFile = new File(tempFolder.getRoot(), "blocks.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(blocksFile))) {
            writer.write(stringBuilder.toString());
        }

        String[] args = new String[] { blocksFile.getAbsolutePath() };

        RskContext rskContext = mock(RskContext.class);
        doReturn(world.getBlockStore()).when(rskContext).getBlockStore();
        doReturn(new BlockFactory(ActivationConfigsForTest.all())).when(rskContext).getBlockFactory();
        NodeStopper stopper = mock(NodeStopper.class);

        ImportBlocks importBlocksCliTool = new ImportBlocks();
        importBlocksCliTool.execute(args, () -> rskContext, stopper);

        Assert.assertEquals(block1.getHash(), blockchain.getBlockByNumber(1).getHash());
        Assert.assertEquals(block2.getHash(), blockchain.getBlockByNumber(2).getHash());

        verify(stopper).stop(0);
    }

    @Test
    public void importState() throws IOException {
        byte[] value = new byte[42];
        Random random = new Random();
        random.nextBytes(value);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(ByteUtil.toHexString(value));
        stringBuilder.append("\n");

        File stateFile = new File(tempFolder.getRoot(), "state.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(stateFile))) {
            writer.write(stringBuilder.toString());
        }

        String databaseDir = new File(tempFolder.getRoot(), "db").getAbsolutePath();
        String[] args = new String[] { stateFile.getAbsolutePath() };

        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(databaseDir).when(rskSystemProperties).databaseDir();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();
        NodeStopper stopper = mock(NodeStopper.class);

        ImportState importStateCliTool = new ImportState();
        importStateCliTool.execute(args, () -> rskContext, stopper);

        byte[] key = new Keccak256(Keccak256Helper.keccak256(value)).getBytes();
        KeyValueDataSource trieDB = LevelDbDataSource.makeDataSource(Paths.get(databaseDir, "unitrie"));
        byte[] result = trieDB.get(key);
        trieDB.close();

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(value, result);

        verify(stopper).stop(0);
    }

    @Test
    public void rewindBlocks() {
        TestSystemProperties config = new TestSystemProperties();
        BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
        KeyValueDataSource keyValueDataSource = new HashMapDB();

        IndexedBlockStore indexedBlockStore = new IndexedBlockStore(
                blockFactory,
                keyValueDataSource,
                new HashMapBlocksIndex());

        long blocksToGenerate = 14;

        for (long i = 0; i < blocksToGenerate; i++) {
            Block block = mock(Block.class);
            Keccak256 blockHash = randomHash();
            when(block.getHash()).thenReturn(blockHash);
            when(block.getNumber()).thenReturn(i);
            when(block.getEncoded()).thenReturn(TestUtils.randomBytes(128));

            indexedBlockStore.saveBlock(block, ZERO, true);
        }

        Block bestBlock = indexedBlockStore.getBestBlock();
        assertThat(bestBlock.getNumber(), is(blocksToGenerate - 1));

        long blockToRewind = blocksToGenerate / 2;
        String[] args = new String[] { String.valueOf(blockToRewind) };

        RskContext rskContext = mock(RskContext.class);
        doReturn(indexedBlockStore).when(rskContext).getBlockStore();
        NodeStopper stopper = mock(NodeStopper.class);

        RewindBlocks rewindBlocksCliTool = new RewindBlocks();
        rewindBlocksCliTool.execute(args, () -> rskContext, stopper);

        bestBlock = indexedBlockStore.getBestBlock();
        assertThat(bestBlock.getNumber(), is(blockToRewind));

        verify(stopper).stop(0);
    }
}
