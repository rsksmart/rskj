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

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.transactionexecutor.helper.Type4TransactionExecutorHelperTest;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Type 4 transaction executor integration tests backed by a real {@link MutableRepository}.
 * Uses mocked outer execution where VM behavior is not under test, and a full VM path via
 * {@link ProgramInvokeFactoryImpl} for delegation context and gas/refund accounting.
 */
class Type4TransactionExecutorWithRealRepositoryTests extends Type4TransactionExecutorHelperTest {

    private static final byte[] ARBITRARY_CONTRACT_CODE = Hex.decode("60006000f3");
    private static final byte[] DELEGATED_PREFIX_ONLY = new byte[] {(byte) 0xef, 0x01, 0x00};
    private static final byte[] DELEGATED_WRONG_LENGTH = new byte[] {(byte) 0xef, 0x01, 0x00, 0x01};
    /** Stores 0x42 at slot 0, then stores ADDRESS at slot 1. */
    private static final byte[] DELEGATION_CONTEXT_PROBE = Hex.decode("60426000553060015500");
    private static final long DELEGATION_REPLACEMENT_REFUND =
            GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST;

    // -------------------------------------------------------------------------
    // Invalid authorization handling
    // -------------------------------------------------------------------------

    @Test
    void wrongSignature_doesNotMutateAuthorityCodeOrNonce() {
        MutableRepository repository = createRepository();
        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);
        prepareReceiver(repository);

        ECDSASignature wrongMessageSignature = ECDSASignature.fromSignature(
                authorityKey.sign(HashUtil.keccak256(new byte[0]))
        );
        SetCodeAuthorization authorization = new SetCodeAuthorization(
                BigInteger.valueOf(constants.getChainId()),
                delegatedAddress,
                ZERO_NONCE.toByteArray(),
                wrongMessageSignature
        );

        Transaction tx = createSignedType4Transaction(
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
        mockSuccessfulProgramInvokeForAnyRepository(tx);

        assertTrue(newExecutorWithRealSignatureCache(tx, repository).executeTransaction());

        assertAuthorityStateUnchanged(repository, authorityAddress, EMPTY_CODE, ZERO_NONCE);
        assertEquals(ONE_NONCE, repository.getNonce(sender));
        assertEquals(Coin.valueOf(2), repository.getBalance(receiver));
    }

    @Test
    void nonceMismatch_doesNotMutateAuthorityCodeOrNonce() {
        MutableRepository repository = createRepository();
        prepareAuthority(repository, authorityAddress, ONE_NONCE, EMPTY_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);
        prepareReceiver(repository);

        SetCodeAuthorization authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        Transaction tx = createSignedType4Transaction(
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
        mockSuccessfulProgramInvokeForAnyRepository(tx);

        assertTrue(newExecutorWithRealSignatureCache(tx, repository).executeTransaction());

        assertAuthorityStateUnchanged(repository, authorityAddress, EMPTY_CODE, ONE_NONCE);
        assertEquals(ONE_NONCE, repository.getNonce(sender));
        assertEquals(Coin.valueOf(2), repository.getBalance(receiver));
    }

    @Test
    void authorityWithArbitraryContractCode_remainsUnchangedOnChain() {
        MutableRepository repository = createRepository();
        prepareAuthority(repository, authorityAddress, ZERO_NONCE, ARBITRARY_CONTRACT_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);
        prepareReceiver(repository);

        SetCodeAuthorization authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        Transaction tx = createSignedType4Transaction(
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
        mockSuccessfulProgramInvokeForAnyRepository(tx);

        assertTrue(newExecutorWithRealSignatureCache(tx, repository).executeTransaction());

        assertAuthorityStateUnchanged(repository, authorityAddress, ARBITRARY_CONTRACT_CODE, ZERO_NONCE);
        assertEquals(ONE_NONCE, repository.getNonce(sender));
    }

    // -------------------------------------------------------------------------
    // Sender code validation
    // -------------------------------------------------------------------------

    @Test
    void rejectsSenderWithArbitraryContractCode() {
        MutableRepository repository = createRepository();
        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSenderWithCode(repository, ZERO_NONCE, 1_000_000, ARBITRARY_CONTRACT_CODE);
        prepareReceiver(repository);

        SetCodeAuthorization authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        Transaction tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                600_000,
                1,
                1,
                receiver,
                0,
                EMPTY_DATA,
                authorization
        );

        assertFalse(newExecutorWithRealSignatureCache(tx, repository).executeTransaction());
        assertEquals(ZERO_NONCE, repository.getNonce(sender));
        assertAuthorityStateUnchanged(repository, authorityAddress, EMPTY_CODE, ZERO_NONCE);
    }

    @Test
    void acceptsSenderWithValidDelegatedCode() {
        MutableRepository repository = createRepository();
        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        byte[] senderDelegation = DelegationCodeResolver.createDelegatedCode(delegatedAddress);
        fundSenderWithCode(repository, ZERO_NONCE, 1_000_000, senderDelegation);
        saveCode(repository, delegatedAddress, new byte[] {0x00});
        prepareReceiver(repository);
        mockAddressAsNotAPrecompiled(delegatedAddress);

        SetCodeAuthorization authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        Transaction tx = createSignedType4Transaction(
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
        mockSuccessfulProgramInvokeForAnyRepository(tx);

        TransactionExecutor executor = newExecutorWithRealSignatureCache(tx, repository);
        assertTrue(executor.executeTransaction(), () -> "Type 4 tx with delegated sender must execute");
        assertEquals(ONE_NONCE, repository.getNonce(sender));
        assertArrayEquals(senderDelegation, normalizeCode(repository.getCode(sender)));
        assertAuthorityDelegatedTo(repository, authorityAddress, delegatedAddress);
    }

    @Test
    void rejectsSenderWithDelegatedCodePrefixOnly() {
        MutableRepository repository = createRepository();
        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSenderWithCode(repository, ZERO_NONCE, 1_000_000, DELEGATED_PREFIX_ONLY);
        prepareReceiver(repository);

        SetCodeAuthorization authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        Transaction tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                600_000,
                1,
                1,
                receiver,
                0,
                EMPTY_DATA,
                authorization
        );

        assertFalse(newExecutorWithRealSignatureCache(tx, repository).executeTransaction());
        assertEquals(ZERO_NONCE, repository.getNonce(sender));
        assertArrayEquals(DELEGATED_PREFIX_ONLY, normalizeCode(repository.getCode(sender)));
    }

    @Test
    void rejectsSenderWithDelegatedCodeWrongLength() {
        MutableRepository repository = createRepository();
        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSenderWithCode(repository, ZERO_NONCE, 1_000_000, DELEGATED_WRONG_LENGTH);
        prepareReceiver(repository);

        SetCodeAuthorization authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        Transaction tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                600_000,
                1,
                1,
                receiver,
                0,
                EMPTY_DATA,
                authorization
        );

        assertFalse(newExecutorWithRealSignatureCache(tx, repository).executeTransaction());
        assertEquals(ZERO_NONCE, repository.getNonce(sender));
        assertArrayEquals(DELEGATED_WRONG_LENGTH, normalizeCode(repository.getCode(sender)));
    }

    @Test
    void nonType4TransactionDoesNotValidateSenderCode() {
        MutableRepository repository = createRepository();
        fundSenderWithCode(repository, ZERO_NONCE, 1_000_000, ARBITRARY_CONTRACT_CODE);
        prepareReceiver(repository);
        mockAddressAsNotAPrecompiled(receiver);

        Transaction legacyTx = Transaction.builder()
                .nonce(ZERO_NONCE)
                .gasLimit(BigInteger.valueOf(600_000))
                .gasPrice(Coin.valueOf(1))
                .receiveAddress(receiver)
                .value(Coin.ZERO)
                .data(EMPTY_DATA)
                .build();
        legacyTx.sign(senderKey.getPrivKeyBytes());

        mockSuccessfulProgramInvokeForAnyRepository(legacyTx);

        assertTrue(newExecutorWithRealSignatureCache(legacyTx, repository).executeTransaction());
        assertEquals(ONE_NONCE, repository.getNonce(sender));
        assertArrayEquals(ARBITRARY_CONTRACT_CODE, normalizeCode(repository.getCode(sender)));
    }

    // -------------------------------------------------------------------------
    // Gas and refund accounting (full VM)
    // -------------------------------------------------------------------------

    @Test
    void delegationRefundWhenAuthorityAlreadyDelegatedOnRealRepository() {
        MutableRepository repository = createRepository();
        RskAddress previousDelegate = createRandomAddress();
        byte[] existingDelegation = DelegationCodeResolver.createDelegatedCode(previousDelegate);

        prepareAuthority(repository, authorityAddress, ONE_NONCE, existingDelegation);
        fundSender(repository, ZERO_NONCE, 1_000_000);
        prepareReceiver(repository);
        mockExecutionBlockForRealVm();

        SetCodeAuthorization authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ONE_NONCE,
                constants.getChainId(),
                authorityKey
        );

        Transaction tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_BYTE_ARRAY,
                authorization
        );

        TransactionExecutor executor = newRealVmExecutor(tx, repository);

        assertTrue(executor.executeTransaction());
        assertEquals(DELEGATION_REPLACEMENT_REFUND, executor.getResult().getDeductedRefund(),
                "Replacing existing delegation must refund PER_EMPTY_ACCOUNT_COST - PER_AUTH_BASE_COST");
        assertAuthorityDelegatedTo(repository, authorityAddress, delegatedAddress);
        assertEquals(BigInteger.valueOf(2), repository.getNonce(authorityAddress));
    }

    @Test
    void duplicateAuthorizationTuple_chargesUpfrontCostOnRealRepository() {
        MutableRepository repository = createRepository();

        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);
        prepareReceiver(repository);
        mockExecutionBlockForRealVm();

        SetCodeAuthorization authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        Transaction tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                600_000,
                1,
                1,
                receiver,
                0,
                EMPTY_BYTE_ARRAY,
                authorization,
                authorization
        );

        long expectedIntrinsic = GasCost.TRANSACTION + 2 * GasCost.PER_EMPTY_ACCOUNT_COST;
        assertEquals(expectedIntrinsic, intrinsicGasCost(tx),
                "Duplicate tuples must each incur PER_EMPTY_ACCOUNT_COST in transactionCost()");

        TransactionExecutor executor = newRealVmExecutor(tx, repository);

        assertTrue(executor.executeTransaction());
        assertAuthorityDelegatedTo(repository, authorityAddress, delegatedAddress);
        assertEquals(ONE_NONCE, repository.getNonce(authorityAddress),
                "Second duplicate tuple must be skipped after first increments authority nonce");
    }

    @Test
    void invalidOnlyAuthorizationStillChargesUpfrontCostOnRealRepository() {
        MutableRepository repository = createRepository();

        prepareAuthority(repository, authorityAddress, ONE_NONCE, EMPTY_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);
        prepareReceiver(repository);
        mockExecutionBlockForRealVm();

        SetCodeAuthorization invalidAuthorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        Transaction tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                600_000,
                1,
                1,
                receiver,
                0,
                EMPTY_BYTE_ARRAY,
                invalidAuthorization
        );

        assertEquals(GasCost.TRANSACTION + GasCost.PER_EMPTY_ACCOUNT_COST, intrinsicGasCost(tx),
                "Invalid tuple still incurs upfront PER_EMPTY_ACCOUNT_COST in transactionCost()");

        TransactionExecutor executor = newRealVmExecutor(tx, repository);

        assertTrue(executor.executeTransaction());
        assertArrayEquals(EMPTY_BYTE_ARRAY, normalizeCode(repository.getCode(authorityAddress)));
        assertEquals(ONE_NONCE, repository.getNonce(authorityAddress));
        assertEquals(ONE_NONCE, repository.getNonce(sender),
                "Outer transaction must still execute despite invalid authorization");
    }

    // -------------------------------------------------------------------------
    // Delegated execution context (full VM)
    // -------------------------------------------------------------------------

    @Test
    void executesInAuthorityContextOnRealRepository() {
        MutableRepository repository = createRepository();
        RskAddress delegatedReceiver = receiver;
        RskAddress delegate = delegatedAddress;

        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);
        saveCode(repository, delegatedReceiver, DelegationCodeResolver.createDelegatedCode(delegate));
        saveCode(repository, delegate, DELEGATION_CONTEXT_PROBE);
        mockExecutionBlockForRealVm();

        var authorization = createValidAuthorizationTuple(
                createRandomAddress(),
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                600_000,
                1,
                1,
                delegatedReceiver,
                0,
                EMPTY_BYTE_ARRAY,
                authorization
        );

        TransactionExecutor executor = newRealVmExecutor(tx, repository);

        assertTrue(executor.executeTransaction(), "Type 4 call to delegated receiver must succeed");
        assertNull(executor.getResult().getException(), "Delegate probe must execute without VM error");

        assertEquals(DataWord.valueOf(0x42), repository.getStorageValue(delegatedReceiver, DataWord.ZERO),
                "Storage writes from delegate code must persist on the delegating receiver account");
        assertEquals(DataWord.valueOf(delegatedReceiver.getBytes()),
                repository.getStorageValue(delegatedReceiver, DataWord.valueOf(1)),
                "ADDRESS opcode must resolve to the delegating receiver, not the delegate contract");
        assertAuthorityDelegatedTo(repository, authorityAddress, authorization.getAddress());
    }

    @Test
    void delegateAccountRemainsUnchangedOnRealRepository() {
        MutableRepository repository = createRepository();
        RskAddress delegatedReceiver = receiver;
        RskAddress delegate = delegatedAddress;

        byte[] delegateCodeBefore = DELEGATION_CONTEXT_PROBE;
        BigInteger delegateNonceBefore = BigInteger.valueOf(7);
        Coin delegateBalanceBefore = Coin.valueOf(12_345);
        DataWord delegateStorageKey = DataWord.valueOf(99);
        DataWord delegateStorageBefore = DataWord.valueOf(0xAB);

        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);
        saveCode(repository, delegatedReceiver, DelegationCodeResolver.createDelegatedCode(delegate));
        repository.createAccount(delegate);
        repository.saveCode(delegate, delegateCodeBefore);
        repository.setNonce(delegate, delegateNonceBefore);
        repository.addBalance(delegate, delegateBalanceBefore);
        repository.addStorageRow(delegate, delegateStorageKey, delegateStorageBefore);
        mockExecutionBlockForRealVm();

        var authorization = createValidAuthorizationTuple(
                createRandomAddress(),
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                600_000,
                1,
                1,
                delegatedReceiver,
                0,
                EMPTY_BYTE_ARRAY,
                authorization
        );

        TransactionExecutor executor = newRealVmExecutor(tx, repository);

        assertTrue(executor.executeTransaction());
        assertNull(executor.getResult().getException());

        assertEquals(DataWord.valueOf(0x42), repository.getStorageValue(delegatedReceiver, DataWord.ZERO),
                "Delegator storage must reflect delegate bytecode execution");
        assertArrayEquals(delegateCodeBefore, repository.getCode(delegate),
                "Delegate bytecode must remain unchanged");
        assertEquals(delegateNonceBefore, repository.getNonce(delegate),
                "Delegate nonce must remain unchanged");
        assertEquals(delegateBalanceBefore, repository.getBalance(delegate),
                "Delegate balance must remain unchanged");
        assertEquals(delegateStorageBefore, repository.getStorageValue(delegate, delegateStorageKey),
                "Delegate storage trie must remain unchanged");
    }

    // -------------------------------------------------------------------------
    // Authorization, delegation resolution, and precompile behavior (mocked outer VM)
    // -------------------------------------------------------------------------

    @Test
    void type4TransactionReceiverIsAuthorityUsesUpdatedDelegatedCodeForExecutionWithRealRepository() {
        MutableRepository repository = createRepository();

        RskAddress delegate = delegatedAddress;
        byte[] delegateCode = new byte[] { 0x00 }; // STOP

        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);
        saveCode(repository, delegate, delegateCode);

        mockAddressAsNotAPrecompiled(authorityAddress);
        mockAddressAsNotAPrecompiled(delegate);

        var authorization = createValidAuthorizationTuple(
                delegate,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
        );

        var tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                600_000,
                1,
                1,
                authorityAddress,
                2,
                EMPTY_DATA,
                authorization
        );

        mockSuccessfulProgramInvokeForAnyRepository(tx);

        var txExecutor = newExecutor(tx, repository);

        assertTrue(txExecutor.executeTransaction());

        verify(programInvokeFactory).createProgramInvoke(
                eq(tx),
                eq(txIndex),
                eq(executionBlock),
                any(),
                eq(blockStore),
                any(SignatureCache.class)
        );

        assertSenderNonceAndFinalBalance(repository, ONE_NONCE, 1_000_000 - 46_000 - 2);
        assertAuthorityDelegatedTo(repository, authorityAddress, delegate);
        assertValueTransferredToReceiver(repository, authorityAddress, 2);
        assertTransactionCostCharged(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
    }

    @Test
    void type4TransactionToAccountDelegatedToPrecompileExecutesEmptyCodeAndDoesNotCallPrecompileWithRealRepository()
            throws VMException {
        MutableRepository repository = createRepository();

        var precompile = mock(PrecompiledContracts.PrecompiledContract.class);

        byte[] receiverDelegationCode = DelegationCodeResolver.createDelegatedCode(PrecompiledContracts.REMASC_ADDR);

        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);
        saveCode(repository, receiver, receiverDelegationCode);

        mockAddressAsNotAPrecompiled(receiver);

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
                ZERO_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx, repository);

        assertTrue(txExecutor.executeTransaction());

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

        assertSenderNonceAndFinalBalance(repository, ONE_NONCE, 1_000_000 - 46_000 - 2);
        assertAuthorityDelegatedTo(repository, authorityAddress, authorization.getAddress());
        assertValueTransferredToReceiver(repository, receiver, 2);
        assertTransactionCostCharged(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
    }

    @Test
    void type4TransactionToDelegatedAccountResolvesDelegationOnlyOnceWithRealRepository() {
        MutableRepository repository = createRepository();

        var bob = createRandomAddress();
        var charlie = createRandomAddress();

        byte[] receiverDelegationCode = DelegationCodeResolver.createDelegatedCode(bob);
        byte[] bobDelegationCode = DelegationCodeResolver.createDelegatedCode(charlie);

        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);
        saveCode(repository, receiver, receiverDelegationCode);
        saveCode(repository, bob, bobDelegationCode);

        mockAddressAsNotAPrecompiled(receiver);
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
                ZERO_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        mockSuccessfulProgramInvokeForAnyRepository(tx);

        var txExecutor = newExecutor(tx, repository);

        assertTrue(txExecutor.executeTransaction());

        verify(programInvokeFactory).createProgramInvoke(
                eq(tx),
                eq(txIndex),
                eq(executionBlock),
                any(),
                eq(blockStore),
                any(SignatureCache.class)
        );

        assertNotNull(txExecutor.getResult().getException());
        assertTrue(txExecutor.getResult().getException().getMessage().contains("opcode[ef]"));

        assertSenderNonceAndFinalBalance(repository, ONE_NONCE, 1_000_000 - 600_000);
        assertAuthorityDelegatedTo(repository, authorityAddress, authorization.getAddress());
        assertTransactionCostCharged(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
    }

    @Test
    void type4TransactionToDelegatedAccountLoadsDelegateCodeForExecutionWithRealRepository() {
        MutableRepository repository = createRepository();
        byte[] receiverDelegationCode = DelegationCodeResolver.createDelegatedCode(delegatedAddress);
        byte[] delegateCode = new byte[] { 0x00 }; // STOP

        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);
        saveCode(repository, receiver, receiverDelegationCode);
        saveCode(repository, delegatedAddress, delegateCode);

        mockAddressAsNotAPrecompiled(receiver);
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
                ZERO_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );
        mockSuccessfulProgramInvokeForAnyRepository(tx);
        var txExecutor = newExecutor(tx, repository);
        assertTrue(txExecutor.executeTransaction());

        verify(programInvokeFactory).createProgramInvoke(
                eq(tx),
                eq(txIndex),
                eq(executionBlock),
                any(),
                eq(blockStore),
                any(SignatureCache.class)
        );

        assertSenderNonceAndFinalBalance(repository, ONE_NONCE, 1_000_000 - 46_000 - 2);
        assertAuthorityDelegatedTo(repository, authorityAddress, authorization.getAddress());
        assertValueTransferredToReceiver(repository, receiver, 2);
        assertTransactionCostCharged(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
    }

    @Test
    void outerPrecompileFailurePreservesAuthorizationOnRealRepository() throws VMException {
        MutableRepository repository = createRepository();

        var precompile = mock(PrecompiledContracts.PrecompiledContract.class);

        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);

        when(precompiledContracts.getContractForAddress(
                any(ActivationConfig.ForBlock.class),
                eq(DataWord.valueOf(receiver.getBytes()))
        )).thenReturn(precompile);

        when(precompile.getGasForData(any())).thenReturn(1L);
        when(precompile.execute(any())).thenThrow(new RuntimeException("outer tx precompiled failure"));

        var authorization = createValidAuthorizationTuple(
                delegatedAddress,
                ZERO_NONCE,
                constants.getChainId(),
                authorityKey
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

        var txExecutor = newExecutor(tx, repository);

        assertTrue(txExecutor.executeTransaction());
        assertNotNull(txExecutor.getResult().getException(),
                "Outer precompile failure must surface as a transaction exception");

        assertAuthorityDelegatedTo(repository, authorityAddress, delegatedAddress);
        assertEquals(ONE_NONCE, repository.getNonce(authorityAddress),
                "Processed authorization must commit authority nonce despite outer failure");
        assertEquals(Coin.ZERO, repository.getBalance(receiver),
                "Value transfer must not occur when outer execution fails");
        assertEquals(ONE_NONCE, repository.getNonce(sender));
    }

    @Test
    void type4TransactionDirectlyToPrecompileKeepsCurrentPrecompileBehaviorWithRealRepository()
            throws VMException {
        MutableRepository repository = createRepository();

        var precompile = mock(PrecompiledContracts.PrecompiledContract.class);
        byte[] precompileOutput = new byte[] { 0x01, 0x02 };

        prepareAuthority(repository, authorityAddress, ZERO_NONCE, EMPTY_CODE);
        fundSender(repository, ZERO_NONCE, 1_000_000);

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
                ZERO_NONCE,
                600_000,
                1,
                1,
                receiver,
                2,
                EMPTY_DATA,
                authorization
        );

        var txExecutor = newExecutor(tx, repository);

        assertTrue(txExecutor.executeTransaction());

        verify(precompile).init(any());
        verify(precompile).getGasForData(EMPTY_DATA);
        verify(precompile).execute(EMPTY_DATA);

        verify(programInvokeFactory, never()).createProgramInvoke(
                any(),
                anyInt(),
                any(),
                any(),
                any(),
                any()
        );

        assertEquals(precompileOutput, txExecutor.getResult().getHReturn());

        assertSenderNonceAndFinalBalance(repository, ONE_NONCE, 1_000_000 - 46_001 - 2);
        assertAuthorityDelegatedTo(repository, authorityAddress, authorization.getAddress());
        assertValueTransferredToReceiver(repository, receiver, 2);
        assertTransactionCostCharged(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TransactionExecutor newExecutorWithRealSignatureCache(Transaction tx, Repository repository) {
        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        TransactionExecutorFactory factory = new TransactionExecutorFactory(
                config,
                blockStore,
                receiptStore,
                blockFactory,
                programInvokeFactory,
                precompiledContracts,
                signatureCache
        );
        return factory.newInstance(
                tx,
                txIndex,
                executionBlock.getCoinbase(),
                repository,
                executionBlock,
                0L
        );
    }

    private TransactionExecutor newRealVmExecutor(Transaction tx, Repository repository) {
        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(
                        config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                config.getNetworkConstants().getBridgeConstants(),
                config.getActivationConfig(),
                signatureCache
        );
        TransactionExecutorFactory factory = new TransactionExecutorFactory(
                config,
                blockStore,
                receiptStore,
                new BlockFactory(config.getActivationConfig()),
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, bridgeSupportFactory, signatureCache),
                signatureCache
        );
        return factory.newInstance(
                tx,
                txIndex,
                executionBlock.getCoinbase(),
                repository,
                executionBlock,
                0L
        );
    }

    private void mockExecutionBlockForRealVm() {
        when(executionBlock.getParentHash()).thenReturn(Keccak256.ZERO_HASH);
        when(executionBlock.getCoinbase()).thenReturn(RskAddress.nullAddress());
        when(executionBlock.getTimestamp()).thenReturn(1L);
        when(executionBlock.getDifficulty()).thenReturn(new BlockDifficulty(BigInteger.ONE));
        when(executionBlock.getMinimumGasPrice()).thenReturn(Coin.ZERO);
    }

    private long intrinsicGasCost(Transaction tx) {
        return tx.transactionCost(
                constants,
                activationConfig.forBlock(executionBlock.getNumber()),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        );
    }

    private MutableRepository createRepository() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableTrieImpl mutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        return new MutableRepository(mutableTrie);
    }

    private void fundSender(MutableRepository repository, BigInteger nonce, long balance) {
        repository.createAccount(sender);
        repository.addBalance(sender, Coin.valueOf(balance));
        repository.setNonce(sender, nonce);
    }

    private void fundSenderWithCode(MutableRepository repository, BigInteger nonce, long balance, byte[] code) {
        fundSender(repository, nonce, balance);
        repository.saveCode(sender, code);
    }

    private void saveCode(MutableRepository repository, RskAddress address, byte[] code) {
        repository.createAccount(address);
        repository.saveCode(address, code);
    }

    private void prepareReceiver(MutableRepository repository) {
        repository.createAccount(receiver);
    }

    private void prepareAuthority(MutableRepository repository, RskAddress authority, BigInteger nonce, byte[] code) {
        repository.createAccount(authority);
        repository.setNonce(authority, nonce);
        repository.saveCode(authority, code);
    }

    private void assertAuthorityStateUnchanged(
            MutableRepository repository,
            RskAddress authority,
            byte[] expectedCode,
            BigInteger expectedNonce
    ) {
        assertArrayEquals(normalizeCode(expectedCode), normalizeCode(repository.getCode(authority)),
                "Authority code must remain unchanged after invalid authorization");
        assertEquals(expectedNonce, repository.getNonce(authority),
                "Authority nonce must remain unchanged after invalid authorization");
    }

    private static byte[] normalizeCode(byte[] code) {
        return code == null ? EMPTY_BYTE_ARRAY : code;
    }

    private void assertSenderNonceAndFinalBalance(MutableRepository repository, BigInteger newNonce, long finalBalance) {
        assertEquals(newNonce, repository.getNonce(sender));
        assertEquals(Coin.valueOf(finalBalance), repository.getBalance(sender));
    }

    private void assertAuthorityDelegatedTo(
            MutableRepository repository,
            RskAddress authority,
            RskAddress delegatedTarget
    ) {
        byte[] expected = DelegationCodeResolver.createDelegatedCode(delegatedTarget);
        assertArrayEquals(expected, repository.getCode(authority));
    }

    private void assertValueTransferredToReceiver(MutableRepository repository, RskAddress receiverAddress, long value) {
        assertEquals(Coin.valueOf(value), repository.getBalance(receiverAddress));
    }

    private void assertTransactionCostCharged(Transaction tx, long expectedAuthorizationCost) {
        assertTrue(tx.transactionCost(
                constants,
                activationConfig.forBlock(executionBlock.getNumber()),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        ) >= expectedAuthorizationCost);
    }

    private ProgramInvoke mockSuccessfulProgramInvokeForAnyRepository(Transaction tx) {
        ProgramInvoke programInvoke = mock(ProgramInvoke.class);

        when(programInvokeFactory.createProgramInvoke(
                eq(tx),
                eq(txIndex),
                eq(executionBlock),
                any(),
                eq(blockStore),
                any(SignatureCache.class)
        )).thenReturn(programInvoke);

        when(programInvoke.getOwnerAddress())
                .thenReturn(DataWord.valueOf(tx.getReceiveAddress().getBytes()));

        when(programInvoke.getCallerAddress())
                .thenReturn(DataWord.valueOf(sender.getBytes()));

        when(programInvoke.getBalance())
                .thenReturn(DataWord.ZERO);

        when(programInvoke.getCallValue())
                .thenReturn(DataWord.valueOf(tx.getValue().getBytes()));

        when(programInvoke.getDataSize())
                .thenReturn(DataWord.valueOf(tx.getData().length));

        when(programInvoke.getGas())
                .thenReturn(GasCost.toGas(tx.getGasLimit()));

        return programInvoke;
    }
}
