/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.core.bc.transactionexecutor;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.transactionexecutor.helper.Type4TransactionExecutorHelperTest;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;

import static org.ethereum.util.BIUtil.toBI;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
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

     private static Stream<Arguments> exceptionalHaltPrograms() {
         return Stream.of(Arguments.of("invalid opcode", new byte[]{(byte) 0xfe}),
                 Arguments.of("out of gas", new byte[]{
                                 0x5b,       // JUMPDEST
                                 0x60, 0x00, // PUSH1 0
                                 0x56        // JUMP -> jump back to 0 forever
                         }));
     }

     @ParameterizedTest(name = "{0}")
     @MethodSource("exceptionalHaltPrograms")
     void exceptionalHaltShouldPreserveAuthorizationRefund(String scenario, byte[] receiverCode) {
         long expectedAuthorizationRefund = GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST;

         var cacheTracker = mock(MutableRepository.class);
         var authorizationTracker = mock(MutableRepository.class);
         when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

         mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
         mockReceiver(receiver, receiverCode);

         byte[] existingDelegatedCode = DelegationCodeResolver.createDelegatedCode(delegatedAddress);
         mockAuthorizationAccount(authorizationTracker, authorityAddress, ONE_NONCE, existingDelegatedCode);

         var authorization = createValidAuthorizationTuple(createRandomAddress(), ONE_NONCE, constants.getChainId(), authorityKey);

         long gasLimit = 600_000L;
         long gasPrice = 1L;

         var tx = createSignedType4Transaction(
                 senderKey,
                 constants.getChainId(),
                 ONE_NONCE,
                 gasLimit,
                 gasPrice,
                 gasPrice,
                 receiver,
                 0,
                 EMPTY_DATA,
                 authorization
         );

         mockSuccessfulProgramInvoke(tx, cacheTracker);

         var txExecutor = newExecutor(tx);
         assertTrue(txExecutor.executeTransaction());

         // Both INVALID and OOG must reach an actual VM exceptional halt.
         assertNotNull(txExecutor.getResult().getException(), scenario + " should produce an exceptional halt");

         BigInteger receiptGasUsed = toBI(txExecutor.getReceipt().getGasUsed());
         BigInteger txGasLimit = toBI(tx.getGasLimit());

         long expectedAppliedRefund = Math.min(expectedAuthorizationRefund, txExecutor.getResult().getGasUsedBeforeRefunds() / 2);

         BigInteger expectedReceiptGasUsed = txGasLimit.subtract(BigInteger.valueOf(expectedAppliedRefund));

         assertEquals(expectedReceiptGasUsed, receiptGasUsed, "authorization refund should survive " + scenario);
         assertEquals( tx.getGasPrice().multiply(receiptGasUsed), txExecutor.getPaidFees(), "paidFees must use the same post-refund gas as the receipt");
         assertSenderReceivedRefund(tx.getGasPrice().multiply(BigInteger.valueOf(expectedAppliedRefund)));

         // Authorization processing must still persist even though execution
         verifyValidAuthorityChanges(authorizationTracker, authorityAddress, authorization.getAddress());
     }

     @Test
     void revertShouldPreserveAuthorizationRefundAndRemainingExecutionGas() {
         long expectedAuthorizationRefund = GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST;

         var cacheTracker = mock(MutableRepository.class);
         var authorizationTracker = mock(MutableRepository.class);
         when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

         mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);

         //PUSH1 0 , PUSH1 0, REVERT
         mockReceiver(receiver, new byte[]{0x60, 0x00, 0x60, 0x00, (byte) 0xfd});

         byte[] existingDelegatedCode = DelegationCodeResolver.createDelegatedCode(delegatedAddress);
         mockAuthorizationAccount(authorizationTracker, authorityAddress, ONE_NONCE, existingDelegatedCode);
         var authorization = createValidAuthorizationTuple(createRandomAddress(), ONE_NONCE, constants.getChainId(), authorityKey);

         long gasLimit = 600_000L;
         long gasPrice = 1L;

         var tx = createSignedType4Transaction(
                 senderKey,
                 constants.getChainId(),
                 ONE_NONCE,
                 gasLimit,
                 gasPrice,
                 gasPrice,
                 receiver,
                 0,
                 EMPTY_DATA,
                 authorization
         );

         mockSuccessfulProgramInvoke(tx, cacheTracker);

         var txExecutor = newExecutor(tx);

         assertTrue(txExecutor.executeTransaction());

         //REVERT is not an exceptional halt.  It sets the revert flag but doesn't set an exception.
         assertNull(txExecutor.getResult().getException());
         assertTrue(txExecutor.getResult().isRevert());

         BigInteger receiptGasUsed = toBI(txExecutor.getReceipt().getGasUsed());

         //REVERT preserves unused execution gas => final gas used = gas used before refunds - authorization refund
         long gasUsedBeforeRefunds = txExecutor.getResult().getGasUsedBeforeRefunds();
         long expectedAppliedRefund = Math.min(expectedAuthorizationRefund, gasUsedBeforeRefunds / 2);
         BigInteger expectedReceiptGasUsed = BigInteger.valueOf(gasUsedBeforeRefunds).subtract(BigInteger.valueOf(expectedAppliedRefund));

         assertEquals(expectedReceiptGasUsed, receiptGasUsed, "authorization refund should be applied after REVERT");
         assertEquals(tx.getGasPrice().multiply(receiptGasUsed), txExecutor.getPaidFees(), "paidFees must use the same post-refund gas as the receipt");
         assertEquals(expectedAppliedRefund, txExecutor.getResult().getDeductedRefund());

         // For REVERT the sender receives: unused execution gas + authorization refund.
         // getGasConsumed() already reflects both.
         BigInteger totalRefundedGas = toBI(tx.getGasLimit()).subtract(receiptGasUsed);
         assertSenderReceivedRefund(tx.getGasPrice().multiply(totalRefundedGas));

         /*
          * Authorization processing happened before REVERT and must persist.
          */
         verifyValidAuthorityChanges(authorizationTracker, authorityAddress, authorization.getAddress());
     }

     @Test
     void exceptionalHaltShouldNotRefundNonRefundableAuthorization() {
         var cacheTracker = mock(MutableRepository.class);
         var authorizationTracker = mock(MutableRepository.class);
         when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

         mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);

         // INVALID -> real exceptional halt.
         mockReceiver(receiver, new byte[]{(byte) 0xfe});

         mockAuthorizationAccount(authorizationTracker, authorityAddress, ONE_NONCE, EMPTY_CODE);
         var authorization = createValidAuthorizationTuple(createRandomAddress(), ONE_NONCE, constants.getChainId(), authorityKey);

         long gasLimit = 600_000L;
         long gasPrice = 1L;

         var tx = createSignedType4Transaction(
                 senderKey,
                 constants.getChainId(),
                 ONE_NONCE,
                 gasLimit,
                 gasPrice,
                 gasPrice,
                 receiver,
                 0,
                 EMPTY_DATA,
                 authorization
         );

         mockSuccessfulProgramInvoke(tx, cacheTracker);

         var txExecutor = newExecutor(tx);

         assertTrue(txExecutor.executeTransaction());

         assertNotNull(txExecutor.getResult().getException(), "INVALID should produce an exceptional halt");

         BigInteger receiptGasUsed = toBI(txExecutor.getReceipt().getGasUsed());
         BigInteger txGasLimit = toBI(tx.getGasLimit());

         assertEquals(txGasLimit, receiptGasUsed, "full gas limit should be charged when authorization is not refundable");
         assertEquals(0L, txExecutor.getResult().getDeductedRefund(), "non-refundable authorization should not produce a gas refund");
         assertEquals(tx.getGasPrice().multiply(receiptGasUsed), txExecutor.getPaidFees(), "paidFees must match receipt gas used");

         verifyValidAuthorityChanges(authorizationTracker, authorityAddress, authorization.getAddress());
     }

     @Test
     void exceptionalHaltShouldPreserveRefundFromMultipleAuthorizations() {
        long refundPerAuthorization = GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST;
        long expectedAuthorizationRefund = refundPerAuthorization * 2;

        var cacheTracker = mock(MutableRepository.class);
        var firstAuthorizationTracker = mock(MutableRepository.class);
        var secondAuthorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, firstAuthorizationTracker, secondAuthorizationTracker);
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);

        // INVALID -> real exceptional halt after processing both authorizations
        mockReceiver(receiver, new byte[]{(byte) 0xfe});

        byte[] firstExistingDelegatedCode = DelegationCodeResolver.createDelegatedCode(delegatedAddress);
        mockAuthorizationAccount(firstAuthorizationTracker, authorityAddress, ONE_NONCE, firstExistingDelegatedCode);

        ECKey secondAuthorityKey = new ECKey();
        RskAddress secondAuthorityAddress = new RskAddress(secondAuthorityKey.getAddress());
        byte[] secondExistingDelegatedCode = DelegationCodeResolver.createDelegatedCode(createRandomAddress());
        mockAuthorizationAccount(secondAuthorizationTracker, secondAuthorityAddress, ONE_NONCE, secondExistingDelegatedCode);


         var firstAuthorization = createValidAuthorizationTuple(createRandomAddress(), ONE_NONCE, constants.getChainId(), authorityKey);
         var secondAuthorization = createValidAuthorizationTuple(createRandomAddress(), ONE_NONCE, constants.getChainId(), secondAuthorityKey);

         long gasLimit = 600_000L;
         long gasPrice = 1L;

         var tx = createSignedType4Transaction(
                 senderKey,
                 constants.getChainId(),
                 ONE_NONCE,
                 gasLimit,
                 gasPrice,
                 gasPrice,
                 receiver,
                 0,
                 EMPTY_DATA,
                 firstAuthorization,
                 secondAuthorization
         );

         mockSuccessfulProgramInvoke(tx, cacheTracker);

         var txExecutor = newExecutor(tx);

         assertTrue(txExecutor.executeTransaction());

         assertNotNull(txExecutor.getResult().getException(), "INVALID should produce an exceptional halt");

         BigInteger receiptGasUsed = toBI(txExecutor.getReceipt().getGasUsed());
         BigInteger txGasLimit = toBI(tx.getGasLimit());

         long expectedAppliedRefund = Math.min(expectedAuthorizationRefund, txExecutor.getResult().getGasUsedBeforeRefunds() / 2);
         /*
          * Both authorization refunds must survive the exceptional halt.
          * gasLeftover after exceptional halt = 0
          * final gasLeftover = refund(A) + refund(B)
          */
         BigInteger expectedReceiptGasUsed = txGasLimit.subtract(BigInteger.valueOf(expectedAppliedRefund));

         assertEquals(expectedReceiptGasUsed, receiptGasUsed, "refunds from both authorizations should survive exceptional halt");
         assertEquals(expectedAppliedRefund, txExecutor.getResult().getDeductedRefund(), "both authorization refunds should be applied");

         assertEquals(tx.getGasPrice().multiply(receiptGasUsed), txExecutor.getPaidFees(), "paidFees must use the same post-refund gas as the receipt");
         assertSenderReceivedRefund(tx.getGasPrice().multiply(BigInteger.valueOf(expectedAppliedRefund)));

         verifyValidAuthorityChanges(firstAuthorizationTracker, authorityAddress, firstAuthorization.getAddress());
         verifyValidAuthorityChanges(secondAuthorizationTracker, secondAuthorityAddress, secondAuthorization.getAddress());
     }

     private void assertSenderReceivedRefund(Coin expectedRefund) {
         ArgumentCaptor<Coin> balanceChangeCaptor = ArgumentCaptor.forClass(Coin.class);
         verify(tracker, atLeast(2)).addBalance(eq(sender), balanceChangeCaptor.capture());
         assertTrue(balanceChangeCaptor.getAllValues().contains(expectedRefund),
                 "sender should receive expected gas refund: " + expectedRefund
         );
     }
}
