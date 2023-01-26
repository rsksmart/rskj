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

import co.rsk.cli.PicoCliToolRskContextAware;
import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.ethereum.util.ByteUtil;
import picocli.CommandLine;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

/**
 * The entry point for export state CLI tool
 * This is an experimental/unsupported tool
 *
 * Required cli args:
 * - args[0] - block number
 * - args[1] - file path
 */
@CommandLine.Command(name = "export-state", mixinStandardHelpOptions = true, version = "export-state 1.0",
        description = "Exports state at specific block number to a file")
public class ExportState extends PicoCliToolRskContextAware {
    @CommandLine.Option(names = {"-b", "--block"}, description = "Block number", required = true)
    private Long blockNumber;

    @CommandLine.Option(names = {"-f", "--file"}, description = "Path to a file to export state to", required = true)
    private String filePath;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();

        try (PrintStream writer = new PrintStream(new BufferedOutputStream(new FileOutputStream(filePath)))) {
            exportState(blockStore, trieStore, writer);
        }

        return 0;
    }

    private void exportState(BlockStore blockStore, TrieStore trieStore, PrintStream writer) {
        Block block = blockStore.getChainBlockByNumber(blockNumber);

        Optional<Trie> otrie = trieStore.retrieve(block.getStateRoot());

        if (!otrie.isPresent()) {
            return;
        }

        Trie trie = otrie.get();

        processTrie(trie, writer);
    }

    private static void processTrie(Trie trie, PrintStream writer) {
        writer.println(ByteUtil.toHexString(trie.toMessage()));

        NodeReference leftReference = trie.getLeft();

        if (!leftReference.isEmpty()) {
            Optional<Trie> left = leftReference.getNode();

            if (left.isPresent()) {
                Trie leftTrie = left.get();

                if (!leftReference.isEmbeddable()) {
                    processTrie(leftTrie, writer);
                }

                if (leftTrie.hasLongValue()) {
                    writer.println(ByteUtil.toHexString(leftTrie.getValue()));
                }
            }
        }

        NodeReference rightReference = trie.getRight();

        if (!rightReference.isEmpty()) {
            Optional<Trie> right = rightReference.getNode();

            if (right.isPresent()) {
                Trie rightTrie = right.get();

                if (!rightReference.isEmbeddable()) {
                    processTrie(rightTrie, writer);
                }

                if (rightTrie.hasLongValue()) {
                    writer.println(ByteUtil.toHexString(rightTrie.getValue()));
                }
            }
        }

        if (trie.hasLongValue()) {
            writer.println(ByteUtil.toHexString(trie.getValue()));
        }
    }
}
