package org.ethereum.util;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ContractDetails;
import org.ethereum.db.ReceiptStore;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

import java.math.BigInteger;

/**
 * Helper methods to easily run contracts.
 */
public class ContractRunner {
    private final Repository repository;
    private final BlockChainImpl blockchain;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;

    public final Account sender;

    public ContractRunner() {
        this(new RskTestFactory());
    }

    public ContractRunner(RskTestFactory factory) {
        this(factory.getRepository(), factory.getBlockchain(), factory.getBlockStore(), factory.getReceiptStore());
    }

    private ContractRunner(Repository repository,
                           BlockChainImpl blockchain,
                           BlockStore blockStore,
                           ReceiptStore receiptStore) {
        this.blockchain = blockchain;
        this.repository = repository;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;

        // we build a new block with high gas limit because Genesis' is too low
        Block block = new BlockBuilder(blockchain, new BlockGenerator())
                .gasLimit(BigInteger.valueOf(10_000_000))
                .build();
        blockchain.setBestBlock(block);
        // create a test sender account with a large balance for running any contract
        this.sender = new AccountBuilder(blockchain)
                .name("sender")
                .balance(Coin.valueOf(1_000_000_000_000L))
                .build();
    }

    public ContractDetails addContract(String runtimeBytecode) {
        Account contractAccount = new AccountBuilder(blockchain)
                        .name(runtimeBytecode)
                        .balance(Coin.valueOf(10))
                        .code(TypeConverter.stringHexToByteArray(runtimeBytecode))
                        .build();

        return repository.getContractDetails(contractAccount.getAddress());
    }

    public ProgramResult createContract(byte[] bytecode) {
        Transaction creationTx = contractCreateTx(bytecode);
        return executeTransaction(creationTx).getResult();
    }

    public ProgramResult createAndRunContract(byte[] bytecode, byte[] encodedCall, BigInteger value) {
        createContract(bytecode);
        Transaction creationTx = contractCreateTx(bytecode);
        executeTransaction(creationTx);
        return runContract(creationTx.getContractAddress().getBytes(), encodedCall, value);
    }

    private Transaction contractCreateTx(byte[] bytecode) {
        BigInteger nonceCreate = repository.getNonce(sender.getAddress());
        return new TransactionBuilder()
                .gasLimit(BigInteger.valueOf(10_000_000))
                .sender(sender)
                .data(bytecode)
                .nonce(nonceCreate.longValue())
                .build();
    }

    private ProgramResult runContract(byte[] contractAddress, byte[] encodedCall, BigInteger value) {
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
        return executeTransaction(transaction).getResult();
    }

    private TransactionExecutor executeTransaction(Transaction transaction) {
        Repository track = repository.startTracking();
        TransactionExecutor executor = new TransactionExecutor(new TestSystemProperties(), transaction, 0, RskAddress.nullAddress(),
                                                               repository, blockStore, receiptStore,
                                                               new ProgramInvokeFactoryImpl(), blockchain.getBestBlock());
        executor.init();
        executor.execute();
        track.commit();
        return executor;
    }
}
