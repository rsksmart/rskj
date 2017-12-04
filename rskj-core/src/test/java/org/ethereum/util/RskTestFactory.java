package org.ethereum.util;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.PendingStateImpl;
import co.rsk.db.RepositoryImpl;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.*;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

import java.math.BigInteger;
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

    public ContractDetails addContract(String runtimeBytecode) {
        Account contractAccount = new AccountBuilder(getBlockchain())
                .name(runtimeBytecode)
                .balance(BigInteger.TEN)
                .code(TypeConverter.stringHexToByteArray(runtimeBytecode))
                .build();

        return getRepository().getContractDetails(contractAccount.getAddress());
    }

    public ProgramResult executeRawContract(byte[] bytecode, byte[] encodedCall, BigInteger value) {
        Account sender = new AccountBuilder(getBlockchain())
                .name("sender")
                // a large balance will allow running any contract
                .balance(BigInteger.valueOf(10000000))
                .build();
        BigInteger nonceCreate = getRepository().getNonce(sender.getAddress());
        Transaction creationTx = new TransactionBuilder()
                .gasLimit(BigInteger.valueOf(3000000))
                .sender(sender)
                .data(bytecode)
                .nonce(nonceCreate.longValue())
                .build();
        executeTransaction(creationTx);
        BigInteger nonceExecute = getRepository().getNonce(sender.getAddress());
        Transaction transaction = new TransactionBuilder()
                // a large gas limit will allow running any contract
                .gasLimit(BigInteger.valueOf(3000000))
                .sender(sender)
                .receiverAddress(creationTx.getContractAddress())
                .data(encodedCall)
                .nonce(nonceExecute.longValue())
                .value(value)
                .build();
        return executeTransaction(transaction).getResult();
    }

    private TransactionExecutor executeTransaction(Transaction transaction) {
        Repository track = getRepository().startTracking();
        TransactionExecutor executor = new TransactionExecutor(transaction, 0, new byte[32],
                getRepository(), getBlockStore(), getReceiptStore(),
                new ProgramInvokeFactoryImpl(), getBlockchain().getBestBlock());
        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();
        track.commit();
        return executor;
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
