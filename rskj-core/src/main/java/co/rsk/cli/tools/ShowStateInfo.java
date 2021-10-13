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
import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.ethereum.util.ByteUtil;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;

/**
 * The entry point for show state info CLI util
 * This is an experimental/unsupported tool
 *
 * Required cli args:
 * - args[0] - block number or "best"
 */
public class ShowStateInfo extends CliToolRskContextAware {

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    private final Printer printer;

    @SuppressWarnings("unused")
    public ShowStateInfo() { // used via reflection
        this(ShowStateInfo::printInfo);
    }

    public ShowStateInfo(@Nonnull Printer printer) {
        this.printer = Objects.requireNonNull(printer);
    }

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) throws Exception {
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();

        printStateInfo(args, blockStore, trieStore);
    }

    private void printStateInfo(String[] args, BlockStore blockStore, TrieStore trieStore) {
        StateInfo stateInfo = new StateInfo();

        Block block;

        if ("best".equals(args[0])) {
            block = blockStore.getBestBlock();
        }
        else {
            block = blockStore.getChainBlockByNumber(Long.parseLong(args[0]));
        }

        printer.println("Block number: " + block.getNumber());
        printer.println("Block hash: " + ByteUtil.toHexString(block.getHash().getBytes()));
        printer.println("Block parent hash: " + ByteUtil.toHexString(block.getParentHash().getBytes()));
        printer.println("Block root hash: " + ByteUtil.toHexString(block.getStateRoot()));

        Optional<Trie> otrie = trieStore.retrieve(block.getStateRoot());

        if (otrie.isPresent()) {
            Trie trie = otrie.get();

            processTrie(trie, stateInfo);
        }

        printer.println("Trie nodes: " + stateInfo.nnodes);
        printer.println("Trie long values: " + stateInfo.nvalues);
        printer.println("Trie MB: " + (double) stateInfo.nbytes / (1024*1024));
    }

    private static void processTrie(Trie trie, StateInfo stateInfo) {
        stateInfo.nnodes++;
        stateInfo.nbytes += trie.getMessageLength();

        NodeReference leftReference = trie.getLeft();

        if (!leftReference.isEmpty()) {
            Optional<Trie> left = leftReference.getNode();

            if (left.isPresent()) {
                Trie leftTrie = left.get();

                if (!leftReference.isEmbeddable()) {
                    processTrie(leftTrie, stateInfo);
                }

                if (leftTrie.hasLongValue()) {
                    stateInfo.nvalues++;
                    stateInfo.nbytes += leftTrie.getValue().length;
                }
            }
        }

        NodeReference rightReference = trie.getRight();

        if (!rightReference.isEmpty()) {
            Optional<Trie> right = rightReference.getNode();

            if (right.isPresent()) {
                Trie rightTrie = right.get();

                if (!rightReference.isEmbeddable()) {
                    processTrie(rightTrie, stateInfo);
                }

                if (rightTrie.hasLongValue()) {
                    stateInfo.nvalues++;
                    stateInfo.nbytes += rightTrie.getValue().length;
                }
            }
        }

        if (trie.hasLongValue()) {
            stateInfo.nvalues++;
            stateInfo.nbytes += trie.getValue().length;
        }
    }

    @FunctionalInterface
    public interface Printer {
        void println(String x);
    }

    private static class StateInfo {
        private int nnodes;
        private int nvalues;
        private int nbytes;
    }
}
