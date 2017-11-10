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

package co.rsk.test.builders;

import co.rsk.core.bc.*;
import co.rsk.db.RepositoryImpl;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.Block;
import org.ethereum.core.Genesis;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.*;
import org.ethereum.listener.EthereumListener;
import org.ethereum.manager.AdminInfo;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

import java.util.HashMap;
import java.util.List;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class BlockChainBuilder {
    private boolean testing;
    private boolean rsk;

    private List<Block> blocks;
    private List<TransactionInfo> txinfos;

    private AdminInfo adminInfo;
    private Repository repository;
    private BlockStore blockStore;
    private Genesis genesis;

    public BlockChainBuilder setAdminInfo(AdminInfo adminInfo) {
        this.adminInfo = adminInfo;
        return this;
    }

    public BlockChainBuilder setTesting(boolean value) {
        this.testing = value;
        return this;
    }

    public BlockChainBuilder setRsk(boolean value) {
        this.rsk = value;
        return this;
    }

    public BlockChainBuilder setBlocks(List<Block> blocks) {
        this.blocks = blocks;
        return this;
    }

    public BlockChainBuilder setBlockStore(BlockStore blockStore) {
        this.blockStore = blockStore;
        return this;
    }

    public BlockChainBuilder setTransactionInfos(List<TransactionInfo> txinfos) {
        this.txinfos = txinfos;
        return this;
    }

    public BlockChainBuilder setGenesis(Genesis genesis) {
        this.genesis = genesis;
        return this;
    }

    public BlockChainImpl build() {
        if (repository == null)
            repository = new RepositoryImpl(new TrieStoreImpl(new HashMapDB().setClearOnClose(false)));

        if (blockStore == null) {
            IndexedBlockStore indexedBlockStore = new IndexedBlockStore();
            indexedBlockStore.init(new HashMap<>(), new HashMapDB(), null);
            blockStore = indexedBlockStore;
        }

        KeyValueDataSource ds = new HashMapDB();
        ds.init();
        ReceiptStore receiptStore = new ReceiptStoreImpl(ds);

        if (txinfos != null && !txinfos.isEmpty())
            for (TransactionInfo txinfo : txinfos)
                receiptStore.add(txinfo.getBlockHash(), txinfo.getIndex(), txinfo.getReceipt());

        EthereumListener listener = new BlockExecutorTest.SimpleEthereumListener();

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();

        validatorBuilder.addBlockRootValidationRule().addBlockUnclesValidationRule(blockStore)
                .addBlockTxsValidationRule(repository).blockStore(blockStore);

        BlockValidator blockValidator = validatorBuilder.build();

        if (this.adminInfo == null)
            this.adminInfo = new AdminInfo();

        BlockChainImpl blockChain = new BlockChainImpl(this.repository, this.blockStore, receiptStore, null, listener, this.adminInfo, blockValidator);

        if (this.testing && this.rsk) {
            blockChain.setBlockValidator(new DummyBlockValidator());
            blockChain.setNoValidation(true);
        }

        blockChain.setRsk(this.rsk);

        PendingStateImpl pendingState = new PendingStateImpl(blockChain, blockChain.getRepository(), blockChain.getBlockStore(), new ProgramInvokeFactoryImpl(), new BlockExecutorTest.SimpleEthereumListener(), 10, 100);

        blockChain.setPendingState(pendingState);

        if (this.genesis != null) {
            for (ByteArrayWrapper key : this.genesis.getPremine().keySet()) {
                this.repository.createAccount(key.getData());
                this.repository.addBalance(key.getData(), this.genesis.getPremine().get(key).getAccountState().getBalance());
            }

            this.genesis.setStateRoot(this.repository.getRoot());
            this.genesis.flushRLP();
            blockChain.setBestBlock(this.genesis);
            blockChain.setTotalDifficulty(this.genesis.getCumulativeDifficulty());
        }

        if (this.blocks != null) {
            BlockExecutor blockExecutor = new BlockExecutor(repository, blockChain, blockStore, listener);

            for (Block b : this.blocks) {
                blockExecutor.executeAndFillAll(b, blockChain.getBestBlock());
                blockChain.tryToConnect(b);
            }
        }

        return blockChain;
    }
}
