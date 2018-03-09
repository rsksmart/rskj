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

package co.rsk.test;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockChainImplTest;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.net.BlockNodeInformation;
import co.rsk.net.BlockStore;
import co.rsk.net.BlockSyncService;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.*;
import org.ethereum.db.ReceiptStore;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 8/7/2016.
 */
public class World {
    private BlockChainImpl blockChain;
    private NodeBlockProcessor blockProcessor;
    private BlockExecutor blockExecutor;
    private Map<String, Block> blocks = new HashMap<>();
    private Map<String, Account> accounts = new HashMap<>();
    private Map<String, Transaction> transactions = new HashMap<>();

    public World() {
        this(new BlockChainBuilder().build());
    }

    public World(Repository repository) {
        this(new BlockChainBuilder().setRepository(repository).build());
    }

    public World(ReceiptStore receiptStore) {
        this(new BlockChainBuilder().setReceiptStore(receiptStore).build());
    }

    public World(BlockChainImpl blockChain) {
        this(blockChain, null);
    }

    public World(BlockChainImpl blockChain, Genesis genesis) {
        this.blockChain = blockChain;

        if (genesis == null) {
            genesis = (Genesis) BlockChainImplTest.getGenesisBlock(blockChain);
            this.blockChain.setStatus(genesis, genesis.getCumulativeDifficulty());
        }

        this.saveBlock("g00", genesis);

        BlockStore store = new BlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        SyncConfiguration syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        TestSystemProperties config = new TestSystemProperties();
        BlockSyncService blockSyncService = new BlockSyncService(config, store, blockChain, nodeInformation, syncConfiguration);
        this.blockProcessor = new NodeBlockProcessor(store, blockChain, nodeInformation, blockSyncService, syncConfiguration);
    }

    public NodeBlockProcessor getBlockProcessor() { return this.blockProcessor; }

    public BlockExecutor getBlockExecutor() {
        if (this.blockExecutor == null)
            this.blockExecutor = new BlockExecutor(new TestSystemProperties(), this.getRepository(), null, this.getBlockChain().getBlockStore(), null);

        return this.blockExecutor;
    }

    public BlockChainImpl getBlockChain() { return this.blockChain; }

    public Block getBlockByName(String name) {
        return blocks.get(name);
    }

    public void saveBlock(String name, Block block) {
        blocks.put(name, block);
    }

    public Account getAccountByName(String name) { return accounts.get(name); }

    public void saveAccount(String name, Account account) { accounts.put(name, account); }

    public Transaction getTransactionByName(String name) { return transactions.get(name); }

    public void saveTransaction(String name, Transaction transaction) { transactions.put(name, transaction); }

    public Repository getRepository() {
        return this.blockChain.getRepository();
    }
}
