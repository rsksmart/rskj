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
import org.junit.jupiter.params.provider.CsvSource;
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

    /**
     * A set-code transaction whose call ends in an EVM exceptional halt is charged its gas limit minus the capped
     * authorization refund, with or without RSKIP692, so the fee matches the receipt gasUsed.
     *
     * <p>Expected values, by hand: the authority already exists, so the authorization earns
     * PER_EMPTY_ACCOUNT_COST - PER_AUTH_BASE_COST = 25_000 - 15_500 = 9_500 (the RSKIP-692 case 9 figure).
     * The halt consumes the whole 200_000 gas limit, so the cap is 200_000 / 2 = 100_000 and the refund applies in full.
     * Charged gas = 200_000 - 9_500 = 190_500; with gasPrice 1 the fee is 190_500 and the sender keeps
     * 1_000_000 - 190_500 = 809_500.
     */
    @ParameterizedTest(name = "{0}, rskip692Active={2}")
    @CsvSource({
            "invalid opcode, fe, true",
            "invalid opcode, fe, false",
            "out of gas in a loop, 5b600056, true",
            "out of gas in a loop, 5b600056, false",
            "stack underflow, 01, true",
            "stack underflow, 01, false",
    })
    void authorizationRefundOnEvmExceptionalHaltIsChargedConsistently(String haltKind, String code, boolean rskip692Active) {
        activationConfig = rskip692Active
                ? ActivationConfigsForTest.all()
                : ActivationConfigsForTest.allBut(ConsensusRule.RSKIP692);
        when(config.getActivationConfig()).thenReturn(activationConfig);
        mockExecutionBlockForRealVm();

        MutableRepository repository = createRepository();

        byte[] existingDelegation = DelegationCodeResolver.createDelegatedCode(createRandomAddress());
        repository.createAccount(authorityAddress);
        repository.setNonce(authorityAddress, ZERO_NONCE);
        repository.saveCode(authorityAddress, existingDelegation);

        fundSender(repository, ZERO_NONCE, 1_000_000);

        RskAddress haltingContract = new RskAddress(HashUtil.calcNewAddr(receiver.getBytes(), BigInteger.ZERO.toByteArray()));
        repository.createAccount(haltingContract);
        repository.saveCode(haltingContract, Hex.decode(code));
        mockAddressAsNotAPrecompiled(haltingContract);

        SetCodeAuthorization authorization =
                createValidAuthorizationTuple(delegatedAddress, ZERO_NONCE, constants.getChainId(), authorityKey);

        Transaction tx = createSignedType4Transaction(
                senderKey, constants.getChainId(), ZERO_NONCE, 200_000, 1, 1,
                haltingContract, 0, EMPTY_DATA, authorization
        );

        TransactionExecutor txExecutor = newExecutorWithRealVm(tx, repository);
        assertTrue(txExecutor.executeTransaction());

        assertNotNull(txExecutor.getResult().getException(), haltKind + " must end in an exceptional halt");
        assertAuthorityDelegatedTo(repository, authorityAddress, delegatedAddress);

        TransactionReceipt receipt = txExecutor.getReceipt();
        assertFalse(receipt.isSuccessful());

        long expectedChargedGas = 190_500L; // 200_000 - 9_500
        assertEquals(BigInteger.valueOf(expectedChargedGas), new BigInteger(1, receipt.getGasUsed()));
        assertEquals(Coin.valueOf(expectedChargedGas), txExecutor.getPaidFees(), "fee must equal receipt gasUsed * gasPrice");
        assertEquals(Coin.valueOf(809_500L), repository.getBalance(sender), "the authorization refund must be returned to the sender");
    }

    /**
     * A legacy transaction (no authorization list) ending in an EVM exceptional halt is charged its full gas limit,
     * whatever the activation state. The contract clears a storage slot and calls a child that self-destructs before
     * halting, so every refund source other than the authorization refund is exercised and must be discarded.
     *
     * <p>Expected values, by hand: gas limit 200_000 and gasPrice 1, so fee = receipt gasUsed = 200_000 and the
     * sender keeps 1_000_000 - 200_000 = 800_000. The control run ends with STOP instead of INVALID and must be
     * charged less than the limit, which shows the refund sources are live.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"all", "allButRskip692", "allButRskip545AndRskip692"})
    void legacyEvmExceptionalHaltChargesFullGasLimit(String activations) {
        activationConfig = switch (activations) {
            case "all" -> ActivationConfigsForTest.all();
            case "allButRskip692" -> ActivationConfigsForTest.allBut(ConsensusRule.RSKIP692);
            default -> ActivationConfigsForTest.allBut(ConsensusRule.RSKIP545, ConsensusRule.RSKIP692);
        };
        when(config.getActivationConfig()).thenReturn(activationConfig);
        mockExecutionBlockForRealVm();

        LegacyRun halted = runLegacyRefundSourcesScenario("fe");
        assertNotNull(halted.executor.getResult().getException());
        assertFalse(halted.executor.getReceipt().isSuccessful());
        assertEquals(BigInteger.valueOf(200_000L), new BigInteger(1, halted.executor.getReceipt().getGasUsed()));
        assertEquals(Coin.valueOf(200_000L), halted.executor.getPaidFees());
        assertEquals(Coin.valueOf(800_000L), halted.repository.getBalance(sender));

        LegacyRun control = runLegacyRefundSourcesScenario("00");
        assertNull(control.executor.getResult().getException());
        assertTrue(control.executor.getResult().getDeductedRefund() > 0, "control run must earn a refund");
    }

    @Test
    void revertBehavesIdenticallyRegardlessOfActivation() {
        TxResult withRskip692 = runRevertScenario(true, false);
        TxResult withoutRskip692 = runRevertScenario(false, false);

        assertFalse(withRskip692.receiptSuccessful);
        assertFalse(withoutRskip692.receiptSuccessful);
        assertEquals(withoutRskip692.paidFees, withRskip692.paidFees, "REVERT fee accounting must be unaffected by RSKIP692");
        assertEquals(withoutRskip692.reportedGasUsed, withRskip692.reportedGasUsed, "REVERT receipt.gasUsed must be unaffected by RSKIP692");
        assertTrue(withRskip692.reportedGasUsed.compareTo(BigInteger.valueOf(200_000L)) < 0);
    }

    @Test
    void revertWithAuthorizationRefundBehavesIdenticallyRegardlessOfActivation() {
        TxResult withRskip692 = runRevertScenario(true, true);
        TxResult withoutRskip692 = runRevertScenario(false, true);

        assertFalse(withRskip692.receiptSuccessful);
        assertFalse(withoutRskip692.receiptSuccessful);
        assertEquals(withoutRskip692.paidFees, withRskip692.paidFees, "REVERT + authorization refund fee accounting must be unaffected by RSKIP692");
        assertEquals(withoutRskip692.reportedGasUsed, withRskip692.reportedGasUsed, "REVERT + authorization refund receipt.gasUsed must be unaffected by RSKIP692");
        assertTrue(withRskip692.reportedGasUsed.compareTo(BigInteger.valueOf(200_000L)) < 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void authorizationOnFailingPrecompileBehavesPerActivation(boolean rskip692Active) {
        activationConfig = rskip692Active ? ActivationConfigsForTest.all() : ActivationConfigsForTest.allBut(ConsensusRule.RSKIP692);
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

        if (rskip692Active) {
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void authorizationOnFailingPrecompileAppliesGasPriceMultiplier(boolean rskip692Active) {
        activationConfig = rskip692Active ? ActivationConfigsForTest.all() : ActivationConfigsForTest.allBut(ConsensusRule.RSKIP692);
        when(config.getActivationConfig()).thenReturn(activationConfig);

        long gasPrice = 3L;

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
                senderKey, constants.getChainId(), ZERO_NONCE, 100_000, gasPrice, gasPrice,
                fakeContractAddress, 0, EMPTY_DATA, authorization
        );

        TransactionExecutor txExecutor = newExecutor(tx, repository);
        assertTrue(txExecutor.executeTransaction());

        assertNotNull(txExecutor.getResult().getException());
        assertAuthorityDelegatedTo(repository, authorityAddress, delegatedAddress);

        long authorizationRefund = GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST; // 9_500

        if (rskip692Active) {
            long expectedChargedGas = 100_000L - authorizationRefund; // 90_500
            Coin expectedFee = Coin.valueOf(expectedChargedGas * gasPrice); // 271_500
            Coin expectedSenderBalance = Coin.valueOf(1_000_000L - expectedChargedGas * gasPrice); // 728_500

            assertEquals(expectedFee, txExecutor.getPaidFees(), "paidFees must be chargedGas * gasPrice, not just chargedGas");
            assertEquals(expectedSenderBalance, repository.getBalance(sender), "sender balance must reflect the refund scaled by gasPrice");
        } else {
            Coin expectedFee = Coin.valueOf(100_000L * gasPrice); // 300_000
            Coin expectedSenderBalance = Coin.valueOf(1_000_000L - 100_000L * gasPrice); // 700_000

            assertEquals(expectedFee, txExecutor.getPaidFees(), "pre-activation, the full gasLimit fee must still be scaled by gasPrice");
            assertEquals(expectedSenderBalance, repository.getBalance(sender), "pre-activation, no refund is paid, but the prepaid cost must reflect gasPrice");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void localCallOnFailingPrecompileDoesNotInflateGasEstimateToLimit(boolean rskip692Active) {
        activationConfig = rskip692Active ? ActivationConfigsForTest.all() : ActivationConfigsForTest.allBut(ConsensusRule.RSKIP692);
        when(config.getActivationConfig()).thenReturn(activationConfig);

        MutableRepository repository = createRepository();

        repository.createAccount(authorityAddress);
        repository.setNonce(authorityAddress, ONE_NONCE);
        repository.saveCode(authorityAddress, DelegationCodeResolver.createDelegatedCode(createRandomAddress()));

        fundSender(repository, ZERO_NONCE, 1_000_000);

        RskAddress fakeContractAddress = createRandomAddress();
        PrecompiledContracts.PrecompiledContract throwingPrecompile = createPrecompiledContract();
        when(precompiledContracts.getContractForAddress(any(), eq(DataWord.valueOf(fakeContractAddress.getBytes())))).thenReturn(throwingPrecompile);

        SetCodeAuthorization authorization = createValidAuthorizationTuple(delegatedAddress, ONE_NONCE, constants.getChainId(), authorityKey);

        long gasEstimationCapStandIn = 6_800_000L;
        Transaction tx = createSignedType4Transaction(
                senderKey, constants.getChainId(), ZERO_NONCE, gasEstimationCapStandIn, 1, 1,
                fakeContractAddress, 0, EMPTY_DATA, authorization
        );

        TransactionExecutor txExecutor = newExecutor(tx, repository).setLocalCall(true);
        assertTrue(txExecutor.executeTransaction());

        assertNotNull(txExecutor.getResult().getException());

        long maxGasUsed = txExecutor.getResult().getMaxGasUsed();
        assertTrue(maxGasUsed < gasEstimationCapStandIn / 2, "a local call against a throwing precompile must not report gasUsed inflated toward the full gas limit; got " + maxGasUsed);
        assertNotEquals(gasEstimationCapStandIn, maxGasUsed, "gasUsed must reflect the precompile's declared cost, not txGasLimit, regardless of RSKIP692 activation");
    }

    private TxResult runRevertScenario(boolean rskip692Active, boolean withAuthorization) {
        activationConfig = rskip692Active
                ? ActivationConfigsForTest.all()
                : ActivationConfigsForTest.allBut(ConsensusRule.RSKIP692);
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

    private LegacyRun runLegacyRefundSourcesScenario(String terminalOpcode) {
        MutableRepository repository = createRepository();
        fundSender(repository, ZERO_NONCE, 1_000_000);

        // CALLER SELFDESTRUCT
        RskAddress selfDestructingChild = createRandomAddress();
        repository.createAccount(selfDestructingChild);
        repository.saveCode(selfDestructingChild, Hex.decode("33ff"));

        // SSTORE(0, 0) over a non-zero slot, CALL(gas, child, 0, 0, 0, 0, 0), POP, then the terminal opcode
        RskAddress parent = new RskAddress(HashUtil.calcNewAddr(receiver.getBytes(), BigInteger.ZERO.toByteArray()));
        repository.createAccount(parent);
        repository.saveCode(parent, Hex.decode("600060005560006000600060006000"
                + "73" + Hex.toHexString(selfDestructingChild.getBytes()) + "5af150" + terminalOpcode));
        repository.addStorageRow(parent, DataWord.ZERO, DataWord.ONE);
        mockAddressAsNotAPrecompiled(parent);
        mockAddressAsNotAPrecompiled(selfDestructingChild);

        Transaction tx = Transaction.builder()
                .nonce(ZERO_NONCE)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(200_000L))
                .receiveAddress(parent)
                .chainId(constants.getChainId())
                .value(Coin.ZERO)
                .data(EMPTY_DATA)
                .build();
        tx.sign(senderKey.getPrivKeyBytes());

        TransactionExecutor txExecutor = newExecutorWithRealVm(tx, repository);
        assertTrue(txExecutor.executeTransaction());
        return new LegacyRun(txExecutor, repository);
    }

    private static final class LegacyRun {
        final TransactionExecutor executor;
        final MutableRepository repository;

        LegacyRun(TransactionExecutor executor, MutableRepository repository) {
            this.executor = executor;
            this.repository = repository;
        }
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
