/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.core;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.PendingStateImpl;
import co.rsk.db.RepositoryImpl;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.config.blockchain.GenesisConfig;
import org.ethereum.config.net.MainNetConfig;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.manager.AdminInfo;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.math.BigInteger;
import java.util.HashMap;

/**
 * Created by Anton Nashatyrev on 29.12.2015.
 */
public class ImportLightTest {

    @BeforeClass
    public static void setup() {
        RskSystemProperties.CONFIG.setBlockchainConfig(new GenesisConfig(new GenesisConfig.GenesisConstants() {
            @Override
            public BigInteger getMinimumDifficulty() {
                return BigInteger.ONE;
            }
        }));
    }

    @AfterClass
    public static void cleanup() {
        RskSystemProperties.CONFIG.setBlockchainConfig(MainNetConfig.INSTANCE);
    }

    public static BlockChainImpl createBlockchain(Genesis genesis) {
        IndexedBlockStore blockStore = new IndexedBlockStore();
        blockStore.init(new HashMap<>(), new HashMapDB(), null);

        Repository repository = new RepositoryImpl(new TrieStoreImpl(new HashMapDB()));

        EthereumListenerAdapter listener = new EthereumListenerAdapter();

        KeyValueDataSource ds = new HashMapDB();
        ds.init();
        ReceiptStore receiptStore = new ReceiptStoreImpl(ds);

        BlockChainImpl blockchain = new BlockChainImpl(
                repository,
                blockStore,
                receiptStore,
                null,
                listener,
                new AdminInfo(),
                new DummyBlockValidator()
        );

        blockchain.setNoValidation(true);

        PendingStateImpl pendingState = new PendingStateImpl(blockchain, null, null, null, listener, 10, 100);

        blockchain.setPendingState(pendingState);

        Repository track = repository.startTracking();
        for (ByteArrayWrapper key : genesis.getPremine().keySet()) {
            track.createAccount(key.getData());
            track.addBalance(key.getData(), genesis.getPremine().get(key).getAccountState().getBalance());
        }

        track.commit();

        genesis.setStateRoot(repository.getRoot());
        genesis.flushRLP();

        blockStore.saveBlock(genesis, genesis.getCumulativeDifficulty(), true);

        blockchain.setBestBlock(genesis);
        blockchain.setTotalDifficulty(genesis.getCumulativeDifficulty());

        return blockchain;
    }
}

