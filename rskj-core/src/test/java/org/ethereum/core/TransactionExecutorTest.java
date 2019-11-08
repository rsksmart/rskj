package org.ethereum.core;

import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.bouncycastle.util.Arrays;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.OngoingStubbing;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class TransactionExecutorTest {
    @Mock Constants constants;
    @Mock ActivationConfig activationConfig;
    @Mock Transaction transaction;
    @Mock RskAddress rskAddress;
    @Mock Repository repository;
    @Mock BlockStore blockStore;
    @Mock ReceiptStore receiptStore;
    @Mock BlockFactory blockFactory;
    @Mock ProgramInvokeFactory programInvokeFactory;
    @Mock Block executionBlock;
    @Mock VmConfig vmConfig;
    @Mock PrecompiledContracts precompiledContracts;
    @Mock Set<DataWord> deletedAccounts;
    @Mock ExecutorService vmExecution;

    private TransactionExecutor createMockedTransactionExecutor() {
        int txIndex = 1;
        long gasUsedInTheBlock = 0;
        Iterator iteratorMock = Mockito.mock(Iterator.class);
        when(iteratorMock.hasNext()).thenReturn(false);
        OngoingStubbing ongoingStubbing = when(deletedAccounts.iterator()).thenReturn(iteratorMock);
        return new TransactionExecutor(
                constants, activationConfig, transaction, txIndex, rskAddress,
                repository, blockStore, receiptStore, blockFactory,
                programInvokeFactory, executionBlock, gasUsedInTheBlock, vmConfig,
                true, true, precompiledContracts, deletedAccounts, vmExecution
        );
    }

    @Test
    public void testInitHandlesFreeTransactionsOK() {
        TransactionExecutor txExecutor = createMockedTransactionExecutor();
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
        assert(transaction.transactionCost(constants, activationConfig.forBlock(executionBlock.getNumber())) == 0);
        Assert.assertFalse(txExecutor.init());
    }

}