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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.TrieConverter;
import co.rsk.trie.TrieStore;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.TestCompositeEthereumListener;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Anton Nashatyrev on 29.12.2015.
 */
public class ImportLightTest {

    public static BlockChainImpl createBlockchain(
            Genesis genesis,
            TestSystemProperties config,
            Repository repository,
            BlockStore blockStore,
            TrieStore trieStore) {
        BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
        CompositeEthereumListener listener = new TestCompositeEthereumListener();

        KeyValueDataSource ds = new HashMapDB();
        ds.init();
        ReceiptStore receiptStore = new ReceiptStoreImpl(ds);
        ReceivedTxSignatureCache receivedTxSignatureCache = new ReceivedTxSignatureCache();
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(receivedTxSignatureCache);

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                config,
                blockStore,
                receiptStore,
                blockFactory,
                new ProgramInvokeFactoryImpl(),
                null,
                blockTxSignatureCache);
        StateRootHandler stateRootHandler = new StateRootHandler(config.getActivationConfig(), new TrieConverter(), new HashMapDB(), new HashMap<>());
        RepositoryLocator repositoryLocator = new RepositoryLocator(trieStore, stateRootHandler);

        TransactionPoolImpl transactionPool = new TransactionPoolImpl(config, repositoryLocator, null, blockFactory, listener, transactionExecutorFactory, receivedTxSignatureCache, 10, 100);

        BlockChainImpl blockchain = new BlockChainImpl(
                blockStore,
                receiptStore,
                transactionPool,
                listener,
                new DummyBlockValidator(),
                new BlockExecutor(
                        config.getActivationConfig(),
                        repositoryLocator,
                        stateRootHandler,
                        transactionExecutorFactory
                ),
                stateRootHandler
        );

        blockchain.setNoValidation(true);

        Repository track = repository.startTracking();

        for (Map.Entry<RskAddress, AccountState> accountsEntry : genesis.getAccounts().entrySet()) {
            RskAddress accountAddress = accountsEntry.getKey();
            track.createAccount(accountAddress);
            track.addBalance(accountAddress, accountsEntry.getValue().getBalance());
        }

        track.commit();

        genesis.setStateRoot(repository.getRoot());
        genesis.flushRLP();

        blockStore.saveBlock(genesis, genesis.getCumulativeDifficulty(), true);
        blockchain.setStatus(genesis, genesis.getCumulativeDifficulty());

        return blockchain;
    }
}

