package org.ethereum.util;

import co.rsk.core.TouchedAccountsTracker;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.util.RskTransactionExecutor;
import co.rsk.util.TestContract;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ContractDetails;
import org.ethereum.db.ReceiptStore;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.program.ProgramResult;
import org.spongycastle.util.encoders.Hex;

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
        Block block = new BlockBuilder(blockchain)
                .gasLimit(BigInteger.valueOf(10_000_000))
                .build();
        blockchain.setBestBlock(block);
        // create a test sender account with a large balance for running any contract
        this.sender = new AccountBuilder(blockchain)
                .name("sender")
                .balance(BigInteger.valueOf(1_000_000_000_000L))
                .build();
    }

    public ContractDetails addContract(String runtimeBytecode) {
        Account contractAccount = new AccountBuilder(blockchain)
                        .name(runtimeBytecode)
                        .balance(BigInteger.TEN)
                        .code(TypeConverter.stringHexToByteArray(runtimeBytecode))
                        .build();

        return repository.getContractDetails(contractAccount.getAddress());
    }

    public ProgramResult createContract(byte[] bytecode) {
        Transaction creationTx = contractCreateTx(bytecode);
        return executeTransaction(creationTx).getResult();
    }

    public ProgramResult createAndRunContract(TestContract contract, String functionName, BigInteger value, Object... args) {
        byte[] bytecode = Hex.decode(contract.bytecode);
        createContract(bytecode);
        Transaction creationTx = contractCreateTx(bytecode);
        executeTransaction(creationTx);
        return runContract(contract, creationTx.getContractAddress(), functionName, value, args);
    }

    private TransactionBuilder contractCreateTxBuilder(byte[] data) {
        BigInteger nonceCreate = repository.getNonce(sender.getAddress());
        return new TransactionBuilder()
                // a large gas limit will allow running any contract
                .gasLimit(BigInteger.valueOf(10_000_000))
                .sender(sender)
                .data(data)
                .nonce(nonceCreate.longValue());
    }

    public Transaction contractCreateTx(byte[] bytecode) {
        return contractCreateTxBuilder(bytecode).build();
    }

    private ProgramResult runContract(TestContract contract, byte[] contractAddress, String functionName, BigInteger value, Object... args) {
        Transaction transaction = callTransaction(contract, contractAddress, functionName, value, args);
        return executeTransaction(transaction).getResult();
    }

    private TransactionExecutor executeTransaction(Transaction transaction) {
        RskTransactionExecutor executor = new RskTransactionExecutor(repository, blockchain, blockStore, receiptStore);
        return executor.executeTransaction(new TouchedAccountsTracker(), transaction);
    }

    public ProgramResult executeFunction(TestContract contract, String functionName, BigInteger value, Object... args) {
        return createAndRunContract(contract, functionName, value, args);
    }

    public ProgramResult createContract(TestContract contract) {
        return createContract(Hex.decode(contract.bytecode));
    }

    public Transaction creationTransaction(TestContract contract) {
        byte[] bytecode = Hex.decode(contract.bytecode);
        return contractCreateTx(bytecode);
    }

    public Transaction callTransaction(TestContract contract, byte[] contractAddress, String functionName, BigInteger value, Object... args) {
        byte[] encodedCall = contract.functions.get(functionName).encode(args);
        return contractCreateTxBuilder(encodedCall)
                .receiverAddress(contractAddress)
                .value(value)
                .build();
    }

}
