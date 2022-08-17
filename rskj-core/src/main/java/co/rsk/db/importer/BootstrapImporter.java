/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.db.importer;

import co.rsk.core.BlockDifficulty;
import co.rsk.db.StateRootsStore;
import co.rsk.db.importer.provider.BootstrapDataProvider;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

public class BootstrapImporter {

    private static final Logger logger = LoggerFactory.getLogger(BootstrapImporter.class);

    private final BootstrapDataProvider bootstrapDataProvider;
    private final BlockStore blockStore;
    private final TrieStore trieStore;
    private final BlockFactory blockFactory;

    public BootstrapImporter(
            BlockStore blockStore,
            TrieStore trieStore,
            BlockFactory blockFactory, BootstrapDataProvider bootstrapDataProvider) {
        this.blockStore = blockStore;
        this.trieStore = trieStore;
        this.blockFactory = blockFactory;
        this.bootstrapDataProvider = bootstrapDataProvider;
    }

    public void importData() {
        long start = System.currentTimeMillis();

        bootstrapDataProvider.retrieveData();
        updateDatabase();

        long durationInMills = System.currentTimeMillis() - start;
        logger.info("Bootstrap data has successfully been imported in {} mills", durationInMills);
    }

    private void updateDatabase() {
        Queue<RLPElement> rlpElementQueue = decodeQueue(bootstrapDataProvider.getBootstrapData());

        long start = System.currentTimeMillis();
        logger.debug("Inserting blocks...");
        insertBlocks(blockStore, blockFactory, Objects.requireNonNull(rlpElementQueue.poll()));
        logger.debug("Blocks have been inserted in {} mills", System.currentTimeMillis() - start);

        HashMapDB hashMapDB = new HashMapDB();
        Queue<byte[]> nodeDataQueue = new LinkedList<>();
        Queue<byte[]> nodeValueQueue = new LinkedList<>();
        Queue<Trie> trieQueue = new LinkedList<>();

        start = System.currentTimeMillis();
        logger.debug("Preparing state for insertion...");
        fillUpRlpDataQueues(nodeDataQueue, nodeValueQueue, Objects.requireNonNull(rlpElementQueue.poll()));
        fillUpTrieQueue(trieQueue, nodeDataQueue, nodeValueQueue, hashMapDB,null); // TODO: set a valid StateRootsStore here
        logger.debug("State has been prepared in {} mills", System.currentTimeMillis() - start);

        start = System.currentTimeMillis();
        logger.debug("Inserting state...");
        insertState(trieStore, trieQueue);
        logger.debug("State has been inserted in {} mills", System.currentTimeMillis() - start);
    }

    private static void insertBlocks(BlockStore blockStore,
                                     BlockFactory blockFactory,
                                     RLPElement encodedTuples) {
        RLPList blocksData = RLP.decodeList(encodedTuples.getRLPData());

        for (int k = 0; k < blocksData.size(); k++) {
            RLPElement element = blocksData.get(k);
            RLPList blockData = RLP.decodeList(element.getRLPData());
            RLPList tuple = RLP.decodeList(blockData.getRLPData());
            Block block = blockFactory.decodeBlock(Objects.requireNonNull(tuple.get(0).getRLPData(), "block data is missing"));
            BlockDifficulty blockDifficulty = new BlockDifficulty(new BigInteger(Objects.requireNonNull(tuple.get(1).getRLPData(), "block difficulty data is missing")));
            blockStore.saveBlock(block, blockDifficulty, true);
        }

        blockStore.flush();
    }

    private static void fillUpRlpDataQueues(Queue<byte[]> nodeDataQueue, Queue<byte[]> nodeValueQueue, RLPElement rlpElement) {
        Queue<RLPElement> nodeListQueue = decodeQueue(rlpElement.getRLPData());

        fillUpRlpDataQueue(nodeDataQueue, RLP.decodeList(Objects.requireNonNull(nodeListQueue.poll()).getRLPData()));
        fillUpRlpDataQueue(nodeValueQueue, RLP.decodeList(Objects.requireNonNull(nodeListQueue.poll()).getRLPData()));
    }

    private static void fillUpRlpDataQueue(Queue<byte[]> rlpDataQueue, RLPList nodesData) {
        int size = nodesData.size();
        for (int k = 0; k < size; k++) {
            RLPElement element = nodesData.get(k);
            byte[] rlpData = Objects.requireNonNull(element.getRLPData());

            rlpDataQueue.add(rlpData);
        }
    }

    private static void fillUpTrieQueue(Queue<Trie> trieQueue,
                                        Queue<byte[]> nodeDataQueue, Queue<byte[]> nodeValueQueue,
                                        HashMapDB hashMapDB,
                                        StateRootsStore rootStore) {
        TrieStoreImpl fakeStore = new TrieStoreImpl(hashMapDB,rootStore);

        for (byte[] nodeData = nodeDataQueue.poll(); nodeData != null; nodeData = nodeDataQueue.poll()) {
            Trie trie = Trie.fromMessage(nodeData, fakeStore);
            hashMapDB.put(trie.getHash().getBytes(), nodeData);
            trieQueue.add(trie);
        }

        for (byte[] nodeValue = nodeValueQueue.poll(); nodeValue != null; nodeValue = nodeValueQueue.poll()) {
            hashMapDB.put(Keccak256Helper.keccak256(nodeValue), nodeValue);
        }
    }

    private static void insertState(TrieStore destinationTrieStore, Queue<Trie> trieQueue) {
        for (Trie trie = trieQueue.poll(); trie != null; trie = trieQueue.poll()) {
            destinationTrieStore.save(trie);
        }
    }

    private static Queue<RLPElement> decodeQueue(byte[] data) {
        RLPList rlpList = RLP.decodeList(data);
        int size = rlpList.size();

        Queue<RLPElement> result = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            result.add(rlpList.get(i));
        }

        return result;
    }
}
