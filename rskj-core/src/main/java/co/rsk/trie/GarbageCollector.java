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

package co.rsk.trie;

import co.rsk.db.StateRootHandler;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.util.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class GarbageCollector implements TrieStore {
    private final CompositeEthereumListener emitter;
    private final StateRootHandler stateRootHandler;
    private final String databaseDir;

    private final OnBestBlockListener listener = new OnBestBlockListener();
    private KeyValueDataSource eden;
    private KeyValueDataSource gen1;
    private KeyValueDataSource gen2;

    public GarbageCollector(CompositeEthereumListener emitter, StateRootHandler stateRootHandler, String databaseDir) {
        this.emitter = emitter;
        this.stateRootHandler = stateRootHandler;
        this.databaseDir = databaseDir;
        this.eden = new LevelDbDataSource("unitrie_eden", databaseDir);
        this.gen1 = new LevelDbDataSource("unitrie_gen1", databaseDir);
        this.gen2 = new LevelDbDataSource("unitrie_gen2", databaseDir);
//    }
//
//    public void start() {
        eden.init();
        gen1.init();
        gen2.init();
        emitter.addListener(listener);
//    }
//
//    public void stop() {
//        emitter.removeListener(listener);
//        gen2.close();
//        gen1.close();
//        eden.close();
    }

    public void hey() {

    }

    @Override
    public void save(Trie trie) {
        eden.put(trie.getHash().getBytes(), trie.toMessage());
        if (trie.hasLongValue()) {
            saveValue(trie);
        }
    }

    @Override
    public void saveValue(Trie trie) {
        eden.put(trie.getValueHash().getBytes(), trie.getValue());
    }

    @Override
    public void flush() {
        // this will be ignored because we're handling flushing behavior on each block
    }

    @Override
    public Trie retrieve(byte[] hash) {
        byte[] message = retrieveValue(hash);

        return Trie.fromMessage(message, this);
    }

    @Override
    public byte[] retrieveValue(byte[] hash) {
        byte[] edenData = eden.get(hash);
        if (edenData != null) {
            return edenData;
        }

        byte[] gen1Data = gen1.get(hash);
        if (gen1Data != null) {
            return gen1Data;
        }

        return gen2.get(hash);
    }

    private void internalFlush(Block bestBlock) {
        eden.flush();

        long blockNumber = bestBlock.getNumber();
        if (!isFrontier(blockNumber)) {
            return;
        }

        Trie bestTrie = retrieve(stateRootHandler.translate(bestBlock.getHeader()).getBytes());
        bestTrie.save(new TrieStoreImpl(gen1), true);
        gen1.flush();

        eden.close();
        gen1.close();
        gen2.close();

        try {
            FileUtil.recursiveDelete(FileUtil.getDatabaseDirectoryPath(databaseDir, "unitrie_gen2").toString());
            Files.move(
                    Paths.get(databaseDir, "unitrie_gen1"),
                    Paths.get(databaseDir, "unitrie_gen2"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.move(
                    Paths.get(databaseDir, "unitrie_eden"),
                    Paths.get(databaseDir, "unitrie_gen1"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }

        eden = new LevelDbDataSource("unitrie_eden", databaseDir);
        gen1 = new LevelDbDataSource("unitrie_gen1", databaseDir);
        gen2 = new LevelDbDataSource("unitrie_gen2", databaseDir);
        eden.init();
        gen1.init();
        gen2.init();
    }

    private boolean isFrontier(long blockNumber) {
        return blockNumber != 0 && blockNumber % 100 == 0;
    }

    private class OnBestBlockListener extends EthereumListenerAdapter {
        @Override
        public void onBestBlock(Block bestBlock, List<TransactionReceipt> receipts) {
            internalFlush(bestBlock);
        }
    }
}

