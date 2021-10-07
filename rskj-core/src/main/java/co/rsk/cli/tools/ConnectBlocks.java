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
import co.rsk.cli.CliToolRskContextAware;
import co.rsk.trie.TrieStore;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

/**
 * The entry point for connect blocks CLI tool
 * This is an experimental/unsupported tool
 *
 * Required cli args:
 * - args[0] - file path
 */
public class ConnectBlocks extends CliToolRskContextAware {

    public static void main(String[] args) throws IOException {
        execute(args, MethodHandles.lookup().lookupClass());
    }

    public static void connectBlocks(BlockFactory blockFactory, Blockchain blockchain, TrieStore trieStore, BlockStore blockStore, ReceiptStore receiptStore, BufferedReader reader) throws IOException {
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            String[] parts = line.split(",");

            if (parts.length < 4) {
                continue;
            }

            byte[] encoded = Hex.decode(parts[3]);

            Block block = blockFactory.decodeBlock(encoded);
            block.seal();

            blockchain.tryToConnect(block);
        }

        blockStore.flush();
        trieStore.flush();
        receiptStore.flush();
    }

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) throws Exception {
        BlockFactory blockFactory = ctx.getBlockFactory();
        Blockchain blockchain = ctx.getBlockchain();
        TrieStore trieStore = ctx.getTrieStore();
        BlockStore blockStore = ctx.getBlockStore();
        ReceiptStore receiptStore = ctx.getReceiptStore();

        String filename = args[0];

        long startTime = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            connectBlocks(blockFactory, blockchain, trieStore, blockStore, receiptStore, reader);
        }

        long endTime = System.currentTimeMillis();

        logger.info("Duration: " + (endTime - startTime) + " millis");
    }
}
