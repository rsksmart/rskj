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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.db.HashMapBlocksIndex;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.trie.Trie;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;

import static co.rsk.core.BlockDifficulty.ZERO;
import static org.ethereum.TestUtils.randomHash;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 26/04/2020.
 */
public class CliToolsTest {
    @Test
    public void exportBlocks() throws FileNotFoundException, DslProcessorException, UnsupportedEncodingException {
        DslParser parser = DslParser.fromResource("dsl/blocks01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String[] args = new String[] { "0", "2" };

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final String utf8 = StandardCharsets.UTF_8.name();

        try (PrintStream ps = new PrintStream(baos, true, utf8)) {
            ExportBlocks.exportBlocks(args, world.getBlockStore(), ps);
        }

        String data = baos.toString(utf8);

        Blockchain blockchain = world.getBlockChain();
        BlockStore blockStore = world.getBlockStore();

        for (long n = 0; n < 3; n++) {
            Block block = blockchain.getBlockByNumber(n);
            BlockDifficulty totalDifficulty = blockStore.getTotalDifficultyForHash(block.getHash().getBytes());

            String line = block.getNumber() + "," + block.getHash().toHexString() + "," + ByteUtil.toHexString(totalDifficulty.getBytes()) + "," + ByteUtil.toHexString(block.getEncoded());

            Assert.assertTrue(data.indexOf(line) >= 0);
        }
    }

    @Test
    public void exportState() throws FileNotFoundException, DslProcessorException, UnsupportedEncodingException {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String[] args = new String[] { "2" };

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final String utf8 = StandardCharsets.UTF_8.name();

        try (PrintStream ps = new PrintStream(baos, true, utf8)) {
            ExportState.exportState(args, world.getBlockStore(), world.getTrieStore(), ps);
        }

        String data = baos.toString(utf8);

        Block block = world.getBlockByName("b02");

        Optional<Trie> otrie = world.getTrieStore().retrieve(block.getStateRoot());

        Assert.assertTrue(otrie.isPresent());

        Trie trie = otrie.get();

        byte[] encoded = trie.toMessage();

        String line = ByteUtil.toHexString(encoded);

        Assert.assertTrue(data.indexOf(line) >= 0);
    }

    @Test
    public void showStateInfo() throws FileNotFoundException, DslProcessorException, UnsupportedEncodingException {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String[] args = new String[] { "best" };

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final String utf8 = StandardCharsets.UTF_8.name();

        try (PrintStream ps = new PrintStream(baos, true, utf8)) {
            ShowStateInfo.printStateInfo(args, world.getBlockStore(), world.getTrieStore(), ps::println);
        }

        String data = baos.toString(utf8);

        Block block = world.getBlockByName("b02");

        String blockLine = "Block hash: " + ByteUtil.toHexString(block.getHash().getBytes());

        Assert.assertTrue(data.indexOf(blockLine) >= 0);

        String longValueLine = "Trie long values: 1";

        Assert.assertTrue(data.indexOf(longValueLine) >= 0);
    }

    @Test
    public void executeBlocks() throws FileNotFoundException, DslProcessorException, UnsupportedEncodingException {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String[] args = new String[] { "1", "2" };

        ExecuteBlocks.executeBlocks(args, world.getBlockExecutor(), world.getBlockStore(), world.getTrieStore(),
                world.getStateRootHandler());

        Assert.assertEquals(2, world.getBlockChain().getBestBlock().getNumber());
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

        BufferedReader reader = new BufferedReader(new StringReader(stringBuilder.toString()));

        ConnectBlocks.connectBlocks(
                new BlockFactory(ActivationConfigsForTest.all()),
                blockchain,
                world.getTrieStore(),
                world.getBlockStore(),
                receiptStore,
                reader);

        Assert.assertEquals(2, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(block1.getHash(), blockchain.getBlockByNumber(1).getHash());
        Assert.assertEquals(block2.getHash(), blockchain.getBlockByNumber(2).getHash());
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

        BufferedReader reader = new BufferedReader(new StringReader(stringBuilder.toString()));

        ImportBlocks.importBlocks(
                new BlockFactory(ActivationConfigsForTest.all()),
                world.getBlockStore(),
                reader);

        Assert.assertEquals(block1.getHash(), blockchain.getBlockByNumber(1).getHash());
        Assert.assertEquals(block2.getHash(), blockchain.getBlockByNumber(2).getHash());
    }

    @Test
    public void importState() throws IOException {
        HashMapDB trieDB = new HashMapDB();
        trieDB.setClearOnClose(false);
        byte[] value = new byte[42];
        Random random = new Random();
        random.nextBytes(value);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(ByteUtil.toHexString(value));
        stringBuilder.append("\n");

        BufferedReader reader = new BufferedReader(new StringReader(stringBuilder.toString()));

        ImportState.importState(reader, trieDB);

        byte[] key = new Keccak256(Keccak256Helper.keccak256(value)).getBytes();
        byte[] result = trieDB.get(key);

        Assert.assertNotNull(result);
        Assert.assertArrayEquals(value, result);
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
        RewindBlocks.rewindBlocks(blockToRewind, indexedBlockStore);

        bestBlock = indexedBlockStore.getBestBlock();
        assertThat(bestBlock.getNumber(), is(blockToRewind));
    }
}
