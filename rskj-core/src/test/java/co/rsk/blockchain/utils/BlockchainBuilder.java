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

package co.rsk.blockchain.utils;

import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockValidatorBuilder;
import co.rsk.core.bc.PendingStateImpl;
import co.rsk.db.RepositoryImpl;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.*;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.manager.AdminInfo;

import java.util.HashMap;
import java.util.List;

/**
 * Created by ajlopez on 5/13/2016.
 */
public class BlockchainBuilder {
    private Blockchain blockchain;
    private List<Block> blocks;
    private List<TransactionInfo> txinfos;
    private boolean testing;
    private boolean rsk;
    Repository repository;
    ReceiptStore receiptStore;
    BlockStore blockStore;
    Genesis genesis;

    public BlockchainBuilder() {

    }

    public BlockchainBuilder setGenesis(Genesis genesis) {
        this.genesis = genesis;
        return this;
    }

    public BlockchainBuilder setTesting(boolean value) {
        this.testing = value;
        return this;
    }

    public BlockchainBuilder setRsk(boolean value) {
        this.rsk = value;
        return this;
    }

    public BlockchainBuilder setBlocks(List<Block> blocks) {
        this.blocks = blocks;
        return this;
    }

    public BlockchainBuilder setTransactionInfos(List<TransactionInfo> txinfos) {
        this.txinfos = txinfos;
        return this;
    }
    public BlockchainBuilder setBlockStore(BlockStore blockStore) {
        this.blockStore = blockStore;
        return this;
    }
    public BlockchainBuilder setRepository(Repository repository) {
        this.repository = repository;
        return this;
    }

    public BlockChainImpl build() {
        if (genesis == null)
            genesis = BlockGenerator.getGenesisBlock();

        if (repository == null)
            repository = new RepositoryImpl(new TrieStoreImpl(new HashMapDB()));

        if (blockStore == null) {
            IndexedBlockStore indexedBlockStore = new IndexedBlockStore();
            indexedBlockStore.init(new HashMap<>(), new HashMapDB(), null);
            blockStore = indexedBlockStore;
        }

        for (ByteArrayWrapper key : genesis.getPremine().keySet()) {
            repository.createAccount(key.getData());
            repository.addBalance(key.getData(), genesis.getPremine().get(key).getAccountState().getBalance());
        }

        genesis.setStateRoot(repository.getRoot());
        genesis.flushRLP();

        blockStore.saveBlock(genesis, genesis.getCumulativeDifficulty(), true);

        AdminInfo adminInfo = new AdminInfo();
        EthereumListener listener = new CompositeEthereumListener();

        KeyValueDataSource ds = new HashMapDB();
        ds.init();
        receiptStore = new ReceiptStoreImpl(ds);

        if (txinfos != null && !txinfos.isEmpty())
            for (TransactionInfo txinfo : txinfos)
                receiptStore.add(txinfo.getBlockHash(), txinfo.getIndex(), txinfo.getReceipt());

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();

        validatorBuilder.addBlockRootValidationRule().addBlockUnclesValidationRule(blockStore)
                .addBlockTxsValidationRule(repository).blockStore(blockStore);

        BlockValidator blockValidator = validatorBuilder.build();

        BlockChainImpl blockchain = new BlockChainImpl(repository, blockStore, receiptStore, null, listener, adminInfo, blockValidator);

        if (this.testing && this.rsk) {
            blockchain.setBlockValidator(new DummyBlockValidator());
            blockchain.setNoValidation(true);
        }

        PendingStateImpl pendingState = new PendingStateImpl(blockchain, repository, null, null, listener, 10, 100);
        pendingState.init();

        blockchain.setBestBlock(genesis);
        blockchain.setTotalDifficulty(genesis.getCumulativeDifficulty());

        blockchain.setPendingState(pendingState);

        blockchain.setRsk(this.rsk);

        BlockExecutor blockExecutor = new BlockExecutor(repository, blockchain, blockStore, listener);

        if (this.blocks != null)
            for (Block b : this.blocks) {
                blockExecutor.executeAndFillAll(b, blockchain.getBestBlock());
                blockchain.tryToConnect(b);
            }

        return blockchain;
    }
}
