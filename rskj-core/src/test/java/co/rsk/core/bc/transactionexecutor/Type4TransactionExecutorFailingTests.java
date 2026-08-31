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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;


class Type4TransactionExecutorFailingTests extends Type4TransactionExecutorHelperTest {

    private static final long FAKE_PRECOMPILE_REQUIRED_GAS = 1_000L;
    private static final byte[] REVERT_CODE = Hex.decode("60006000fd");

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void authorizationRefundOnPlainVmExceptionBehavesPerActivation(boolean rskip560Active) {
        activationConfig = rskip560Active
                ? ActivationConfigsForTest.all()
                : ActivationConfigsForTest.allBut(ConsensusRule.RSKIP560);
        when(config.getActivationConfig()).thenReturn(activationConfig);
        mockExecutionBlockForRealVm();

        MutableRepository repository = createRepository();

        byte[] existingDelegation = DelegationCodeResolver.createDelegatedCode(createRandomAddress());
        repository.createAccount(authorityAddress);
        repository.setNonce(authorityAddress, ZERO_NONCE);
        repository.saveCode(authorityAddress, existingDelegation);

        repository.createAccount(sender);
        repository.addBalance(sender, Coin.valueOf(1_000_000L));
        repository.setNonce(sender, ZERO_NONCE);

        RskAddress plainContract = new RskAddress(HashUtil.calcNewAddr(receiver.getBytes(), BigInteger.ZERO.toByteArray()));
        repository.createAccount(plainContract);
        repository.saveCode(plainContract, Hex.decode("fe"));
        mockAddressAsNotAPrecompiled(plainContract);

        SetCodeAuthorization authorization =
                createValidAuthorizationTuple(delegatedAddress, ZERO_NONCE, constants.getChainId(), authorityKey);

        Transaction tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                200_000,
                1,
                1,
                plainContract,
                0,
                EMPTY_DATA,
                authorization
        );

        TransactionExecutor txExecutor = newExecutorWithRealVm(tx, repository);
        assertTrue(txExecutor.executeTransaction());

        assertNotNull(txExecutor.getResult().getException());
        assertArrayEquals(
                DelegationCodeResolver.createDelegatedCode(delegatedAddress),
                repository.getCode(authorityAddress),
                "authorization must persist regardless of activation -- it commits before call()/create() runs"
        );

        TransactionReceipt receipt = txExecutor.getReceipt();
        assertFalse(receipt.isSuccessful());

        BigInteger reportedGasUsed = new BigInteger(1, receipt.getGasUsed());
        Coin fullFee = Coin.valueOf(200_000L);
        long authorizationRefund = GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST; // 9_500

        if (rskip560Active) {
            Coin expectedFee = Coin.valueOf(200_000L - authorizationRefund);
            assertEquals(expectedFee, txExecutor.getPaidFees(), "post-activation, the authorization refund reduces the charged fee");
            assertEquals(BigInteger.valueOf(200_000L - authorizationRefund), reportedGasUsed, "post-activation, receipt.gasUsed matches what was actually charged");
            assertEquals(Coin.valueOf(reportedGasUsed.longValueExact()), txExecutor.getPaidFees(), "post-activation, paidFees and receipt.gasUsed must reconcile");
        } else {
            assertEquals(fullFee, txExecutor.getPaidFees(), "pre-activation, the authorization refund must be discarded -- legacy behavior charges the full gas limit");
            assertNotEquals(Coin.valueOf(reportedGasUsed.longValueExact()), txExecutor.getPaidFees(), "pre-activation, paidFees and receipt.gasUsed are expected to diverge -- reproducing the original bug");
        }
    }

    @Test
    void revertBehavesIdenticallyRegardlessOfActivation() {
        TxResult withRskip560 = runRevertScenario(true, false);
        TxResult withoutRskip560 = runRevertScenario(false, false);

        assertFalse(withRskip560.receiptSuccessful);
        assertFalse(withoutRskip560.receiptSuccessful);
        assertEquals(withoutRskip560.paidFees, withRskip560.paidFees, "REVERT fee accounting must be unaffected by RSKIP560");
        assertEquals(withoutRskip560.reportedGasUsed, withRskip560.reportedGasUsed, "REVERT receipt.gasUsed must be unaffected by RSKIP560");
        assertTrue(withRskip560.reportedGasUsed.compareTo(BigInteger.valueOf(200_000L)) < 0);
    }

    @Test
    void revertWithAuthorizationRefundBehavesIdenticallyRegardlessOfActivation() {
        TxResult withRskip560 = runRevertScenario(true, true);
        TxResult withoutRskip560 = runRevertScenario(false, true);

        assertFalse(withRskip560.receiptSuccessful);
        assertFalse(withoutRskip560.receiptSuccessful);
        assertEquals(withoutRskip560.paidFees, withRskip560.paidFees, "REVERT + authorization refund fee accounting must be unaffected by RSKIP560");
        assertEquals(withoutRskip560.reportedGasUsed, withRskip560.reportedGasUsed, "REVERT + authorization refund receipt.gasUsed must be unaffected by RSKIP560");
        assertTrue(withRskip560.reportedGasUsed.compareTo(BigInteger.valueOf(200_000L)) < 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void authorizationOnFailingPrecompileBehavesPerActivation(boolean rskip560Active) {
        activationConfig = rskip560Active ? ActivationConfigsForTest.all() : ActivationConfigsForTest.allBut(ConsensusRule.RSKIP560);
        when(config.getActivationConfig()).thenReturn(activationConfig);

        MutableRepository repository = createRepository();

        repository.createAccount(authorityAddress);
        repository.setNonce(authorityAddress, ONE_NONCE);
        repository.saveCode(authorityAddress, DelegationCodeResolver.createDelegatedCode(createRandomAddress()));

        fundSender(repository, ZERO_NONCE, 1_000_000);

        RskAddress fakeContractAddress = createRandomAddress();
        PrecompiledContracts.PrecompiledContract throwingPrecompile = createPrecompiledContract();
        when(precompiledContracts.getContractForAddress(any(), eq(DataWord.valueOf(fakeContractAddress.getBytes()))))
                .thenReturn(throwingPrecompile);

        SetCodeAuthorization authorization = createValidAuthorizationTuple(delegatedAddress, ONE_NONCE, constants.getChainId(), authorityKey);

        Transaction tx = createSignedType4Transaction(
                senderKey, constants.getChainId(), ZERO_NONCE, 100_000, 1, 1,
                fakeContractAddress, 0, EMPTY_DATA, authorization
        );

        TransactionExecutor txExecutor = newExecutor(tx, repository);
        assertTrue(txExecutor.executeTransaction());

        assertNotNull(txExecutor.getResult().getException());
        assertAuthorityDelegatedTo(repository, authorityAddress, delegatedAddress);

        TransactionReceipt receipt = txExecutor.getReceipt();
        BigInteger  reportedGasUsed = new BigInteger(1, receipt.getGasUsed());
        long authorizationRefund = GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST; // 9_500
        long expectedGasUsed = 100_000L - authorizationRefund; // 90_500

        if (rskip560Active) {
            assertEquals(authorizationRefund, txExecutor.getResult().getDeductedRefund(), "authorization refund should be fully applied (well under the half-of-gasUsed cap)");
            assertFalse(receipt.isSuccessful(), "post-activation, both gates fire: status must be FAILED");

            Coin expectedFee = Coin.valueOf(100_000L - authorizationRefund); // 90_500

            assertEquals(BigInteger.valueOf(expectedGasUsed), reportedGasUsed, "receipt.getGasUsed should be gasLimit minus the authorization refund");

            assertEquals(expectedFee, txExecutor.getPaidFees());  // effective gasPrice = 1
            assertEquals(Coin.valueOf(909_500L), repository.getBalance(sender), "post-activation, authorization refund must be returned to sender");
        } else {
            assertTrue(receipt.isSuccessful(), "pre-activation, legacy (buggy) SUCCESS status must be preserved even with an authorization present");
            assertEquals(Coin.valueOf(100_000L), txExecutor.getPaidFees(), "pre-activation, full gasLimit is still charged -- the authorization refund is discarded, same as legacy");
            assertTrue(reportedGasUsed.compareTo(BigInteger.valueOf(100_000L)) < 0);
            assertEquals(Coin.valueOf(900_000L), repository.getBalance(sender), "pre-activation, exception must refund nothing");
        }
    }

    private TxResult runRevertScenario(boolean rskip560Active, boolean withAuthorization) {
        activationConfig = rskip560Active
                ? ActivationConfigsForTest.all()
                : ActivationConfigsForTest.allBut(ConsensusRule.RSKIP560);
        when(config.getActivationConfig()).thenReturn(activationConfig);
        mockExecutionBlockForRealVm();

        MutableRepository repository = createRepository();

        if (withAuthorization) {
            byte[] existingDelegation = DelegationCodeResolver.createDelegatedCode(createRandomAddress());
            repository.createAccount(authorityAddress);
            repository.setNonce(authorityAddress, ZERO_NONCE);
            repository.saveCode(authorityAddress, existingDelegation);
        }

        fundSender(repository, ZERO_NONCE, 1_000_000);

        RskAddress revertingContract = new RskAddress(HashUtil.calcNewAddr(receiver.getBytes(), BigInteger.ZERO.toByteArray()));
        repository.createAccount(revertingContract);
        repository.saveCode(revertingContract, REVERT_CODE);
        mockAddressAsNotAPrecompiled(revertingContract);

        Transaction tx;
        if (withAuthorization) {
            SetCodeAuthorization authorization =
                    createValidAuthorizationTuple(delegatedAddress, ZERO_NONCE, constants.getChainId(), authorityKey);
            tx = createSignedType4Transaction(
                    senderKey, constants.getChainId(), ZERO_NONCE, 200_000, 1, 1,
                    revertingContract, 0, EMPTY_DATA, authorization
            );
        } else {
            // A type-4 tx requires at least one authorization -- an empty list is
            // invalid per EIP-7702. Use a plain (non-type-4) transaction instead
            // for the no-authorization case.
            tx = Transaction.builder()
                    .nonce(ZERO_NONCE)
                    .gasPrice(BigInteger.ONE)
                    .gasLimit(BigInteger.valueOf(200_000L))
                    .receiveAddress(revertingContract)
                    .chainId(constants.getChainId())
                    .value(Coin.ZERO)
                    .data(EMPTY_DATA)
                    .build();
            tx.sign(senderKey.getPrivKeyBytes());
        }

        TransactionExecutor txExecutor = newExecutorWithRealVm(tx, repository);
        assertTrue(txExecutor.executeTransaction());

        assertNull(txExecutor.getResult().getException(), "pure REVERT must not set an exception");
        assertTrue(txExecutor.getResult().isRevert());

        if (withAuthorization) {
            assertAuthorityDelegatedTo(repository, authorityAddress, delegatedAddress);
        }

        TransactionReceipt receipt = txExecutor.getReceipt();
        BigInteger reportedGasUsed = new BigInteger(1, receipt.getGasUsed());

        return new TxResult(receipt.isSuccessful(), txExecutor.getPaidFees(), reportedGasUsed);
    }

    @Test
    void failingPrecompileWithNullExceptionMessageProducesFailedReceipt() {
        activationConfig = ActivationConfigsForTest.all();
        when(config.getActivationConfig()).thenReturn(activationConfig);

        MutableRepository repository = createRepository();
        fundSender(repository, ZERO_NONCE, 1_000_000);

        RskAddress fakeContractAddress = createRandomAddress();

        PrecompiledContracts.PrecompiledContract throwingPrecompile =
                new PrecompiledContracts.PrecompiledContract() {
                    @Override
                    public long getGasForData(byte[] data) {
                        return FAKE_PRECOMPILE_REQUIRED_GAS;
                    }

                    @Override
                    public byte[] execute(byte[] data) {
                        throw new RuntimeException();
                    }
                };

        when(precompiledContracts.getContractForAddress(any(), eq(DataWord.valueOf(fakeContractAddress.getBytes())))).thenReturn(throwingPrecompile);

        SetCodeAuthorization authorization =
                createValidAuthorizationTuple(
                        delegatedAddress,
                        ZERO_NONCE,
                        constants.getChainId(),
                        authorityKey
                );

        Transaction tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                100_000,
                1,
                1,
                fakeContractAddress,
                0,
                EMPTY_DATA,
                authorization
        );

        TransactionExecutor txExecutor = newExecutor(tx, repository);

        assertTrue(txExecutor.executeTransaction());
        assertNotNull(txExecutor.getResult().getException());

        TransactionReceipt receipt = txExecutor.getReceipt();

        assertFalse(receipt.isSuccessful());
    }

    private static final class TxResult {
        final boolean receiptSuccessful;
        final Coin paidFees;
        final BigInteger reportedGasUsed;

        TxResult(boolean receiptSuccessful, Coin paidFees, BigInteger reportedGasUsed) {
            this.receiptSuccessful = receiptSuccessful;
            this.paidFees = paidFees;
            this.reportedGasUsed = reportedGasUsed;
        }
    }

    private void mockExecutionBlockForRealVm() {
        when(executionBlock.getParentHash()).thenReturn(Keccak256.ZERO_HASH);
        when(executionBlock.getCoinbase()).thenReturn(RskAddress.nullAddress());
        when(executionBlock.getTimestamp()).thenReturn(1L);
        when(executionBlock.getDifficulty()).thenReturn(new BlockDifficulty(BigInteger.ONE));
        when(executionBlock.getMinimumGasPrice()).thenReturn(Coin.ZERO);
    }

    private TransactionExecutor newExecutorWithRealVm(Transaction tx, Repository repository) {
        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        TransactionExecutorFactory factory = new TransactionExecutorFactory(
                config,
                blockStore,
                receiptStore,
                blockFactory,
                new ProgramInvokeFactoryImpl(), // real, not the base helper's mocked one
                precompiledContracts,
                signatureCache
        );
        return factory.newInstance(tx, txIndex, executionBlock.getCoinbase(), repository, executionBlock, 0L);
    }

    private static PrecompiledContracts.PrecompiledContract createPrecompiledContract() {
        return new PrecompiledContracts.PrecompiledContract() {
            @Override
            public long getGasForData(byte[] data) {
                return FAKE_PRECOMPILE_REQUIRED_GAS;
            }

            @Override
            public byte[] execute(byte[] data) throws VMException {
                throw new RuntimeException("boom - simulated precompile failure");
            }
        };
    }

    private void fundSender(MutableRepository repository, BigInteger nonce, long balance) {
        repository.createAccount(sender);
        repository.addBalance(sender, Coin.valueOf(balance));
        repository.setNonce(sender, nonce);
    }

    private void assertAuthorityDelegatedTo(MutableRepository repository, RskAddress authority, RskAddress delegatedTarget) {
        byte[] expected = DelegationCodeResolver.createDelegatedCode(delegatedTarget);
        assertArrayEquals(expected, repository.getCode(authority));
    }

    private MutableRepository createRepository() {
        co.rsk.trie.TrieStore trieStore = new co.rsk.trie.TrieStoreImpl(new org.ethereum.datasource.HashMapDB());
        co.rsk.db.MutableTrieImpl mutableTrie = new co.rsk.db.MutableTrieImpl(trieStore, new co.rsk.trie.Trie(trieStore));
        return new MutableRepository(mutableTrie);
    }
}
