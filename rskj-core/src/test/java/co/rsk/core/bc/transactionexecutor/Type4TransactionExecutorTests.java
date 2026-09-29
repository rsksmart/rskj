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
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyByte;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Type4TransactionExecutorTests extends Type4TransactionExecutorHelperTest {

    @Test
    void type4TransactionProcessesAuthorizationListAndExecutesAValueTransferCall() {
        var authorityKey = new ECKey();
        var authorityAddress = new RskAddress(authorityKey.getAddress());

        MutableRepository cacheTracker = mock(MutableRepository.class);
        MutableRepository authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 800_000, ONE_NONCE);
        mockAuthorizationAccount(authorizationTracker, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        var setCodeAuthorization = createValidAuthorizationTuple(
                delegatedAddress,
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
                        setCodeAuthorization);

        TransactionExecutor txExecutor = newExecutor(tx);
        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authorityAddress, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST );
    }

    @Test
    void type4TransactionChargesAuthorizationCostForValidAndInvalidTuples() {
        var validAuthorityKey = new ECKey();
        var invalidAuthorityKey = new ECKey();

        var validAuthority = new RskAddress(validAuthorityKey.getAddress());
        var invalidAuthority = new RskAddress(invalidAuthorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var validAuthorizationTracker = mock(MutableRepository.class);
        var invalidAuthorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, validAuthorizationTracker, invalidAuthorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);

        mockAuthorizationAccount(validAuthorizationTracker, validAuthority, ZERO_NONCE, EMPTY_CODE);
        mockAuthorizationAccount(invalidAuthorizationTracker, invalidAuthority, ONE_NONCE, EMPTY_CODE);

        var validAuthorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                validAuthorityKey
        );

        var invalidAuthorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                invalidAuthorityKey
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
                validAuthorization, invalidAuthorization);

        TransactionExecutor txExecutor = newExecutor(tx);
        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(3);
        verifyValidAuthorityChanges(validAuthorizationTracker, validAuthority, delegatedAddress);
        verifyInvalidAuthorityChanges(invalidAuthorizationTracker, invalidAuthority, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST * 2L);
    }

    @Test
    void type4TransactionRefundsWhenAuthorizationAccountAlreadyHasDelegatedCode() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        MutableRepository cacheTracker = mock(MutableRepository.class);
        MutableRepository authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);
        mockFreeBridgeTxFalse();

        byte[] existingDelegatedCode = DelegationCodeResolver.createDelegatedCode(new RskAddress("0000000000000000000000000000000000000022"));

        when(authorizationTracker.getCode(authority)).thenReturn(existingDelegatedCode);
        when(authorizationTracker.getNonce(authority)).thenReturn(ONE_NONCE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ONE_NONCE,
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
                authorization);

        var txExecutor = newExecutor(tx);
        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);

        assertEquals(txExecutor.getResult().getDeductedRefund(), 9500L);
    }

    @Test
    void type4TransactionWithEmptyDelegatedAddressIsProcessed() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                EMPTY_ADDRESS,
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
                authorization);

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authority, new byte[0]);
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionAppliesAuthorizationBeforeLowLevelExecution() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);

        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);
        var authorization = createValidAuthorizationTuple(delegatedAddress, ZERO_NONCE, constants.getChainId(), authorityKey);

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
                authorization);

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerStartTrackingInvocations(2);

        verifyAuthorizationAppliedBeforeExecution(
                sender,
                authority,
                delegatedAddress,
                authorizationTracker,
                cacheTracker,
                600_000,
                2
        );
    }

    @Test
    void type4TransactionWithDataExecutesValidFlow() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        byte[] txData = new byte[] { 0x01, 0x02, 0x03, 0x04 };

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
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
                txData,
                authorization);

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);

        verifyTransfer(cacheTracker, sender, 2);
        assertEquals(txExecutor.getResult().getDeductedRefund(), 0);
    }

    @Test
    void type4TransactionRejectsSenderWithArbitraryContractCode() {
        byte[] arbitraryContractCode = new byte[] { 0x60, 0x00, 0x60, 0x00 };
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockValidSender(sender,1_000_000, ONE_NONCE, arbitraryContractCode);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
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
                authorization);

        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());
        verify(tracker, never()).increaseNonce(sender);
        verify(tracker, never()).addBalance(eq(sender), eq(Coin.valueOf(600_000).negate()));
    }

    @Test
    void type4TransactionReceiverIsSameAuthorityWithNoDataTransfersValue() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);
        mockReceiver(authority, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
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
                authority,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);

        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);

        verify(cacheTracker).transfer(
                eq(sender),
                eq(authority),
                eq(Coin.valueOf(2))
        );
        assertEquals(txExecutor.getResult().getDeductedRefund(), 0);
    }

    @Test
    void type4TransactionWithMultipleAuthorizationsFromSameAuthorityPersistsLastOne() {
        var firstDelegatedAddress = createRandomAddress();
        var secondDelegatedAddress = createRandomAddress();

        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var firstAuthorizationTracker = mock(MutableRepository.class);
        var secondAuthorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, firstAuthorizationTracker, secondAuthorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);

        mockAuthorizationAccount(firstAuthorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);
        byte[] firstDelegatedCode = DelegationCodeResolver.createDelegatedCode(firstDelegatedAddress);
        mockAuthorizationAccount(secondAuthorizationTracker, authority, ONE_NONCE, firstDelegatedCode);

        var firstAuthorization = createValidAuthorizationTuple(
                firstDelegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var secondAuthorization = createValidAuthorizationTuple(
                secondDelegatedAddress,
                ONE_NONCE,
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
                firstAuthorization, secondAuthorization);

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());


        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(3);
        verifyValidAuthorityChanges(firstAuthorizationTracker, authority, firstDelegatedCode);
        byte[] secondDelegatedCode = DelegationCodeResolver.createDelegatedCode(secondDelegatedAddress);
        verifyValidAuthorityChanges(secondAuthorizationTracker, authority, secondDelegatedCode);
        verifyTransfer(cacheTracker, sender, 2);
        assertEquals(txExecutor.getResult().getDeductedRefund(), GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST);
    }

    @Test
    void type4TransactionAuthorizationWithUniversalChainIdZeroIsAccepted() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                (byte) 0,
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
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);

        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionAuthorizationWithMismatchedNonZeroChainIdIsRejectedButOuterTxContinues() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        byte mismatchedChainId = Constants.TESTNET_CHAIN_ID;

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                mismatchedChainId,
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                Constants.MAINNET_CHAIN_ID,
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
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyInvalidAuthorityChanges(authorizationTracker, authority, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionAuthorizationDoesNotExecuteDelegateTargetDuringAuthorizationStep() {
        var precompiledDelegatedAddress = PrecompiledContracts.REMASC_ADDR;

        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                precompiledDelegatedAddress,
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
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);

        verifyValidAuthorityChanges(authorizationTracker, authority, precompiledDelegatedAddress);

        verify(precompiledContracts, never()).getContractForAddress(
                any(ActivationConfig.ForBlock.class),
                eq(DataWord.valueOf(precompiledDelegatedAddress.getBytes()))
        );
        verifyTransfer(cacheTracker, sender, 2);
        assertEquals(txExecutor.getResult().getDeductedRefund(), 0);
    }

    @Test
    void type4TransactionOuterPrecompiledFailureDoesNotRollbackProcessedAuthorization() throws VMException {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        var precompiledContract = mock(PrecompiledContracts.PrecompiledContract.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockAccountWithBalanceAndNonce(tracker, sender, 1_000_000, ONE_NONCE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        when(precompiledContracts.getContractForAddress(
                any(ActivationConfig.ForBlock.class),
                eq(DataWord.valueOf(receiver.getBytes()))
        )).thenReturn(precompiledContract);

        when(precompiledContract.getGasForData(any())).thenReturn(1L);
        when(precompiledContract.execute(any(byte[].class))).thenThrow(new RuntimeException("outer tx precompiled failure"));


        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
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
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);

        verify(cacheTracker, never()).rollback();
        verify(cacheTracker, never()).transfer(any(), any(), any());

        assertNotNull(txExecutor.getResult().getException());
    }

    @Test
    void type4TransactionRejectsReceiverAddressLongerThanTwentyBytes() {
        Transaction tx = mock(Transaction.class);

        RskAddress invalidReceiver = mock(RskAddress.class);
        byte[] invalidReceiverBytes = new byte[21];
        Arrays.fill(invalidReceiverBytes, (byte) 1);

        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_4));
        when(tx.isType4()).thenReturn(true);
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(false);
        when(tx.isContractCreation()).thenReturn(false);
        when(tx.getReceiveAddress()).thenReturn(invalidReceiver);
        when(invalidReceiver.getBytes()).thenReturn(invalidReceiverBytes);
        when(tx.getGasLimit()).thenReturn(BigInteger.valueOf(600_000).toByteArray());
        when(tx.getGasPrice()).thenReturn(Coin.valueOf(1));
        when(tx.getValue()).thenReturn(Coin.valueOf(2));
        when(tx.getNonce()).thenReturn(ONE_NONCE.toByteArray());
        when(tx.getSender(any())).thenReturn(sender);
        when(tx.getSender()).thenReturn(sender);
        when(tx.transactionCost(any(), any(), any())).thenReturn(21_000L);
        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);
        mockFreeBridgeTxFalse();
        mockExecutionBlockGasLimit(6_800_000);
        mockValidSender(sender,1_000_000, ONE_NONCE, EMPTY_CODE );

        var cacheTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker);
        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());
        verify(tracker, never()).increaseNonce(sender);
        verify(tracker, never()).addBalance(eq(sender), any(Coin.class));
    }

    @Test
    void type4TransactionAllowsSenderWithValidDelegatedCode() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        byte[] validDelegatedSenderCode = DelegationCodeResolver.createDelegatedCode(delegatedAddress);

        mockValidSender(sender,1_000_000, ONE_NONCE, validDelegatedSenderCode);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(delegatedAddress, ZERO_NONCE, constants.getChainId(), authorityKey);
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

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(2);
        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);

        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionAllowsSenderToAlsoBeAuthorizer() {
        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockValidSender(sender,1_000_000, ZERO_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        mockAuthorizationAccount(authorizationTracker, sender, ONE_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ONE_NONCE,
                constants.getChainId(),
                senderKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
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

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyValidAuthorityChanges(authorizationTracker, sender, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionWhenSenderIsAuthorityReadsUpdatedSenderNonceDuringAuthorization() {
        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        // Initial outer tx nonce is 0.
        mockValidSender(sender,1_000_000, ZERO_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);
        // Authorization tracker should see sender nonce AFTER outer tx increment.
        mockAuthorizationAccount(authorizationTracker, sender, ONE_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ONE_NONCE,
                constants.getChainId(),
                senderKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
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

        InOrder inOrder = inOrder(tracker, authorizationTracker);

        inOrder.verify(tracker).increaseNonce(sender);
        inOrder.verify(authorizationTracker).getNonce(sender);

        verifyValidAuthorityChanges(authorizationTracker, sender, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionRejectsSenderWithDelegatedCodePrefixOnly() {
        byte[] invalidDelegatedCode = new byte[] {(byte) 0xef, 0x01, 0x00};

        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockValidSender(sender,1_000_000, ONE_NONCE, invalidDelegatedCode);
        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
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

        assertFalse(txExecutor.executeTransaction());

        verify(tracker).getCode(sender);
        verify(tracker, never()).increaseNonce(sender);
        verify(tracker, never()).addBalance(eq(sender), any(Coin.class));
        verify(authorizationTracker, never()).commit();
    }

    @Test
    void type4TransactionRejectsSenderWithDelegatedCodeWrongLength() {
        byte[] invalidDelegatedCodeWrongLength = new byte[] {(byte) 0xef, 0x01, 0x00, 0x01};

        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);
        mockValidSender(sender,1_000_000, ONE_NONCE, invalidDelegatedCodeWrongLength);

        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
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

        assertFalse(txExecutor.executeTransaction());

        verify(tracker).getCode(sender);
        verify(tracker, never()).increaseNonce(sender);
        verify(tracker, never()).addBalance(eq(sender), any(Coin.class));
        verify(authorizationTracker, never()).commit();
    }

    @Test
    void nonType4TransactionStillRejectsInvalidInitCodeSize() {
        Transaction tx = mock(Transaction.class);

        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(false);
        when(tx.isType4()).thenReturn(false);
        when(tx.isInitCodeSizeInvalidForTx(any())).thenReturn(true);
        when(tx.getData()).thenReturn(new byte[] { 1, 2, 3 });
        when(tx.getHash()).thenReturn(null);
        when(tx.transactionCost(any(), any(), any())).thenReturn(21_000L);

        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());

        verify(tx).isInitCodeSizeInvalidForTx(any());
        verify(tracker, never()).increaseNonce(any());
    }

    @Test
    void type4TransactionDoesNotRunInitCodeSizeValidation() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        mockAuthorizationAccount(authorizationTracker, authority, ZERO_NONCE, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = spy(createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ONE_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                new byte[] { 1, 2, 3 },
                authorization
        ));


        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verify(tx, never()).isInitCodeSizeInvalidForTx(any());
        verifyValidAuthorityChanges(authorizationTracker, authority, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void typedTransactionIsRejectedWhenNotAllowedBeforeActivation() {
        Transaction tx = mock(Transaction.class);

        when(tx.transactionCost(any(), any(), any())).thenReturn(21_000L);
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(true);
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_4));
        when(tx.getHash()).thenReturn(null);

        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());

        verify(tx).isTypedTransactionNotAllowed(any());
        verify(tracker, never()).increaseNonce(any());
        verify(tracker, never()).addBalance(any(), any());
    }

    @Test
    void type4TransactionRejectsContractCreationBeforeExecution() {
        Transaction tx = mock(Transaction.class);

        when(tx.transactionCost(any(), any(), any())).thenReturn(21_000L);
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(false);
        when(tx.isType4()).thenReturn(true);
        when(tx.isContractCreation()).thenReturn(true);
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_4));
        when(tx.getHash()).thenReturn(null);

        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());

        verify(tx).isContractCreation();
        verify(tracker, never()).increaseNonce(any());
        verify(tracker, never()).addBalance(any(), any());
    }

    @Test
    void nonType4TransactionDoesNotValidateSenderCode() {
        Transaction tx = mock(Transaction.class);

        when(tx.transactionCost(any(), any(), any())).thenReturn(21_000L);
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(false);
        when(tx.isType4()).thenReturn(false);
        when(tx.isInitCodeSizeInvalidForTx(any())).thenReturn(false);
        when(tx.getGasLimit()).thenReturn(BigInteger.valueOf(600_000).toByteArray());
        when(tx.getNonce()).thenReturn(ONE_NONCE.toByteArray());
        when(tx.getValue()).thenReturn(Coin.ZERO);
        when(tx.getGasPrice()).thenReturn(Coin.valueOf(1));
        when(tx.getSender(any())).thenReturn(sender);
        when(tx.getReceiveAddress()).thenReturn(receiver);
        when(tx.acceptTransactionSignature(anyByte())).thenReturn(true);

        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);
        mockFreeBridgeTxFalse();
        mockValidSender(sender, 1_000_000, ONE_NONCE, new byte[] { 0x60, 0x00 });
        mockExecutionBlockGasLimit(6_800_000);
        mockReceiver(receiver, EMPTY_CODE);

        var cacheTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker);
        var txExecutor = newExecutor(tx);
        assertTrue(txExecutor.executeTransaction());
        // sender code must not block non-Type4 txs
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
    }

    @Test
    void type4TransactionWithOnlyInvalidAuthorizationsStillChargesGasAndExecutesOuterTx() {
        var authorityKey = new ECKey();
        var authority = new RskAddress(authorityKey.getAddress());

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(authorizationTracker, authority, ONE_NONCE, EMPTY_CODE);
        var invalidAuthorization = createValidAuthorizationTuple(
                delegatedAddress,
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
                invalidAuthorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyInvalidAuthorityChanges(authorizationTracker, authority, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
    }

    @Test
    void type4TransactionWithInvalidAuthorizationDoesNotMutateAuthorityCodeOrNonce() {
        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        byte[] arbitraryContractCode = new byte[] { 0x60, 0x00, 0x60, 0x00 };
        mockAuthorizationAccount(authorizationTracker, authorityAddress, ZERO_NONCE, arbitraryContractCode);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
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

        verify(authorizationTracker).rollback();
        verify(authorizationTracker, never()).saveCode(any(), any());
        verify(authorizationTracker, never()).increaseNonce(any());
        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTransfer(cacheTracker, sender, 2);
    }

    @Test
    void type4TransactionWithInvalidAuthorizationNonceSkipsAuthorizationButExecutesOuterTx() {


        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        var invalidSignatureAuthorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        mockAuthorizationAccount(authorizationTracker, authorityAddress, ONE_NONCE, EMPTY_CODE);

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
                invalidSignatureAuthorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyInvalidAuthorityChanges(authorizationTracker, authorityAddress, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
    }

    @Test
    void type4TransactionWithAuthorizationSignedOverWrongMessageDoesNotMutateOriginalAuthorityButExecutesOuterTx() {
        ECDSASignature wrongMessageSignature = ECDSASignature.fromSignature(
                authorityKey.sign(HashUtil.keccak256(new byte[0]))
        );

        var cacheTracker = mock(MutableRepository.class);
        var authorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, authorizationTracker);

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        mockAuthorizationAccount(
                authorizationTracker,
                authorityAddress,
                ZERO_NONCE,
                EMPTY_CODE
        );

        var authorization = new SetCodeAuthorization(
                BigInteger.valueOf(constants.getChainId()),
                delegatedAddress,
                ZERO_NONCE.toByteArray(),
                wrongMessageSignature
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

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);

        verify(authorizationTracker, never()).saveCode(eq(authorityAddress), any());
        verify(authorizationTracker, never()).increaseNonce(eq(authorityAddress));

        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
    }

    @Test
    void shouldChargeGasForDuplicateAuthorizationTupleAndApplyOnlyFirst() {
        var cacheTracker = mock(MutableRepository.class);
        var firstAuthorizationTracker = mock(MutableRepository.class);
        var secondAuthorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(cacheTracker, firstAuthorizationTracker, secondAuthorizationTracker);

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        mockAuthorizationAccount(firstAuthorizationTracker, authorityAddress, ZERO_NONCE, EMPTY_CODE);

        byte[] delegatedCode = DelegationCodeResolver.createDelegatedCode(delegatedAddress);
        mockAuthorizationAccount(secondAuthorizationTracker, authorityAddress, ONE_NONCE, delegatedCode);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
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
                authorization,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(3);

        verifyValidAuthorityChanges(firstAuthorizationTracker, authorityAddress, delegatedAddress);

        verifyInvalidAuthorityChanges(secondAuthorizationTracker, authorityAddress, delegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST * 2L);
    }

    @Test
    void shouldApplySecondAuthorizationWhenFirstAuthorizationIsInvalid() {
        var firstDelegatedAddress = createRandomAddress();
        var secondDelegatedAddress = createRandomAddress();

        var cacheTracker = mock(MutableRepository.class);
        var firstAuthorizationTracker = mock(MutableRepository.class);
        var secondAuthorizationTracker = mock(MutableRepository.class);

        when(tracker.startTracking()).thenReturn(
                cacheTracker,
                firstAuthorizationTracker,
                secondAuthorizationTracker
        );

        mockValidSender(sender, 1_000_000, ONE_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);
        mockAuthorizationAccount(firstAuthorizationTracker, authorityAddress, ONE_NONCE, EMPTY_CODE);
        mockAuthorizationAccount(secondAuthorizationTracker, authorityAddress, ONE_NONCE, EMPTY_CODE);

        var invalidFirstAuthorization = createValidAuthorizationTuple(
                firstDelegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var validSecondAuthorization = createValidAuthorizationTuple(
                secondDelegatedAddress,
                ONE_NONCE,
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
                invalidFirstAuthorization,
                validSecondAuthorization
        );

        var txExecutor = newExecutor(tx);

        assertTrue(txExecutor.executeTransaction());

        verifyTrackerIncreaseNonceAndReduceBalance(sender, 600_000);
        verifyTrackerStartTrackingInvocations(3);
        verifyInvalidAuthorityChanges(firstAuthorizationTracker, authorityAddress, firstDelegatedAddress);
        verifyValidAuthorityChanges(secondAuthorizationTracker, authorityAddress, secondDelegatedAddress);
        verifyTransfer(cacheTracker, sender, 2);
        verifyTransactionCostBiggerOrEqualThan(tx, GasCost.PER_EMPTY_ACCOUNT_COST * 2L);
    }

    @Test
    void shouldRejectTransactionWhenBalanceCannotCoverAuthorizationCosts() {
        var authorizationTracker = mock(MutableRepository.class);
        when(tracker.startTracking()).thenReturn(authorizationTracker);

        long intrinsicCostWithoutAuth = 21_000L;
        long authCost = GasCost.PER_EMPTY_ACCOUNT_COST;
        long requiredGasCost = intrinsicCostWithoutAuth + authCost;
        long senderBalance = requiredGasCost - 1;

        mockAccountWithBalanceAndNonce(tracker, sender, senderBalance, ONE_NONCE);
        mockAuthorizationAccount(authorizationTracker, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        mockReceiver(receiver, EMPTY_CODE);

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
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
                0,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx);

        assertFalse(txExecutor.executeTransaction());

        verify(tracker, never()).increaseNonce(sender);
        verify(tracker, never()).addBalance(eq(sender), any(Coin.class));
        verify(authorizationTracker, never()).saveCode(any(), any());
        verify(authorizationTracker, never()).increaseNonce(any());
        verify(authorizationTracker, never()).commit();
    }

}
