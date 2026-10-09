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

package org.ethereum.core;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.core.bc.transactionexecutor.helper.Type4TransactionExecutorHelperTest;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Set;

import static co.rsk.db.ClearedAccountAssertions.assertClearedAccount;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * RSKIP701 test cases, executed through the real VM on a real repository.
 * The contract {@code D} stores 42 in slot 0 and then executes SELFDESTRUCT with the beneficiary
 * taken from the call data.
 */
class SelfdestructPreservesNonceTest extends Type4TransactionExecutorHelperTest {

    // PUSH1 42 PUSH1 0 SSTORE PUSH1 0 CALLDATALOAD SELFDESTRUCT
    private static final byte[] CODE_D = Hex.decode("602a600055600035ff");

    // PUSH9 <CODE_D> PUSH1 0 MSTORE PUSH1 9 PUSH1 23 RETURN: init code whose runtime is CODE_D
    private static final byte[] INIT_CODE_D = ByteUtil.merge(Hex.decode("68"), CODE_D, Hex.decode("60005260096017f3"));
    private static final byte[] CREATE2_SALT = DataWord.valueOf(1).getData();
    /**
     * Factory F: CREATE2 with INIT_CODE_D and salt 1, then CALL the new contract forwarding the call data.
     * PUSH18 <INIT_CODE_D> PUSH1 0 MSTORE | CALLDATACOPY(32, 0, 32) | CREATE2(0, 14, 18, 1) |
     * CALL(GAS, addr, 0, 32, 32, 0, 0) | POP STOP
     */
    private static final byte[] CODE_FACTORY_F = ByteUtil.merge(
            Hex.decode("71"), INIT_CODE_D, Hex.decode("600052"
                    + "602060006020" + "37"
                    + "6001601260" + "0e" + "6000" + "f5"
                    + "60006000602060206000" + "85" + "5a" + "f1"
                    + "5000"));

    private static final RskAddress CONTRACT_C = new RskAddress("7c0d52faab596c08f484e3478aebc6205f3f5d8c");
    private static final RskAddress FACTORY_F = new RskAddress("f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff00010203");
    private static final RskAddress CONTRACT_D = new RskAddress("d0d1d2d3d4d5d6d7d8d9dadbdcdddedf00010203");
    private static final RskAddress CONTRACT_E = new RskAddress("e0e1e2e3e4e5e6e7e8e9eaebecedeeef00010203");
    private static final RskAddress CONTRACT_P = new RskAddress("909192939495969798999a9b9c9d9e9f00010203");
    private static final RskAddress CONTRACT_X = new RskAddress("a0a1a2a3a4a5a6a7a8a9aaabacadaeaf00010203");
    private static final RskAddress CONTRACT_Y = new RskAddress("c0c1c2c3c4c5c6c7c8c9cacbcccdcecf00010203");
    private static final RskAddress SECOND_DELEGATE = new RskAddress("5051525354555657585960616263646566676869");
    private static final RskAddress CONTRACT_G = new RskAddress("606162636465666768696a6b6c6d6e6f00010203");
    // CREATE(0, 0, 0) POP PUSH1 0 CALLDATALOAD SELFDESTRUCT: the CREATE raises the nonce before the mark
    private static final byte[] CODE_G = Hex.decode("600060006000f050600035ff");
    private static final long BALANCE_A = 777;
    private static final long BALANCE_Y = 50;
    private static final RskAddress BENEFICIARY_B = new RskAddress("b1c7a1f0e4d3c2b1a0f9e8d7c6b5a4f3e2d1c0b9");
    private static final long BALANCE_C = 1000;
    private static final long BALANCE_B = 5;
    private static final long SENDER_BALANCE = 1_000_000;
    private static final long GAS_LIMIT = 100_000;
    private static final long NESTED_CREATION_GAS_LIMIT = 500_000;

    @Test
    void contractWithNonZeroNonceIsClearedAndKeepsItsNonce() {
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_C);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        Transaction tx = signedCall(CONTRACT_C, beneficiaryWord(BENEFICIARY_B));
        TransactionExecutor executor = execute(repository, tx);

        assertClearedAccount(repository, CONTRACT_C, BigInteger.ONE);
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(CONTRACT_C.getBytes())),
                "A cleared account is still recorded as deleted for the same-block creation check");
    }

    // -------------------------------------------------------------------------
    // SELFDESTRUCT in a delegated account
    // -------------------------------------------------------------------------

    @Test
    void delegatedAccountOnlyMovesItsBalance() {
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_D, BigInteger.ONE, CODE_D, 0);
        createAccountWithBalance(repository, authorityAddress, BALANCE_A);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        installDelegation(repository, authorityKey, BigInteger.ZERO, CONTRACT_D, BigInteger.ZERO);
        assertTrue(repository.isActiveDelegatedEOA(authorityAddress));
        assertEquals(Coin.valueOf(BALANCE_A), repository.getBalance(authorityAddress));

        Transaction tx = signedCall(BigInteger.ONE, authorityAddress, beneficiaryWord(BENEFICIARY_B));
        TransactionExecutor executor = execute(repository, tx);

        assertEquals(BigInteger.ONE, repository.getNonce(authorityAddress));
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(CONTRACT_D), repository.getCode(authorityAddress));
        assertEquals(DataWord.valueOf(42), repository.getStorageValue(authorityAddress, DataWord.ZERO));
        assertEquals(Coin.ZERO, repository.getBalance(authorityAddress));
        assertTrue(repository.hasDelegationAuthority(authorityAddress));
        assertTrue(repository.hasInitializedStorage(authorityAddress));
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_A), repository.getBalance(BENEFICIARY_B));
        assertFalse(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(authorityAddress.getBytes())),
                "A delegated account is never marked");
    }

    @Test
    void consumedAuthorizationStaysSkippedAfterSelfdestruct() {
        MutableRepository repository = replayConsumedAuthorizationAfterSelfdestruct();

        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_A), repository.getBalance(BENEFICIARY_B),
                "The second delegate must have executed SELFDESTRUCT");
        // The authorization for D carries nonce 0, the authority has nonce 2, so it is skipped
        assertEquals(BigInteger.valueOf(2), repository.getNonce(authorityAddress));
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(SECOND_DELEGATE),
                repository.getCode(authorityAddress));
    }

    @Test
    void beforeActivationConsumedAuthorizationIsAppliedAgainAfterSelfdestruct() {
        activateAllBut(ConsensusRule.RSKIP701);

        MutableRepository repository = replayConsumedAuthorizationAfterSelfdestruct();

        // The authority was deleted with nonce 0, so the authorization for D applies again
        assertEquals(BigInteger.ONE, repository.getNonce(authorityAddress));
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(CONTRACT_D),
                repository.getCode(authorityAddress));
    }

    /**
     * The authority delegates to D with nonce 0, then to a second delegate with nonce 1. The second delegate
     * executes SELFDESTRUCT, and a set-code transaction then carries the authorization for D again.
     */
    private MutableRepository replayConsumedAuthorizationAfterSelfdestruct() {
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_D, BigInteger.ONE, CODE_D, 0);
        installContract(repository, SECOND_DELEGATE, BigInteger.ONE, codeDestructingTo(BENEFICIARY_B), 0);
        createAccountWithBalance(repository, authorityAddress, BALANCE_A);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        installDelegation(repository, authorityKey, BigInteger.ZERO, CONTRACT_D, BigInteger.ZERO);
        installDelegation(repository, authorityKey, BigInteger.ONE, SECOND_DELEGATE, BigInteger.ONE);
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(SECOND_DELEGATE),
                repository.getCode(authorityAddress));
        execute(repository, signedCall(BigInteger.valueOf(2), authorityAddress, EMPTY_DATA));

        installDelegation(repository, authorityKey, BigInteger.ZERO, CONTRACT_D, BigInteger.valueOf(3));
        return repository;
    }

    @Test
    void delegatedAccountNamingItselfKeepsItsBalance() {
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_D, BigInteger.ONE, CODE_D, 0);
        createAccountWithBalance(repository, authorityAddress, BALANCE_A);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        installDelegation(repository, authorityKey, BigInteger.ZERO, CONTRACT_D, BigInteger.ZERO);

        TransactionExecutor executor = execute(repository,
                signedCall(BigInteger.ONE, authorityAddress, beneficiaryWord(authorityAddress)));

        assertEquals(Coin.valueOf(BALANCE_A), repository.getBalance(authorityAddress));
        assertEquals(BigInteger.ONE, repository.getNonce(authorityAddress));
        assertEquals(DataWord.valueOf(42), repository.getStorageValue(authorityAddress, DataWord.ZERO));
        assertFalse(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(authorityAddress.getBytes())));
    }

    @Test
    void delegatedAccountReachedThroughDelegatecallOnlyMovesItsBalance() {
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_D, BigInteger.ONE, CODE_D, 0);
        installContract(repository, CONTRACT_E, BigInteger.ONE, codeForwardingByDelegatecallTo(CONTRACT_D), 0);
        createAccountWithBalance(repository, authorityAddress, BALANCE_A);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        installDelegation(repository, authorityKey, BigInteger.ZERO, CONTRACT_E, BigInteger.ZERO);

        TransactionExecutor executor = execute(repository,
                signedCall(BigInteger.ONE, authorityAddress, beneficiaryWord(BENEFICIARY_B)));

        assertEquals(BigInteger.ONE, repository.getNonce(authorityAddress));
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(CONTRACT_E), repository.getCode(authorityAddress));
        assertEquals(DataWord.valueOf(42), repository.getStorageValue(authorityAddress, DataWord.ZERO));
        assertEquals(Coin.ZERO, repository.getBalance(authorityAddress));
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_A), repository.getBalance(BENEFICIARY_B));
        assertFalse(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(authorityAddress.getBytes())));
        assertTrue(repository.isExist(CONTRACT_D));
        assertEquals(CODE_D.length, repository.getCodeLength(CONTRACT_D));
    }

    @Test
    void setCodeTransactionFromAnotherSenderInstallsDelegationAndOnlyMovesTheBalance() {
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_D, BigInteger.ONE, CODE_D, 0);
        createAccountWithBalance(repository, authorityAddress, BALANCE_A);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        SetCodeAuthorization authorization = createValidAuthorizationTuple(
                CONTRACT_D, BigInteger.ZERO, constants.getChainId(), authorityKey);
        Transaction tx = createSignedType4Transaction(
                senderKey, constants.getChainId(), BigInteger.ZERO, 600_000, 1, 1,
                authorityAddress, 0, beneficiaryWord(BENEFICIARY_B), authorization);

        TransactionExecutor executor = execute(repository, tx);

        assertEquals(BigInteger.ONE, repository.getNonce(authorityAddress));
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(CONTRACT_D), repository.getCode(authorityAddress));
        assertEquals(DataWord.valueOf(42), repository.getStorageValue(authorityAddress, DataWord.ZERO));
        assertEquals(Coin.ZERO, repository.getBalance(authorityAddress));
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_A), repository.getBalance(BENEFICIARY_B));
        assertFalse(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(authorityAddress.getBytes())));
    }

    @Test
    void delegatedAccountEarnsNoRefundWhileAContractEarnsTheCappedRefund() {
        MutableRepository delegatedArm = createRepository();
        installContract(delegatedArm, CONTRACT_D, BigInteger.ONE, CODE_D, 0);
        createAccountWithBalance(delegatedArm, authorityAddress, BALANCE_A);
        createAccountWithBalance(delegatedArm, BENEFICIARY_B, BALANCE_B);
        fundSender(delegatedArm, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        installDelegation(delegatedArm, authorityKey, BigInteger.ZERO, CONTRACT_D, BigInteger.ZERO);

        MutableRepository contractArm = createRepository();
        installContract(contractArm, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_A);
        createAccountWithBalance(contractArm, BENEFICIARY_B, BALANCE_B);
        fundSender(contractArm, SENDER_BALANCE);

        TransactionExecutor delegated = execute(delegatedArm,
                signedCall(BigInteger.ONE, authorityAddress, beneficiaryWord(BENEFICIARY_B)));
        TransactionExecutor contract = execute(contractArm,
                signedCall(BigInteger.ZERO, CONTRACT_C, beneficiaryWord(BENEFICIARY_B)));

        long gasBeforeRefunds = contract.getResult().getGasUsedBeforeRefunds();
        assertEquals(gasBeforeRefunds, delegated.getResult().getGasUsedBeforeRefunds(),
                "SELFDESTRUCT charges the same gas in a delegated account and in a contract");
        assertEquals(Math.min(GasCost.SUICIDE_REFUND, gasBeforeRefunds / 2), contract.getResult().getDeductedRefund());
        assertEquals(0, delegated.getResult().getDeductedRefund());
    }

    @Test
    void balanceReceivedAfterMarkingIsRemovedWithTheRest() {
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_C);
        installContract(repository, CONTRACT_Y, BigInteger.ZERO, codeDestructingTo(CONTRACT_C), BALANCE_Y);
        installContract(repository, CONTRACT_X, BigInteger.ONE, codeCallingThenCalling(CONTRACT_C, CONTRACT_Y), 0);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        TransactionExecutor executor = execute(repository, signedCall(CONTRACT_X, beneficiaryWord(BENEFICIARY_B)));

        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(CONTRACT_Y.getBytes())));
        assertClearedAccount(repository, CONTRACT_C, BigInteger.ONE);
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
        assertFalse(repository.isExist(CONTRACT_Y));
    }

    @Test
    void contractRaisingItsNonceBeforeSelfdestructIsClearedWithTheRaisedNonce() {
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_G, BigInteger.ZERO, CODE_G, BALANCE_C);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        RskAddress child = new RskAddress(HashUtil.calcNewAddr(CONTRACT_G.getBytes(), BigInteger.ZERO.toByteArray()));

        TransactionExecutor executor = execute(repository, signedCall(CONTRACT_G, beneficiaryWord(BENEFICIARY_B)));

        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(CONTRACT_G.getBytes())));
        assertClearedAccount(repository, CONTRACT_G, BigInteger.ONE);
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
        assertTrue(repository.isExist(child));
        assertEquals(BigInteger.ONE, repository.getNonce(child));
    }

    @Test
    void beforeActivationContractRaisingItsNonceBeforeSelfdestructIsDeleted() {
        activateAllBut(ConsensusRule.RSKIP701);
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_G, BigInteger.ZERO, CODE_G, BALANCE_C);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        execute(repository, signedCall(CONTRACT_G, beneficiaryWord(BENEFICIARY_B)));

        assertFalse(repository.isExist(CONTRACT_G));
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
    }

    @Test
    void beforeActivationBalanceReceivedAfterMarkingIsRemovedWithTheAccount() {
        activateAllBut(ConsensusRule.RSKIP701);
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_C);
        installContract(repository, CONTRACT_Y, BigInteger.ZERO, codeDestructingTo(CONTRACT_C), BALANCE_Y);
        installContract(repository, CONTRACT_X, BigInteger.ONE, codeCallingThenCalling(CONTRACT_C, CONTRACT_Y), 0);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        execute(repository, signedCall(CONTRACT_X, beneficiaryWord(BENEFICIARY_B)));

        assertFalse(repository.isExist(CONTRACT_C));
        assertFalse(repository.isExist(CONTRACT_Y));
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
    }

    @Test
    void contractWithDelegationIndicatorCodeAndNoFlagIsCleared() {
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_D, BigInteger.ONE, CODE_D, 0);
        installContract(repository, CONTRACT_P, BigInteger.ONE, DelegationCodeResolver.createDelegatedCode(CONTRACT_D), BALANCE_C);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        assertFalse(repository.hasDelegationAuthority(CONTRACT_P));

        TransactionExecutor executor = execute(repository, signedCall(CONTRACT_P, beneficiaryWord(BENEFICIARY_B)));

        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(CONTRACT_P.getBytes())),
                "The code of D runs in the context of P and marks P");
        assertClearedAccount(repository, CONTRACT_P, BigInteger.ONE);
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
    }

    // -------------------------------------------------------------------------
    // Marked accounts at the end of the transaction
    // -------------------------------------------------------------------------

    @Test
    void contractWithZeroNonceIsDeleted() {
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ZERO, CODE_D, BALANCE_C);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        Transaction tx = signedCall(CONTRACT_C, beneficiaryWord(BENEFICIARY_B));
        TransactionExecutor executor = execute(repository, tx);

        assertFalse(repository.isExist(CONTRACT_C));
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(CONTRACT_C.getBytes())));
    }

    @Test
    void contractCreatedByCreate2AndDestroyedInTheSameTransactionIsDeleted() {
        MutableRepository repository = createRepository();
        installContract(repository, FACTORY_F, BigInteger.ONE, CODE_FACTORY_F, 0);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        RskAddress created = new RskAddress(HashUtil.calcSaltAddr(FACTORY_F, Bytes.of(INIT_CODE_D), CREATE2_SALT));

        Transaction tx = signedCall(FACTORY_F, beneficiaryWord(BENEFICIARY_B));
        TransactionExecutor executor = execute(repository, tx);

        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(created.getBytes())),
                "The created contract must have executed SELFDESTRUCT");
        assertFalse(repository.isExist(created));
        assertEquals(Coin.valueOf(BALANCE_B), repository.getBalance(BENEFICIARY_B));
    }

    @Test
    void contractCreatedInANestedFrameAndDestroyedInTheSameTransactionIsDeleted() {
        RskAddress caller = new RskAddress("8081828384858687888990919293949596979899");
        // CALLDATACOPY(0, 0, CALLDATASIZE) CALL(GAS, F, 0, 0, CALLDATASIZE, 0, 0) POP STOP: F runs in a nested frame
        byte[] codeCallingFactory = ByteUtil.merge(
                Hex.decode("366000600037" + "60006000366000600073"), FACTORY_F.getBytes(), Hex.decode("5af15000"));
        MutableRepository repository = createRepository();
        installContract(repository, caller, BigInteger.ONE, codeCallingFactory, 0);
        installContract(repository, FACTORY_F, BigInteger.ONE, CODE_FACTORY_F, 0);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        RskAddress created = new RskAddress(HashUtil.calcSaltAddr(FACTORY_F, Bytes.of(INIT_CODE_D), CREATE2_SALT));

        Transaction tx = signedCall(caller, beneficiaryWord(BENEFICIARY_B));
        TransactionExecutor executor = execute(repository, tx);

        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(created.getBytes())),
                "The created contract must have executed SELFDESTRUCT");
        assertFalse(repository.isExist(created), "A contract created in a nested frame is still created in this transaction");
        assertEquals(Coin.valueOf(BALANCE_B), repository.getBalance(BENEFICIARY_B));
    }

    @Test
    void contractCreatedByTransactionWhoseInitCodeCreatesAndDestroysIsDeleted() {
        MutableRepository repository = createRepository();
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        // CREATE(0, 0, 0) POP PUSH20 <B> SELFDESTRUCT: the CREATE raises the nonce to 1 before the mark
        byte[] initCode = ByteUtil.merge(Hex.decode("600060006000f050" + "73"), BENEFICIARY_B.getBytes(), Hex.decode("ff"));

        Transaction tx = signedCreation(initCode);
        RskAddress created = tx.getContractAddress();
        TransactionExecutor executor = execute(repository, tx);

        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(created.getBytes())));
        assertFalse(repository.isExist(created));
        assertEquals(Coin.valueOf(BALANCE_B), repository.getBalance(BENEFICIARY_B));
    }

    @Test
    void contractCreatedTwoCreationLevelsDeepAndDestroyedInTheSameTransactionIsDeleted() {
        MutableRepository repository = createRepository();
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        // Grandchild init code: PUSH20 <B> SELFDESTRUCT
        byte[] grandchildInitCode = ByteUtil.merge(Hex.decode("73"), BENEFICIARY_B.getBytes(), Hex.decode("ff"));
        // Child init code: PUSH22 <grandchild init code> PUSH1 0 MSTORE CREATE(0, 10, 22) POP STOP
        byte[] childInitCode = ByteUtil.merge(Hex.decode("75"), grandchildInitCode, Hex.decode("600052" + "6016600a6000f0" + "5000"));
        // CODECOPY(0, 16, 35) CREATE(0, 0, 35) POP STOP, followed by the 35 bytes of the child init code
        byte[] initCode = ByteUtil.merge(Hex.decode("602360106000" + "39" + "602360006000f0" + "5000"), childInitCode);

        Transaction tx = signedCreation(initCode, NESTED_CREATION_GAS_LIMIT);
        TransactionExecutor executor = execute(repository, tx);

        Set<DataWord> marked = executor.getResult().getDeleteAccounts();
        assertEquals(1, marked.size(), "Only the grandchild executes SELFDESTRUCT");
        RskAddress grandchild = new RskAddress(marked.iterator().next().getLast20Bytes());
        assertFalse(repository.isExist(grandchild), "A contract created by a nested CREATE is still created in this transaction");
        assertEquals(Coin.valueOf(BALANCE_B), repository.getBalance(BENEFICIARY_B));
    }

    @Test
    void beforeActivationContractWithNonZeroNonceIsDeleted() {
        activateAllBut(ConsensusRule.RSKIP701);
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_C);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        Transaction tx = signedCall(CONTRACT_C, beneficiaryWord(BENEFICIARY_B));
        execute(repository, tx);

        assertFalse(repository.isExist(CONTRACT_C));
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private void activateAllBut(ConsensusRule... disabled) {
        activationConfig = ActivationConfigsForTest.allBut(disabled);
        when(config.getActivationConfig()).thenReturn(activationConfig);
    }

    private static MutableRepository createRepository() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        return new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));
    }

    private static void installContract(MutableRepository repository, RskAddress address, BigInteger nonce, byte[] code, long balance) {
        repository.createAccount(address);
        repository.setNonce(address, nonce);
        repository.addBalance(address, Coin.valueOf(balance));
        repository.initializeStorage(address);
        repository.saveCode(address, code);
    }

    private static void createAccountWithBalance(MutableRepository repository, RskAddress address, long balance) {
        repository.createAccount(address);
        repository.addBalance(address, Coin.valueOf(balance));
    }

    private void fundSender(MutableRepository repository, long balance) {
        repository.createAccount(sender);
        repository.addBalance(sender, Coin.valueOf(balance));
    }

    /** CALLDATACOPY(0, 0, CALLDATASIZE) DELEGATECALL(GAS, target, 0, CALLDATASIZE, 0, 0) POP STOP */
    private static byte[] codeForwardingByDelegatecallTo(RskAddress target) {
        return ByteUtil.merge(Hex.decode("36600060003760006000366000" + "73"), target.getBytes(), Hex.decode("5af45000"));
    }

    /** PUSH20 beneficiary SELFDESTRUCT */
    private static byte[] codeDestructingTo(RskAddress beneficiary) {
        return ByteUtil.merge(Hex.decode("73"), beneficiary.getBytes(), Hex.decode("ff"));
    }

    /**
     * CALLDATACOPY(0, 0, 32) CALL(GAS, first, 0, 0, 32, 0, 0) POP CALL(GAS, second, 0, 0, 0, 0, 0) POP STOP:
     * the first call forwards the call data, the second call carries nothing.
     */
    private static byte[] codeCallingThenCalling(RskAddress first, RskAddress second) {
        return ByteUtil.merge(
                Hex.decode("602060006000" + "37" + "60006000602060006000" + "73"), first.getBytes(),
                Hex.decode("5af150" + "60006000600060006000" + "73"), second.getBytes(),
                Hex.decode("5af15000"));
    }

    private TransactionExecutor execute(MutableRepository repository, Transaction tx) {
        TransactionExecutor executor = newRealVmExecutor(tx, repository);
        assertTrue(executor.executeTransaction());
        assertNull(executor.getResult().getException());
        return executor;
    }

    /** The beneficiary as the 32-byte word that CALLDATALOAD reads: the address right-aligned. */
    private static byte[] beneficiaryWord(RskAddress beneficiary) {
        return DataWord.valueOf(beneficiary.getBytes()).getData();
    }

    private Transaction signedCreation(byte[] initCode) {
        return signedCreation(initCode, GAS_LIMIT);
    }

    private Transaction signedCreation(byte[] initCode, long gasLimit) {
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(Coin.valueOf(1))
                .gasLimit(BigInteger.valueOf(gasLimit))
                .value(Coin.ZERO)
                .data(initCode)
                .chainId(constants.getChainId())
                .build();
        tx.sign(senderKey.getPrivKeyBytes());
        return tx;
    }

    private Transaction signedCall(RskAddress to, byte[] data) {
        return signedCall(BigInteger.ZERO, to, data);
    }

    private Transaction signedCall(BigInteger senderNonce, RskAddress to, byte[] data) {
        Transaction tx = Transaction.builder()
                .nonce(senderNonce)
                .gasPrice(Coin.valueOf(1))
                .gasLimit(BigInteger.valueOf(GAS_LIMIT))
                .receiveAddress(to)
                .value(Coin.ZERO)
                .data(data)
                .chainId(constants.getChainId())
                .build();
        tx.sign(senderKey.getPrivKeyBytes());
        return tx;
    }

    /** Runs a set-code transaction from the sender carrying one authorization of the authority for the delegate. */
    private void installDelegation(MutableRepository repository, ECKey authority, BigInteger authorityNonce,
                                   RskAddress delegate, BigInteger senderNonce) {
        SetCodeAuthorization authorization = createValidAuthorizationTuple(
                delegate, authorityNonce, constants.getChainId(), authority);
        Transaction setCodeTx = createSignedType4Transaction(
                senderKey, constants.getChainId(), senderNonce, 600_000, 1, 1,
                BENEFICIARY_B, 0, EMPTY_DATA, authorization);
        TransactionExecutor executor = newRealVmExecutor(setCodeTx, repository);
        assertTrue(executor.executeTransaction(), "The set-code transaction must succeed");
        assertNull(executor.getResult().getException());
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
}
