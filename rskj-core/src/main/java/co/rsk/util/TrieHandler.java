/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
package co.rsk.util;

import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class TrieHandler {

    private static final Logger logger = LoggerFactory.getLogger(TrieHandler.class);

    private final KeyValueDataSource destDataSource;
    private final Set<Keccak256> alreadySaved;

    private final Map<ByteArrayWrapper, byte[]> entriesToCopy;

    private final int batchSize;

    public TrieHandler(@Nonnull KeyValueDataSource destDataSource, int cacheSize, int batchSize) {
        this.destDataSource = Objects.requireNonNull(destDataSource);
        this.alreadySaved = cacheSize > 0 ? Collections.newSetFromMap(new MaxSizeHashMap<>(cacheSize, true)) : null;
        this.entriesToCopy = batchSize > 0 ? new HashMap<>(batchSize) : null;
        this.batchSize = batchSize;
    }

    public void copyTrie(long blockHeight, @Nonnull Trie trie) {
        Keccak256 tryHash = trie.getHash();
        logger.info("Processing trie at block height: {} with hash: {}", blockHeight, tryHash);

        Stats stats = new Stats(trie.getChildrenSize().value);

        if (destDataSource.get(tryHash.getBytes()) != null) {
            stats.addProcessed(trie.getChildrenSize().value);
            stats.incSkippedNodes();
            printUpdateIfNeeded(stats);
            return;
        }

        processTrieNode(trie, stats);

        if (trie.hasLongValue()) {
            put(trie.getValueHash().getBytes(), trie.getValue());
        }

        flushToDestDataSource();

        printUpdateIfNeeded(stats);

        logger.info("Finished processing trie at block height: {} with hash: {}; {}", blockHeight, tryHash, stats);
    }

    private void put(byte[] key, byte[] value) {
        if (entriesToCopy == null) {
            destDataSource.put(key, value);
            return;
        }

        if (entriesToCopy.size() == batchSize) {
            flushToDestDataSource();
        }

        entriesToCopy.put(new ByteArrayWrapper(key), value);
    }

    private void flushToDestDataSource() {
        if (entriesToCopy == null || entriesToCopy.isEmpty()) {
            return;
        }
        destDataSource.updateBatch(entriesToCopy, Collections.emptySet());
        entriesToCopy.clear();
    }

    private static long getChildrenOnlySize(@Nonnull Trie trieNode, @Nullable Trie leftNode, @Nullable Trie rightNode) {
        long childrenTotalSize = trieNode.getChildrenSize().value;
        if (leftNode != null) {
            childrenTotalSize -= leftNode.getChildrenSize().value;
        }
        if (rightNode != null) {
            childrenTotalSize -= rightNode.getChildrenSize().value;
        }

        return childrenTotalSize;
    }

    private void processTrieNode(@Nonnull Trie trieNode, @Nullable Stats stats) {
        Keccak256 trieHash = trieNode.getHash();
        byte[] trieKeyBytes = trieHash.getBytes();

        Optional<Trie> leftNode = trieNode.getLeftNodeDetached();
        Optional<Trie> rightNode = trieNode.getRightNodeDetached();

        if (alreadySaved != null && alreadySaved.contains(trieHash)) {
            if (stats != null) {
                stats.addProcessed(trieNode.getChildrenSize().value);
                stats.incSkippedNodes();
                printUpdateIfNeeded(stats);
            }
            return;
        }

        leftNode.ifPresent(trie -> processChildNode(trie, stats));
        rightNode.ifPresent(trie -> processChildNode(trie, stats));

        put(trieKeyBytes, trieNode.toMessage());

        if (alreadySaved != null) {
            alreadySaved.add(trieHash);
        }

        if (stats != null) {
            stats.addProcessed(getChildrenOnlySize(trieNode, leftNode.orElse(null), rightNode.orElse(null)));
        }
    }

    private void processChildNode(@Nonnull Trie childNode, @Nullable Stats stats) {
        if (!childNode.isEmbeddable()) {
            processTrieNode(childNode, stats);
            if (stats != null) {
                printUpdateIfNeeded(stats);
            }
        }

        if (childNode.hasLongValue()) {
            put(childNode.getValueHash().getBytes(), childNode.getValue());
        }
    }

    private static void printUpdateIfNeeded(@Nonnull Stats stats) {
        String update = stats.getUpdate();
        if (update != null) {
            logger.info("{}", update);
        }
    }

    private static class Stats {

        private final long expected;
        private long processed;
        private long percents;
        private long skippedNodes;

        public Stats(long expected) {
            this.expected = expected;
        }

        @Nullable
        String getUpdate() {
            long p = 100 * processed / expected;
            if (p > percents) {
                percents = p;
                return toString();
            }

            return null;
        }

        void addProcessed(long processed) {
            this.processed += processed;
        }

        void incSkippedNodes() {
            skippedNodes++;
        }

        @Override
        public String toString() {
            return "Stats{"
                    + " expected bytes: " + expected
                    + ", percents: " + percents + "%"
                    + ", processed bytes: " + processed
                    + ", skipped nodes: " + skippedNodes
                    + " }";
        }
    }

}
