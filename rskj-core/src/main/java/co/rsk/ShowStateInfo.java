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

import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

import java.io.PrintStream;
import java.util.Optional;

/**
 * The entry point for show state info CLI util
 */
public class ShowStateInfo {
    private static int nnodes;
    private static int nvalues;
    private static int nbytes;

    public static void main(String[] args) {
        RskContext ctx = new RskContext(args);
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();

        execute(args, blockStore, trieStore, System.out);
    }
    
    public static void execute(String[] args, BlockStore blockStore, TrieStore trieStore, PrintStream writer) {
        Block block;

        if ("best".equals(args[0])) {
            block = blockStore.getBestBlock();
        }
        else {
            block = blockStore.getChainBlockByNumber(Long.parseLong(args[0]));
        }

        writer.println("Block number: " + block.getNumber());
        writer.println("Block hash: " + Hex.toHexString(block.getHash().getBytes()));
        writer.println("Block parent hash: " + Hex.toHexString(block.getParentHash().getBytes()));
        writer.println("Block root hash: " + Hex.toHexString(block.getStateRoot()));

        Trie trie = trieStore.retrieve(block.getStateRoot()).get();

        processTrie(trie);

        writer.println("Trie nodes: " + nnodes);
        writer.println("Trie long values: " + nvalues);
        writer.println("Trie MB: " + (double)nbytes / (1024*1024));
    }

    private static void processTrie(Trie trie) {
        nnodes++;
        nbytes += trie.getMessageLength();

        NodeReference leftReference = trie.getLeft();

        if (!leftReference.isEmpty()) {
            Optional<Trie> left = leftReference.getNode();

            if (left.isPresent()) {
                Trie leftTrie = left.get();

                if (!leftReference.isEmbeddable()) {
                    processTrie(leftTrie);
                }

                if (leftTrie.hasLongValue()) {
                    nvalues++;
                    nbytes += leftTrie.getValue().length;
                }
            }
        }

        NodeReference rightReference = trie.getRight();

        if (!rightReference.isEmpty()) {
            Optional<Trie> right = rightReference.getNode();

            if (right.isPresent()) {
                Trie rightTrie = right.get();

                if (!rightReference.isEmbeddable()) {
                    processTrie(rightTrie);
                }

                if (rightTrie.hasLongValue()) {
                    nvalues++;
                    nbytes += rightTrie.getValue().length;
                }
            }
        }

        if (trie.hasLongValue()) {
            nvalues++;
            nbytes += trie.getValue().length;
        }
    }
}
