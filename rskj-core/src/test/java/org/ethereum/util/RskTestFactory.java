package org.ethereum.util;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.PendingStateImpl;
import co.rsk.db.RepositoryImpl;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.Genesis;
import org.ethereum.core.PendingState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;

import java.util.HashMap;

/**
 * This is the test version of {@link co.rsk.core.RskFactory}, but without Spring.
 *
 * We try to recreate the objects used in production as best as we can,
 * replacing persistent storage with in-memory storage.
 * There are many nulls in place of objects that aren't part of our
 * tests yet.
 */
public class RskTestFactory {
    private BlockChainImpl blockchain;
    private IndexedBlockStore blockStore;
    private PendingState pendingState;
    private RepositoryImpl repository;

    public RskTestFactory() {
        Genesis genesis = BlockGenerator.getInstance().getGenesisBlock();
        genesis.setStateRoot(getRepository().getRoot());
        genesis.flushRLP();
        getBlockchain().setBestBlock(genesis);
        getBlockchain().setTotalDifficulty(genesis.getCumulativeDifficulty());
    }

    public BlockChainImpl getBlockchain() {
        if (blockchain == null) {
            blockchain = new BlockChainImpl(
                    getRepository(),
                    getBlockStore(),
                    getReceiptStore(),
                    null, //circular dependency
                    null,
                    null,
                    new DummyBlockValidator()
            );
            PendingState pendingState = getPendingState();
            blockchain.setPendingState(pendingState);
        }

        return blockchain;
    }

    public ReceiptStore getReceiptStore() {
        HashMapDB receiptStore = new HashMapDB();
        return new ReceiptStoreImpl(receiptStore);
    }

    public BlockStore getBlockStore() {
        if (blockStore == null) {
            blockStore = new IndexedBlockStore();
            HashMapDB blockStore = new HashMapDB();
            this.blockStore.init(new HashMap<>(), blockStore, null);
        }

        return blockStore;
    }

    public PendingState getPendingState() {
        if (pendingState == null) {
            pendingState = new PendingStateImpl(getBlockchain(), getBlockStore(), getRepository());
        }

        return pendingState;
    }

    public Repository getRepository() {
        if (repository == null) {
            HashMapDB stateStore = new HashMapDB();
            repository = new RepositoryImpl(new TrieStoreImpl(stateStore));
        }

        return repository;
    }
}
