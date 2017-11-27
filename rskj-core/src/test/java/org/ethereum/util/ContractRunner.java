package org.ethereum.util;

import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.*;
import org.ethereum.db.ContractDetails;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

import java.math.BigInteger;

/**
 * Helper methods to easily run contracts using {@link org.ethereum.util.RskTestFactory}.
 */
public class ContractRunner {
    private final RskTestFactory factory;
    public final Account sender;

    public ContractRunner() {
        this(new RskTestFactory());
    }

    public ContractRunner(RskTestFactory factory) {
        this.factory = factory;
        // we build a new block with high gas limit because Genesis' is too low
        Block block = new BlockBuilder(factory.getBlockchain())
                .gasLimit(BigInteger.valueOf(10_000_000))
                .build();
        this.factory.getBlockchain().setBestBlock(block);
        // create a test sender account with a large balance for running any contract
        this.sender = new AccountBuilder(factory.getBlockchain())
                .name("sender")
                .balance(BigInteger.valueOf(1_000_000_000_000L))
                .build();
    }

    public ContractDetails addContract(String runtimeBytecode) {
        Account contractAccount = new AccountBuilder(factory.getBlockchain())
                        .name(runtimeBytecode)
                        .balance(BigInteger.TEN)
                        .code(TypeConverter.stringHexToByteArray(runtimeBytecode))
                        .build();

        return factory.getRepository().getContractDetails(contractAccount.getAddress());
    }

    public ProgramResult createContract(byte[] bytecode) {
        Transaction creationTx = contractCreateTx(bytecode);
        return executeTransaction(creationTx).getResult();
    }

    public ProgramResult createAndRunContract(byte[] bytecode, byte[] encodedCall, BigInteger value) {
        createContract(bytecode);
        Transaction creationTx = contractCreateTx(bytecode);
        executeTransaction(creationTx);
        return runContract(creationTx.getContractAddress(), encodedCall, value);
    }

    private Transaction contractCreateTx(byte[] bytecode) {
        BigInteger nonceCreate = factory.getRepository().getNonce(sender.getAddress());
        return new TransactionBuilder()
                .gasLimit(BigInteger.valueOf(10_000_000))
                .sender(sender)
                .data(bytecode)
                .nonce(nonceCreate.longValue())
                .build();
    }

    private ProgramResult runContract(byte[] contractAddress, byte[] encodedCall, BigInteger value) {
        BigInteger nonceExecute = factory.getRepository().getNonce(sender.getAddress());
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
        Repository track = factory.getRepository().startTracking();
        TransactionExecutor executor = new TransactionExecutor(transaction, new byte[32],
                factory.getRepository(), factory.getBlockStore(), factory.getReceiptStore(),
                new ProgramInvokeFactoryImpl(), factory.getBlockchain().getBestBlock());
        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();
        track.commit();
        return executor;
    }
}
