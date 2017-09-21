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

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.*;
import co.rsk.db.RepositoryImpl;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.BlockValidator;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.listener.EthereumListener;
import org.ethereum.manager.AdminInfo;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;

import java.util.HashMap;
import java.util.List;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class BlockChainBuilder {
    private AdminInfo adminInfo = null;

    public BlockChainBuilder adminInfo(AdminInfo adminInfo) {
        this.adminInfo = adminInfo;
        return this;
    }

    public BlockChainImpl build() {
        TrieStore store = new TrieStoreImpl(new HashMapDB().setClearOnClose(false));
        Repository repository = new RepositoryImpl(store);

        IndexedBlockStore blockStore = new IndexedBlockStore();
        blockStore.init(new HashMap<>(), new HashMapDB(), null);

        KeyValueDataSource ds = new HashMapDB();
        ds.init();
        ReceiptStore receiptStore = new ReceiptStoreImpl(ds);

        EthereumListener listener = new BlockExecutorTest.SimpleEthereumListener();

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();

        validatorBuilder.addBlockRootValidationRule().addBlockUnclesValidationRule(blockStore)
                .addBlockTxsValidationRule(repository).blockStore(blockStore);

        BlockValidator blockValidator = validatorBuilder.build();

        BlockChainImpl blockChain = new BlockChainImpl(repository, blockStore, receiptStore, null, listener, adminInfo, blockValidator);

        PendingStateImpl pendingState = new PendingStateImpl(blockChain, blockChain.getRepository(), blockChain.getBlockStore(), new ProgramInvokeFactoryImpl(), new BlockExecutorTest.SimpleEthereumListener(), 10, 100);

        blockChain.setPendingState(pendingState);

        return blockChain;
    }

    public static Blockchain ofSize(int size) {
        return ofSize(size, false);
    }

    public static Blockchain ofSize(int size, boolean mining) {
        BlockChainBuilder builder = new BlockChainBuilder();
        BlockChainImpl blockChain = builder.build();

        Block genesis = BlockGenerator.getGenesisBlock();
        genesis.setStateRoot(blockChain.getRepository().getRoot());
        genesis.flushRLP();

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        if (size > 0) {
            List<Block> blocks = mining ? BlockGenerator.getMinedBlockChain(genesis, size) : BlockGenerator.getBlockChain(genesis, size);

            for (Block block: blocks)
                blockChain.tryToConnect(block);
        }

        return blockChain;
    }

    public static Blockchain copy(Blockchain original) {
        BlockChainBuilder builder = new BlockChainBuilder();
        BlockChainImpl blockChain = builder.build();

        long height = original.getStatus().getBestBlockNumber();

        for (long k = 0; k <= height; k++)
            blockChain.tryToConnect(original.getBlockByNumber(k));

        return blockChain;
    }

    public static Blockchain copyAndExtend(Blockchain original, int size) {
        return copyAndExtend(original, size, false);
    }

    public static Blockchain copyAndExtend(Blockchain original, int size, boolean mining) {
        Blockchain blockchain = copy(original);
        extend(blockchain, size, false, mining);
        return blockchain;
    }

    public static void extend(Blockchain blockchain, int size, boolean withUncles, boolean mining) {
        Block initial = blockchain.getBestBlock();
        List<Block> blocks = BlockGenerator.getBlockChain(initial, size, 0, withUncles, mining);

        for (Block block: blocks)
            blockchain.tryToConnect(block);
    }
}
