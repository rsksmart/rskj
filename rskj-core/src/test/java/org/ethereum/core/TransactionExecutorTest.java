package org.ethereum.core;

import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.junit.Test;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public class TransactionExecutorTest {
    @Test
    public void testInitHandlesFreeTransactionsOK() {
        // paperwork: mock a whole nice transaction executor
        int txIndex = 1;
        long gasUsedInTheBlock = 0;

        ActivationConfig activationConfig = ActivationConfigsForTest.all();
        Constants constants = mock(Constants.class);
        Transaction transaction = mock(Transaction.class);
        RskAddress rskAddress = mock(RskAddress.class);
        Repository repository = mock(Repository.class);
        BlockStore blockStore = mock(BlockStore.class);
        ReceiptStore receiptStore = mock(ReceiptStore.class);
        BlockFactory blockFactory = mock(BlockFactory.class);
        ProgramInvokeFactory programInvokeFactory = mock(ProgramInvokeFactory.class);
        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);
        VmConfig vmConfig = mock(VmConfig.class);
        PrecompiledContracts precompiledContracts = mock(PrecompiledContracts.class);
        Set<DataWord> deletedAccounts = new HashSet<>();
        ExecutorService vmExecution = mock(ExecutorService.class);
        BlockTxSignatureCache blockTxSignatureCache = mock(BlockTxSignatureCache.class);

        TransactionExecutor txExecutor = new TransactionExecutor(
                constants, activationConfig, transaction, txIndex, rskAddress,
                repository, blockStore, receiptStore, blockFactory,
                programInvokeFactory, executionBlock, gasUsedInTheBlock, vmConfig,
                true, true, precompiledContracts, deletedAccounts,
                vmExecution, blockTxSignatureCache
        );


        // paperwork: transaction has high gas limit, execution block has normal gas limit
        // and the nonces are okey
        when(transaction.getGasLimit()).thenReturn(BigInteger.valueOf(4000000).toByteArray());
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(6800000).toByteArray());
        when(repository.getNonce(transaction.getSender())).thenReturn(BigInteger.valueOf(1L));
        when(transaction.getNonce()).thenReturn(BigInteger.valueOf(1L).toByteArray());
        // more paperwork, the receiver is just someone
        RskAddress receiver = new RskAddress("0000000000000000000000000000000000000001");
        when(transaction.getReceiveAddress()).thenReturn(receiver);
        when(transaction.acceptTransactionSignature(constants.getChainId())).thenReturn(true);
        // sender has no balance
        when(repository.getBalance(transaction.getSender())).thenReturn(new Coin(BigInteger.valueOf(0L)));
        // but is sending some nice value over the wire
        when(transaction.getValue()).thenReturn(new Coin(BigInteger.valueOf(68000)));
        // note that the transaction is free of cost
        assertEquals(0, transaction.transactionCost(constants, activationConfig.forBlock(executionBlock.getNumber())));
        assertFalse(txExecutor.executeTransaction());
    }
}