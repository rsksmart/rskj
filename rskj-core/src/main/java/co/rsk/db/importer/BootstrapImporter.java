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

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

public class BootstrapImporter {

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
        bootstrapDataProvider.retrieveData();
        updateDatabase();
    }

    private void updateDatabase() {
        Queue<RLPElement> rlpElementQueue = decodeQueue(bootstrapDataProvider.getBootstrapData());

        insertBlocks(blockStore, blockFactory, Objects.requireNonNull(rlpElementQueue.poll()));

        HashMapDB hashMapDB = new HashMapDB();
        Queue<byte[]> nodeDataQueue = new LinkedList<>();

        prepareState(nodeDataQueue, hashMapDB, Objects.requireNonNull(rlpElementQueue.poll()));
        insertState(trieStore, nodeDataQueue, hashMapDB);
    }

    private static void insertBlocks(BlockStore blockStore,
                                     BlockFactory blockFactory,
                                     RLPElement encodedTuples) {
        RLPList blocksData = RLP.decodeList(encodedTuples.getRLPData());

        for (int k = 0; k < blocksData.size(); k++) {
            RLPElement element = blocksData.get(k);
            RLPList blockData = RLP.decodeList(element.getRLPData());
            RLPList tuple = RLP.decodeList(blockData.getRLPData());
            Block block = blockFactory.decodeBlock(tuple.get(0).getRLPData());
            BlockDifficulty blockDifficulty = new BlockDifficulty(new BigInteger(Objects.requireNonNull(tuple.get(1).getRLPData())));
            blockStore.saveBlock(block, blockDifficulty, true);
        }

        blockStore.flush();
    }

    private static void prepareState(Queue<byte[]> nodeDataQueue, HashMapDB hashMapDB, RLPElement rlpElement) {
        Queue<RLPElement> nodeListQueue = decodeQueue(rlpElement.getRLPData());

        prepareNodeData(nodeDataQueue, hashMapDB, RLP.decodeList(Objects.requireNonNull(nodeListQueue.poll()).getRLPData()));
        prepareNodeValues(hashMapDB, RLP.decodeList(Objects.requireNonNull(nodeListQueue.poll()).getRLPData()));
    }

    private static void prepareNodeData(Queue<byte[]> rlpDataQueue, HashMapDB hashMapDB, RLPList nodesData) {
        int size = nodesData.size();
        for (int k = 0; k < size; k++) {
            RLPElement element = nodesData.get(k);
            byte[] rlpData = Objects.requireNonNull(element.getRLPData());

            hashMapDB.put(Keccak256Helper.keccak256(rlpData), rlpData);
            rlpDataQueue.add(rlpData);
        }
    }

    private static void prepareNodeValues(HashMapDB hashMapDB, RLPList nodeValues) {
        int size = nodeValues.size();
        for (int k = 0; k < size; k++) {
            RLPElement element = nodeValues.get(k);
            byte[] rlpData = element.getRLPData();
            hashMapDB.put(Keccak256Helper.keccak256(rlpData), rlpData);
        }
    }

    private static void insertState(TrieStore destinationTrieStore, Queue<byte[]> nodeDataQueue, HashMapDB hashMapDB) {
        TrieStoreImpl fakeStore = new TrieStoreImpl(hashMapDB);
        for (byte[] nodeData = nodeDataQueue.poll(); nodeData != null; nodeData = nodeDataQueue.poll()) {
            Trie trie = Trie.fromMessage(nodeData, fakeStore);

            destinationTrieStore.save(trie);
        }
    }

    private static Queue<RLPElement> decodeQueue(byte[] data) {
        RLPList rlpList = RLP.decodeList(data);
        int size = rlpList.size();

        LinkedList<RLPElement> result = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            result.add(rlpList.get(i));
        }

        return result;
    }
}
