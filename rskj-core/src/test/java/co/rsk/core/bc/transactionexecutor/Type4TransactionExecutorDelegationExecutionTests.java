package co.rsk.core.bc.transactionexecutor;

import co.rsk.core.Coin;
import co.rsk.core.bc.transactionexecutor.helper.Type4TransactionExecutorHelperTest;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.SetCodeAuthorizationTransactionExecutor;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

 class Type4TransactionExecutorDelegationExecutionTests  extends Type4TransactionExecutorHelperTest {

    @Test
    void type4TransactionToDelegatedAccountLoadsDelegateCodeForExecution() {
        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockAuthorizationAccount(authorizationTracker, authorityAddress, ZERO_NONCE, EMPTY_CODE);

        byte[] delegatedReceiverAddress = DelegationCodeResolver.createDelegatedCode(delegatedAddress);
        byte[] delegateCode = new byte[] { 0x00 }; // STOP

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);
        mockAccountWithCode(tracker, receiver, delegatedReceiverAddress);
        mockAddressAsNotAPrecompiled(receiver);
        mockAccountWithCode(tracker, delegatedAddress, delegateCode);
        mockAddressAsNotAPrecompiled(delegatedAddress);

        var authorization = createValidAuthorizationTuple(
                createRandomAddress(),
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        mockSuccessfulProgramInvoke(tx, cacheTracker);

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verify(tracker).getCode(receiver);
        verify(tracker).getCode(delegatedAddress);
        verifyCreateProgramInvoked(tx, cacheTracker);

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyValidAuthorityChanges(authorizationTracker, authorityAddress, authorization.getAddress());
        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
    }

    @Test
    void type4TransactionToDelegatedAccountResolvesDelegationOnlyOnce() {
        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockAuthorizationAccount(authorizationTracker, authorityAddress, ZERO_NONCE, EMPTY_CODE);

        var bob = createRandomAddress();
        var charlie = createRandomAddress();

        byte[] delegatedCode = DelegationCodeResolver.createDelegatedCode(bob);
        byte[] bobDelegatedCode = DelegationCodeResolver.createDelegatedCode(charlie);

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);

        mockAccountWithCode(tracker, receiver, delegatedCode);
        mockAddressAsNotAPrecompiled(receiver);

        mockAccountWithCode(tracker, bob, bobDelegatedCode);
        mockAddressAsNotAPrecompiled(bob);

        mockAddressAsNotAPrecompiled(charlie);

        var authorization = createValidAuthorizationTuple(
                createRandomAddress(),
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        mockSuccessfulProgramInvoke(tx, cacheTracker);

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verify(tracker).getCode(receiver);
        verify(tracker).getCode(bob);
        verify(tracker, never()).getCode(charlie);
        // Bob.code is used as-is. We must not resolve Bob -> Charlie.
        verifyCreateProgramInvoked(tx, cacheTracker);

        assertNotNull(txExecutor.getResult().getException());
        assertTrue(txExecutor.getResult().getException().getMessage().contains("Invalid operation code"));
        assertTrue(txExecutor.getResult().getException().getMessage().contains("opcode[ef]"));

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyValidAuthorityChanges(authorizationTracker, authorityAddress, authorization.getAddress());
        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
    }

    @Test
    void transactionToAccountDelegatedToPrecompileExecutesEmptyCodeAndDoesNotCallPrecompile() throws VMException {
        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        var precompile = mock(PrecompiledContracts.PrecompiledContract.class);

        byte[] delegatedReceiverCode = DelegationCodeResolver.createDelegatedCode(PrecompiledContracts.REMASC_ADDR);

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);

        mockAccountWithCode(tracker, receiver, delegatedReceiverCode);
        mockAddressAsNotAPrecompiled(receiver);
        mockAuthorizationAccount(authorizationTracker, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        when(precompiledContracts.getContractForAddress(
                any(ActivationConfig.ForBlock.class),
                eq(DataWord.valueOf(PrecompiledContracts.REMASC_ADDR.getBytes()))
        )).thenReturn(precompile);

        var authorization = createValidAuthorizationTuple(
                createRandomAddress(),
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verify(tracker).getCode(receiver);
        verify(tracker, never()).getCode(PrecompiledContracts.REMASC_ADDR);

        verify(precompile, never()).init(any());
        verify(precompile, never()).getGasForData(any());
        verify(precompile, never()).execute(any());

        verify(programInvokeFactory, never()).createProgramInvoke(
                any(),
                anyInt(),
                any(),
                any(),
                any(),
                any()
        );
        assertEquals(EMPTY_BYTE_ARRAY, txExecutor.getResult().getHReturn());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyValidAuthorityChanges(authorizationTracker, authorityAddress, authorization.getAddress());
        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
        verify(tracker, never()).createAccount(any());
        verify(tracker, never()).setupContract(any());
        verify(cacheTracker, never()).createAccount(any());
        verify(cacheTracker, never()).setupContract(any());
        verify(cacheTracker, never()).saveCode(any(), any());
    }

     @Test
     void type4TransactionDirectlyToPrecompileKeepsCurrentPrecompileBehavior() throws VMException {
         var cacheTracker = mock(MutableRepository.class);
         var authorizationTracker = mock(MutableRepository.class);
         when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

         var precompile = mock(PrecompiledContracts.PrecompiledContract.class);
         byte[] precompileOutput = new byte[] { 0x01, 0x02 };

         mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);
         mockAuthorizationAccount(authorizationTracker, authorityAddress, ZERO_NONCE, EMPTY_CODE);
         when(precompiledContracts.getContractForAddress(
                 any(ActivationConfig.ForBlock.class),
                 eq(DataWord.valueOf(receiver.getBytes()))
         )).thenReturn(precompile);

         when(precompile.getGasForData(any())).thenReturn(1L);
         when(precompile.execute(any())).thenReturn(precompileOutput);
         when(precompile.getSubtraces()).thenReturn(List.of());

         var authorization = createValidAuthorizationTuple(
                 createRandomAddress(),
                 ZERO_NONCE,
                 constants.getChainId(),
                 authorityKey
         );

         var tx = createSignedType4Transaction(
                 senderKey,
                 constants.getChainId(),
                 ONE_NONCE,
                 600_000,
                 1,
                 1,
                 receiver,
                 2,
                 EMPTY_DATA,
                 authorization
         );

         var txExecutor = newExecutor(tx);

         assertTrue(txExecutor.executeTransaction());

         verify(precompile).init(any());
         verify(precompile).getGasForData(EMPTY_DATA);
         verify(precompile).execute(EMPTY_DATA);

         verify(tracker, never()).getCode(receiver);
         verify(programInvokeFactory, never()).createProgramInvoke(
                 any(),
                 anyInt(),
                 any(),
                 any(),
                 any(),
                 any()
         );

         assertEquals(precompileOutput, txExecutor.getResult().getHReturn());

         verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
         verifyValidAuthorityChanges(authorizationTracker, authorityAddress, authorization.getAddress());
         verifyTransfer(cacheTracker, sender, 2);
         verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
     }

}
