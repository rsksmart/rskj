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
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

class Type4TransactionExecutorWithRealRepositoryTests extends Type4TransactionExecutorHelperTest {


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
                authorityAddress, // tx.to == authority whose code is changed
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
    void type4TransactionToAccountDelegatedToPrecompileExecutesEmptyCodeAndDoesNotCallPrecompileWithRealRepository() throws VMException {
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

        assertSenderNonceAndFinalBalance(repository, ONE_NONCE, 1_000_000-46000-2);
        assertAuthorityDelegatedTo(repository, authorityAddress, authorization.getAddress());
        assertValueTransferredToReceiver(repository, receiver, 2);
        assertTransactionCostCharged(tx, GasCost.PER_EMPTY_ACCOUNT_COST);
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

    private void saveCode(MutableRepository repository, RskAddress address, byte[] code) {
        repository.createAccount(address);
        repository.saveCode(address, code);
    }

    private void assertSenderNonceAndFinalBalance(MutableRepository repository, BigInteger newNonce, long finalBalance) {
        assertEquals(newNonce, repository.getNonce(sender));
        assertEquals(Coin.valueOf(finalBalance), repository.getBalance(sender));
    }

    private void assertAuthorityDelegatedTo(
            MutableRepository repository,
            RskAddress authority,
            RskAddress delegatedAddress
    ) {
        byte[] expected = DelegationCodeResolver.createDelegatedCode(delegatedAddress);
        byte[] actual = repository.getCode(authority);
        assertArrayEquals(expected, actual, "Authority code does not contain the expected delegation designator");
    }

    private void assertValueTransferredToReceiver(MutableRepository repository, RskAddress receiver, long value) {
        assertEquals(Coin.valueOf(value), repository.getBalance(receiver));
    }

    private void assertTransactionCostCharged(Transaction tx, long expectedAuthorizationCost) {
        assertTrue(tx.transactionCost(
                constants,
                activationConfig.forBlock(executionBlock.getNumber()),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        ) >= expectedAuthorizationCost);
    }

    private void prepareAuthority(MutableRepository repository, RskAddress authority, BigInteger nonce, byte[] code) {
        repository.createAccount(authority);
        repository.setNonce(authority, nonce);
        repository.saveCode(authority, code);
    }

    protected ProgramInvoke mockSuccessfulProgramInvokeForAnyRepository(Transaction tx) {
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
