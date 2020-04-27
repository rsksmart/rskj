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
package co.rsk;

import co.rsk.core.BlockDifficulty;
import co.rsk.net.BlockSyncService;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.trie.Trie;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockStore;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

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

            String line = block.getNumber() + "," + block.getHash().toHexString() + "," + Hex.toHexString(totalDifficulty.getBytes()) + "," + Hex.toHexString(block.getEncoded());

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

        String line = Hex.toHexString(encoded);

        Assert.assertTrue(data.indexOf(line) >= 0);
    }
}
