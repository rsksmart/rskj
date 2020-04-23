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

import java.util.Optional;

/**
 * The entrypoint for export state CLI util
 */
public class ExportState {
    public static void main(String[] args) {
        RskContext ctx = new RskContext(args);
        BlockStore blockStore = ctx.getBlockStore();

        long blockNumber = Long.parseLong(args[0]);

        Block block = blockStore.getChainBlockByNumber(blockNumber);

        TrieStore trieStore = ctx.getTrieStore();

        Trie trie = trieStore.retrieve(block.getStateRoot()).get();

        processTrie(trie);
    }

    private static void processTrie(Trie trie) {
        System.out.println(Hex.toHexString(trie.toMessage()));

        NodeReference leftReference = trie.getLeft();

        if (!leftReference.isEmpty()) {
            Optional<Trie> left = leftReference.getNode();

            if (left.isPresent()) {
                Trie leftTrie = left.get();

                if (!leftReference.isEmbeddable()) {
                    processTrie(leftTrie);
                }

                if (leftTrie.hasLongValue()) {
                    System.out.println(Hex.toHexString(leftTrie.getValue()));
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
                    System.out.println(Hex.toHexString(rightTrie.getValue()));
                }
            }
        }

        if (trie.hasLongValue()) {
            System.out.println(Hex.toHexString(trie.getValue()));
        }
    }
}
