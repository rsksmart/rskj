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

import co.rsk.NodeRunner;
import co.rsk.RskContext;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.db.HashMapBlocksIndex;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.logfilter.BlocksBloom;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.trie.Trie;
import co.rsk.util.NodeStopper;
import co.rsk.util.PreflightChecksUtils;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Bloom;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Random;

import static co.rsk.core.BlockDifficulty.ZERO;
import static org.ethereum.TestUtils.randomHash;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
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
        String[] args = new String[]{"0", "2", blocksFile.getAbsolutePath()};

        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(world.getBlockStore()).when(rskContext).getBlockStore();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();
        doReturn(tempFolder.getRoot().getPath()).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();
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
        String[] args = new String[]{"2", stateFile.getAbsolutePath()};

        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(world.getBlockStore()).when(rskContext).getBlockStore();
        doReturn(world.getTrieStore()).when(rskContext).getTrieStore();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();
        doReturn(tempFolder.getRoot().getPath()).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();
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

        String[] args = new String[]{"best"};

        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(world.getBlockStore()).when(rskContext).getBlockStore();
        doReturn(world.getTrieStore()).when(rskContext).getTrieStore();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();
        doReturn(tempFolder.getRoot().getPath()).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();
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

        String[] args = new String[]{"1", "2"};

        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(world.getBlockExecutor()).when(rskContext).getBlockExecutor();
        doReturn(world.getBlockStore()).when(rskContext).getBlockStore();
        doReturn(world.getTrieStore()).when(rskContext).getTrieStore();
        doReturn(world.getStateRootHandler()).when(rskContext).getStateRootHandler();
        doReturn(world.getTrieStore()).when(rskContext).getTrieStore();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();
        doReturn(tempFolder.getRoot().getPath()).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();
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

        String[] args = new String[]{blocksFile.getAbsolutePath()};

        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(blockchain).when(rskContext).getBlockchain();
        doReturn(world.getBlockStore()).when(rskContext).getBlockStore();
        doReturn(world.getTrieStore()).when(rskContext).getTrieStore();
        doReturn(receiptStore).when(rskContext).getReceiptStore();
        doReturn(new BlockFactory(ActivationConfigsForTest.all())).when(rskContext).getBlockFactory();
        doReturn(world.getTrieStore()).when(rskContext).getTrieStore();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();
        doReturn(tempFolder.getRoot().getPath()).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();
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

        String[] args = new String[]{blocksFile.getAbsolutePath()};

        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(world.getBlockStore()).when(rskContext).getBlockStore();
        doReturn(new BlockFactory(ActivationConfigsForTest.all())).when(rskContext).getBlockFactory();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();
        doReturn(tempFolder.getRoot().getPath()).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();
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
        String[] args = new String[]{stateFile.getAbsolutePath()};

        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(databaseDir).when(rskSystemProperties).databaseDir();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();
        NodeStopper stopper = mock(NodeStopper.class);

        ImportState importStateCliTool = new ImportState();
        importStateCliTool.execute(args, () -> rskContext, stopper);

        byte[] key = new Keccak256(Keccak256Helper.keccak256(value)).getBytes();
        KeyValueDataSource trieDB = KeyValueDataSourceUtils.makeDataSource(Paths.get(databaseDir, "unitrie"), rskSystemProperties.databaseKind(), false);
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

        int blocksToGenerate = 14;

        Keccak256 parentHash = Keccak256.ZERO_HASH;
        for (long i = 0; i < blocksToGenerate; i++) {
            Block block = mock(Block.class);
            Keccak256 blockHash = randomHash();
            when(block.getHash()).thenReturn(blockHash);
            when(block.getParentHash()).thenReturn(parentHash);
            when(block.getNumber()).thenReturn(i);
            when(block.getEncoded()).thenReturn(TestUtils.randomBytes(128));

            indexedBlockStore.saveBlock(block, ZERO, true);
            parentHash = blockHash;
        }

        Block bestBlock = indexedBlockStore.getBestBlock();
        assertThat(bestBlock.getNumber(), is((long) blocksToGenerate - 1));

        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(indexedBlockStore).when(rskContext).getBlockStore();
        RepositoryLocator repositoryLocator = mock(RepositoryLocator.class);
        doReturn(Optional.of(mock(RepositorySnapshot.class))).when(repositoryLocator).findSnapshotAt(any());
        doReturn(repositoryLocator).when(rskContext).getRepositoryLocator();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();
        doReturn(tempFolder.getRoot().getPath()).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();
        NodeStopper stopper = mock(NodeStopper.class);

        StringBuilder output = new StringBuilder();
        RewindBlocks rewindBlocksCliTool = new RewindBlocks(output::append);
        rewindBlocksCliTool.execute(new String[]{"fmi"}, () -> rskContext, stopper);

        String data = output.toString();
        Assert.assertTrue(data.contains("No inconsistent block has been found"));

        verify(stopper).stop(0);

        clearInvocations(stopper);

        long blockToRewind = blocksToGenerate / 2;

        output = new StringBuilder();
        rewindBlocksCliTool = new RewindBlocks(output::append);
        rewindBlocksCliTool.execute(new String[]{String.valueOf(blockToRewind)}, () -> rskContext, stopper);

        bestBlock = indexedBlockStore.getBestBlock();
        assertThat(bestBlock.getNumber(), is(blockToRewind));

        data = output.toString();
        Assert.assertTrue(data.contains("New highest block number stored in db: " + blockToRewind));

        verify(stopper).stop(0);

        clearInvocations(stopper);

        output = new StringBuilder();
        rewindBlocksCliTool = new RewindBlocks(output::append);
        rewindBlocksCliTool.execute(new String[]{String.valueOf(blocksToGenerate + 1)}, () -> rskContext, stopper);

        bestBlock = indexedBlockStore.getBestBlock();
        assertThat(bestBlock.getNumber(), is(blockToRewind));

        data = output.toString();
        Assert.assertTrue(data.contains("No need to rewind"));

        verify(stopper).stop(0);

        clearInvocations(stopper);

        doReturn(Optional.empty()).when(repositoryLocator).findSnapshotAt(any());

        output = new StringBuilder();
        rewindBlocksCliTool = new RewindBlocks(output::append);
        rewindBlocksCliTool.execute(new String[]{"fmi"}, () -> rskContext, stopper);

        data = output.toString();
        Assert.assertTrue(data.contains("Min inconsistent block number: 0"));

        verify(stopper).stop(0);

        clearInvocations(stopper);

        output = new StringBuilder();
        rewindBlocksCliTool = new RewindBlocks(output::append);
        rewindBlocksCliTool.execute(new String[]{"rbc"}, () -> rskContext, stopper);

        data = output.toString();
        Assert.assertTrue(data.contains("Min inconsistent block number: 0"));
        Assert.assertTrue(data.contains("New highest block number stored in db: -1"));

        verify(stopper).stop(0);
    }

    @Test
    public void dbMigrate() throws IOException {
        File nodeIdPropsFile = new File(tempFolder.getRoot(), "nodeId.properties");
        File dbKindPropsFile = new File(tempFolder.getRoot(), KeyValueDataSourceUtils.DB_KIND_PROPERTIES_FILE);

        if (nodeIdPropsFile.createNewFile()) {
            FileWriter myWriter = new FileWriter(nodeIdPropsFile);
            myWriter.write("nodeId=testing");
            myWriter.close();
        }

        new File(tempFolder.getRoot(), "blocks").mkdir();

        RskContext rskContext = mock(RskContext.class);
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);

        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();
        doReturn(tempFolder.getRoot().getPath()).when(rskSystemProperties).databaseDir();
        doReturn(true).when(rskSystemProperties).databaseReset();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();

        NodeStopper stopper = mock(NodeStopper.class);

        DbMigrate dbMigrateCliTool = new DbMigrate();
        dbMigrateCliTool.execute(new String[]{"rocksdb"}, () -> rskContext, stopper);

        String nodeIdPropsFileLine = null;

        if (nodeIdPropsFile.exists()) {
            BufferedReader reader = new BufferedReader(new FileReader(nodeIdPropsFile));
            nodeIdPropsFileLine = reader.readLine();
            reader.close();
        }

        String dbKindPropsFileLine = null;

        if (dbKindPropsFile.exists()) {
            BufferedReader reader = new BufferedReader(new FileReader(dbKindPropsFile));
            reader.readLine();
            reader.readLine();
            dbKindPropsFileLine = reader.readLine();
            reader.close();
        }

        Assert.assertEquals(nodeIdPropsFileLine, "nodeId=testing");
        Assert.assertEquals(dbKindPropsFileLine, "keyvalue.datasource=ROCKS_DB");
    }

    @Test
    public void startBootstrap() throws Exception {
        // check thread setup
        Thread thread = new Thread(() -> {
        });

        StartBootstrap.setUpThread(thread);

        assertEquals("main", thread.getName());

        // check happy flow of bootstrap node start
        ArgumentCaptor<Thread> threadCaptor = ArgumentCaptor.forClass(Thread.class);
        NodeRunner runner = mock(NodeRunner.class);
        RskContext ctx = mock(RskContext.class);
        doReturn(runner).when(ctx).getNodeRunner();
        PreflightChecksUtils preflightChecks = mock(PreflightChecksUtils.class);
        Runtime runtime = mock(Runtime.class);
        NodeStopper nodeStopper = mock(NodeStopper.class);

        StartBootstrap.runBootstrapNode(ctx, preflightChecks, runtime, nodeStopper);

        verify(preflightChecks, times(1)).runChecks();
        verify(runner, times(1)).run();
        verify(runtime, times(1)).addShutdownHook(threadCaptor.capture());
        assertEquals("stopper", threadCaptor.getValue().getName());

        // check unhappy flow of bootstrap node start
        doThrow(new RuntimeException()).when(preflightChecks).runChecks();

        StartBootstrap.runBootstrapNode(ctx, preflightChecks, runtime, nodeStopper);

        verify(preflightChecks, times(2)).runChecks();
        verify(runner, times(1)).run();
        verify(runtime, times(1)).addShutdownHook(any());
        verify(ctx, times(1)).close();
        verify(nodeStopper, times(1)).stop(1);
    }

    @Test
    public void makeBlockRange() {
        BlockStore blockStore = mock(BlockStore.class);
        doReturn(5L).when(blockStore).getMinNumber();
        doReturn(10L).when(blockStore).getMaxNumber();

        try {
            IndexBlooms.makeBlockRange(new String[]{}, blockStore);
            fail();
        } catch (IllegalArgumentException ignored) { /* ignored */ }

        try {
            IndexBlooms.makeBlockRange(new String[]{"0"}, blockStore);
            fail();
        } catch (IllegalArgumentException ignored) { /* ignored */ }

        try {
            IndexBlooms.makeBlockRange(new String[]{"0", "abc"}, blockStore);
            fail();
        } catch (NumberFormatException ignored) { /* ignored */ }

        try {
            IndexBlooms.makeBlockRange(new String[]{"-1", "1"}, blockStore);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid 'from' and/or 'to' block number", e.getMessage());
        }

        try {
            IndexBlooms.makeBlockRange(new String[]{"2", "1"}, blockStore);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid 'from' and/or 'to' block number", e.getMessage());
        }

        doReturn(2L).when(blockStore).getMinNumber();

        try {
            IndexBlooms.makeBlockRange(new String[]{"1", "10"}, blockStore); // min block num is 10
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("'from' block number is lesser than the min block number stored", e.getMessage());
        }

        try {
            IndexBlooms.makeBlockRange(new String[]{"5", "11"}, blockStore); // best block num is 10
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("'to' block number is greater than the best block number", e.getMessage());
        }

        IndexBlooms.Range range = IndexBlooms.makeBlockRange(new String[]{"5", "10"}, blockStore);

        assertEquals(5, range.fromBlockNumber);
        assertEquals(10, range.toBlockNumber);
    }

    @Test
    public void indexBlooms() {
        Block block = mock(Block.class);
        BlockStore blockStore = mock(BlockStore.class);
        BlocksBloomStore blocksBloomStore = mock(BlocksBloomStore.class);
        ArgumentCaptor<BlocksBloom> captor = ArgumentCaptor.forClass(BlocksBloom.class);

        doReturn(new byte[Bloom.BLOOM_BYTES]).when(block).getLogBloom();
        doReturn(block).when(blockStore).getChainBlockByNumber(anyLong());
        doAnswer(i -> {
            long num = i.getArgument(0);
            return num - (num % 64);
        }).when(blocksBloomStore).firstNumberInRange(anyLong());
        doAnswer(i -> {
            long num = i.getArgument(0);
            return num - (num % 64) + 64 - 1;
        }).when(blocksBloomStore).lastNumberInRange(anyLong());

        IndexBlooms.execute(new IndexBlooms.Range(0, 63), blockStore, blocksBloomStore);

        verify(blocksBloomStore, times(1)).addBlocksBloom(captor.capture());
        verify(blockStore, times(64)).getChainBlockByNumber(anyLong());

        BlocksBloom blocksBloom = captor.getValue();
        assertEquals(0, blocksBloom.fromBlock());
        assertEquals(63, blocksBloom.toBlock());
        assertArrayEquals(new byte[Bloom.BLOOM_BYTES], blocksBloom.getBloom().getData());

        clearInvocations(blocksBloomStore, blockStore);

        IndexBlooms.execute(new IndexBlooms.Range(60, 300), blockStore, blocksBloomStore);

        // saved 3 block blooms in range [60..300]
        verify(blocksBloomStore, times(3)).addBlocksBloom(captor.capture());

        int i = 0;
        for (BlocksBloom bb : captor.getAllValues()) {
            assertEquals(i * 64L, bb.fromBlock());
            assertEquals(i * 64L + 63L, bb.toBlock());
            assertArrayEquals(new byte[Bloom.BLOOM_BYTES], bb.getBloom().getData());
            i++;
        }

        // [60..63] - ignored, [64..300] - processed
        // 192 (3*64) processed and saved, and 45 processed but not saved
        verify(blockStore, times(192 + 45)).getChainBlockByNumber(anyLong());
    }

    @Test
    public void generateOpenRpcDoc() throws IOException {
        String version = "1.1.1";

        ClassLoader classLoader = this.getClass().getClassLoader();
        File workDir = new File(classLoader.getResource("doc/rpc").getFile());
        File destFile = new File(tempFolder.getRoot(), "generated_openrpc.json");

        GenerateOpenRpcDoc generateOpenRpcDocCliTool = new GenerateOpenRpcDoc();

        String[] args = new String[]{version, workDir.getAbsolutePath(), destFile.getAbsolutePath()};

        try {
            generateOpenRpcDocCliTool.execute(args);
        } catch (RuntimeException e) {
            fail("should have not thrown " + e.getMessage());
        }

        ObjectMapper jsonMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);

        Object actual = jsonMapper.readValue(destFile, Object.class);

        File expectedResultFile = new File(classLoader.getResource("doc/rpc/expected_result.json").getFile());
        Object expected = jsonMapper.readValue(expectedResultFile, Object.class);

        assertEquals(expected, actual);
    }
}
