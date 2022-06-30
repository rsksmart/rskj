package org.ethereum.util;

import java.math.BigInteger;

import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.program.ProgramResult;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.trie.TrieStore;
import co.rsk.util.HexUtils;

/**
 * Helper methods to easily run contracts.
 */
public class ContractRunner {
    private final RepositoryLocator repositoryLocator;
    private final Blockchain blockchain;
    private final BlockStore blockStore;
    private final TransactionExecutorFactory transactionExecutorFactory;

    public final Account sender;

    public ContractRunner() {
        this(new RskTestFactory());
    }

    public ContractRunner(RskTestFactory factory) {
        this(factory.getRepositoryLocator(), factory.getBlockchain(), factory.getBlockStore(), factory.getTransactionExecutorFactory(), factory.getTrieStore());
    }

    private ContractRunner(RepositoryLocator repositoryLocator,
                           Blockchain blockchain,
                           BlockStore blockStore,
                           TransactionExecutorFactory transactionExecutorFactory,
                           TrieStore trieStore) {
        this.blockchain = blockchain;
        this.repositoryLocator = repositoryLocator;
        this.blockStore = blockStore;
        this.transactionExecutorFactory = transactionExecutorFactory;

        // we build a new block with high gas limit because Genesis' is too low
        Block block = new BlockBuilder(blockchain, null, blockStore)
                .trieStore(trieStore)
                .parent(blockchain.getBestBlock())
                .gasLimit(BigInteger.valueOf(10_000_000))
                .build();
        blockchain.setStatus(block, block.getCumulativeDifficulty());
        // create a test sender account with a large balance for running any contract
        this.sender = new AccountBuilder(blockchain, blockStore, repositoryLocator)
                .name("sender")
                .balance(Coin.valueOf(1_000_000_000_000L))
                .build();
    }

    public RskAddress addContract(String runtimeBytecode) {
        Account contractAccount = new AccountBuilder(blockchain, blockStore, repositoryLocator)
                        .name(runtimeBytecode)
                        .balance(Coin.valueOf(10))
                        .code(HexUtils.strHexOrStrNumberToByteArray(runtimeBytecode))
                        .build();

        return contractAccount.getAddress();
    }

    public ProgramResult createContract(byte[] bytecode) {
        return createContract(bytecode, repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader()));
    }

    private ProgramResult createContract(byte[] bytecode, RepositorySnapshot repository) {
        Transaction creationTx = contractCreateTx(bytecode, repository);
        return executeTransaction(creationTx, repository).getResult();
    }

    public ProgramResult createAndRunContract(byte[] bytecode, byte[] encodedCall, BigInteger value, boolean localCall) {
        RepositorySnapshot repository = repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());
        createContract(bytecode, repository);
        Transaction creationTx = contractCreateTx(bytecode, repository);
        executeTransaction(creationTx, repository);
        return runContract(creationTx.getContractAddress().getBytes(), encodedCall, value, localCall, repository);
    }

    private Transaction contractCreateTx(byte[] bytecode, RepositorySnapshot repository) {
        BigInteger nonceCreate = repository.getNonce(sender.getAddress());
        return new TransactionBuilder()
                .gasLimit(BigInteger.valueOf(10_000_000))
                .sender(sender)
                .data(bytecode)
                .nonce(nonceCreate.longValue())
                .build();
    }

    private ProgramResult runContract(byte[] contractAddress, byte[] encodedCall, BigInteger value, boolean localCall, RepositorySnapshot repository) {
        BigInteger nonceExecute = repository.getNonce(sender.getAddress());
        Transaction transaction = new TransactionBuilder()
                // a large gas limit will allow running any contract
                .gasLimit(BigInteger.valueOf(10_000_000))
                .sender(sender)
                .receiverAddress(contractAddress)
                .data(encodedCall)
                .nonce(nonceExecute.longValue())
                .value(value)
                .build();
        transaction.setLocalCallTransaction(localCall);
        return executeTransaction(transaction, repository).getResult();
    }

    private TransactionExecutor executeTransaction(Transaction transaction, RepositorySnapshot repository) {
        Repository track = repository.startTracking();
        TransactionExecutor executor = transactionExecutorFactory
                .newInstance(transaction, 0, RskAddress.nullAddress(), track, blockchain.getBestBlock(), 0);

        executor.executeTransaction();
        track.commit();

        return executor;
    }
}
