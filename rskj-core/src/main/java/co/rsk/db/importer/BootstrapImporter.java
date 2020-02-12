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
import java.util.ArrayList;
import java.util.List;

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
        byte[] encodedData = bootstrapDataProvider.getBootstrapData();
        RLPList rlpElements = RLP.decodeList(encodedData);
        insertBlocks(blockStore, blockFactory, rlpElements.get(0));
        insertState(trieStore, rlpElements.get(1));
    }

    private static void insertState(TrieStore destinationTrieStore, RLPElement rlpElement) {
        RLPList statesData = RLP.decodeList(rlpElement.getRLPData());
        RLPList nodesData = RLP.decodeList(statesData.get(0).getRLPData());
        RLPList valuesData = RLP.decodeList(statesData.get(1).getRLPData());
        HashMapDB hashMapDB = new HashMapDB();
        TrieStoreImpl fakeStore = new TrieStoreImpl(hashMapDB);

        List<Trie> nodes = new ArrayList<>();

        for (int k = 0; k < nodesData.size(); k++) {
            RLPElement element = nodesData.get(k);
            byte[] rlpData = element.getRLPData();
            Trie trie = Trie.fromMessage(rlpData, fakeStore);
            hashMapDB.put(trie.getHash().getBytes(), rlpData);
            nodes.add(trie);
        };

        for (int k = 0; k < valuesData.size(); k++) {
            RLPElement element = valuesData.get(k);
            byte[] rlpData = element.getRLPData();
            hashMapDB.put(Keccak256Helper.keccak256(rlpData), rlpData);
        };

        nodes.forEach(destinationTrieStore::save);
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
            BlockDifficulty blockDifficulty = new BlockDifficulty(new BigInteger(tuple.get(1).getRLPData()));
            blockStore.saveBlock(block, blockDifficulty, true);
        }

        blockStore.flush();
    }
}
