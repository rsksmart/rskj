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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.Set;
import java.util.stream.Stream;

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
    private static final byte OP_CALLCODE = (byte) 0xf2;
    private static final byte OP_DELEGATECALL = (byte) 0xf4;
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
    private static final long MULTI_CALL_GAS_LIMIT = 600_000;
    private static final long CODE_DEPOSIT_GAS_LIMIT = 150_000;

    static Stream<Arguments> activationStates() {
        return Stream.of(
                Arguments.of("after activation", true),
                Arguments.of("before activation", false));
    }

    @ParameterizedTest(name = "RSKIP701 case 7: contract with non-zero nonce {0}")
    @MethodSource("activationStates")
    void contractWithNonZeroNonceIsClearedAndKeepsItsNonceOnlyAfterActivation(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701();
        }
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_C);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        Transaction tx = signedCall(CONTRACT_C, beneficiaryWord(BENEFICIARY_B));
        TransactionExecutor executor = execute(repository, tx);

        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(CONTRACT_C.getBytes())),
                "A cleared account is still recorded as deleted for the same-block creation check");
        if (activated) {
            assertClearedAccount(repository, CONTRACT_C, BigInteger.ONE);
        } else {
            assertFalse(repository.isExist(CONTRACT_C));
        }
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
    }

    @ParameterizedTest(name = "RSKIP701: contract naming itself as beneficiary {0}")
    @MethodSource("activationStates")
    void contractWithNonZeroNonceNamingItselfBurnsItsBalanceAndIsClearedOnlyAfterActivation(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701();
        }
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_C);
        installContract(repository, CONTRACT_X, BigInteger.ONE, codeCallingThenStoringBalancePlusOne(CONTRACT_C), 0);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        TransactionExecutor executor = execute(repository, signedCall(CONTRACT_X, beneficiaryWord(CONTRACT_C)));

        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(CONTRACT_C.getBytes())));
        // The balance is burned at SELFDESTRUCT, not only at the end of the transaction: the caller reads zero
        assertEquals(DataWord.ONE, repository.getStorageValue(CONTRACT_X, DataWord.ZERO));
        if (activated) {
            // Only a delegated account keeps its balance when it names itself; this one burns it
            assertClearedAccount(repository, CONTRACT_C, BigInteger.ONE);
        } else {
            assertFalse(repository.isExist(CONTRACT_C));
        }
        assertEquals(Coin.valueOf(BALANCE_B), repository.getBalance(BENEFICIARY_B));
    }

    @ParameterizedTest(name = "RSKIP701: EXTCODEHASH and EXTCODESIZE of a destroyed contract {0}")
    @MethodSource("activationStates")
    void extcodehashOfAClearedContractIsTheHashOfEmptyCodeOnlyAfterActivation(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701();
        }
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_C);
        installContract(repository, CONTRACT_P, BigInteger.ONE, codeProbing(CONTRACT_C), 0);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        execute(repository, signedCall(BigInteger.ZERO, CONTRACT_C, beneficiaryWord(BENEFICIARY_B)));
        execute(repository, signedCall(BigInteger.ONE, CONTRACT_P, EMPTY_DATA));

        assertEquals(DataWord.ONE, repository.getStorageValue(CONTRACT_P, DataWord.valueOf(2)), "The probe must have run");
        if (activated) {
            assertEquals(DataWord.valueOf(MutableRepository.KECCAK_256_OF_EMPTY_ARRAY.getBytes()),
                    repository.getStorageValue(CONTRACT_P, DataWord.ZERO),
                    "A cleared account exists, so EXTCODEHASH pushes the hash of empty code");
        } else {
            assertNull(repository.getStorageValue(CONTRACT_P, DataWord.ZERO),
                    "A deleted account does not exist, so EXTCODEHASH pushes zero");
        }
        // Control: no code in either case
        assertNull(repository.getStorageValue(CONTRACT_P, DataWord.ONE));
    }

    @Test
    void selfdestructNamingAClearedAccountPaysNoNewAccountCostWhileADeletedOnePaysIt() {
        long clearedArm = gasOfSelfdestructNamingADestroyedContract();
        deactivateRskip701();
        long deletedArm = gasOfSelfdestructNamingADestroyedContract();

        assertEquals(GasCost.NEW_ACCT_SUICIDE, deletedArm - clearedArm);
    }

    /**
     * Destroys C (nonce 1) in a first transaction, then E executes SELFDESTRUCT naming C in a second one.
     * Returns the gas of the second transaction before refunds.
     */
    private long gasOfSelfdestructNamingADestroyedContract() {
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_C);
        installContract(repository, CONTRACT_E, BigInteger.ONE, CODE_D, 0);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        execute(repository, signedCall(BigInteger.ZERO, CONTRACT_C, beneficiaryWord(BENEFICIARY_B)));

        TransactionExecutor executor = execute(repository,
                signedCall(BigInteger.ONE, CONTRACT_E, beneficiaryWord(CONTRACT_C)));

        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(CONTRACT_E.getBytes())));
        return executor.getResult().getGasUsedBeforeRefunds();
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
        deactivateRskip701KeepingRskip545();

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

    /** RSKIP701 case 4: both opcodes run D's SELFDESTRUCT in the context of A, via the same owner address path. */
    static Stream<Arguments> codeSharingCallOpcodes() {
        return Stream.of(
                Arguments.of("DELEGATECALL", OP_DELEGATECALL),
                Arguments.of("CALLCODE", OP_CALLCODE));
    }

    @ParameterizedTest(name = "RSKIP701 case 4: delegated account reached through {0} only moves its balance")
    @MethodSource("codeSharingCallOpcodes")
    void delegatedAccountReachedThroughCodeSharingCallOnlyMovesItsBalance(String opcodeName, byte opcode) {
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_D, BigInteger.ONE, CODE_D, 0);
        installContract(repository, CONTRACT_E, BigInteger.ONE, codeForwardingTo(opcode, CONTRACT_D), 0);
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

    /** Plan T06 @base (council C#24): with RSKIP701 off the delegated account is marked and earns the refund too. */
    @ParameterizedTest(name = "RSKIP701 refund of a delegated account and of a contract {0}")
    @MethodSource("activationStates")
    void delegatedAccountEarnsNoRefundWhileAContractEarnsTheCappedRefund(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701KeepingRskip545();
        }
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
        if (activated) {
            assertEquals(0, delegated.getResult().getDeductedRefund());
            assertTrue(delegatedArm.isActiveDelegatedEOA(authorityAddress));
        } else {
            assertEquals(contract.getResult().getDeductedRefund(), delegated.getResult().getDeductedRefund());
            assertFalse(delegatedArm.isExist(authorityAddress));
        }
    }

    @ParameterizedTest(name = "RSKIP701 case 12: balance received after marking {0}")
    @MethodSource("activationStates")
    void balanceReceivedAfterMarkingIsRemovedWithTheRestOnlyAfterActivation(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701();
        }
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_C);
        installContract(repository, CONTRACT_Y, BigInteger.ZERO, codeDestructingTo(CONTRACT_C), BALANCE_Y);
        installContract(repository, CONTRACT_X, BigInteger.ONE, codeCallingThenCalling(CONTRACT_C, CONTRACT_Y), 0);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        TransactionExecutor executor = execute(repository, signedCall(CONTRACT_X, beneficiaryWord(BENEFICIARY_B)));

        if (activated) {
            assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(CONTRACT_Y.getBytes())));
            assertClearedAccount(repository, CONTRACT_C, BigInteger.ONE);
        } else {
            assertFalse(repository.isExist(CONTRACT_C));
        }
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
        assertFalse(repository.isExist(CONTRACT_Y));
    }

    @ParameterizedTest(name = "RSKIP701 case 11: contract raising its nonce before SELFDESTRUCT {0}")
    @MethodSource("activationStates")
    void contractRaisingItsNonceBeforeSelfdestructIsClearedWithTheRaisedNonceOnlyAfterActivation(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701();
        }
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_G, BigInteger.ZERO, CODE_G, BALANCE_C);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        RskAddress child = new RskAddress(HashUtil.calcNewAddr(CONTRACT_G.getBytes(), BigInteger.ZERO.toByteArray()));

        TransactionExecutor executor = execute(repository, signedCall(CONTRACT_G, beneficiaryWord(BENEFICIARY_B)));

        if (activated) {
            assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(CONTRACT_G.getBytes())));
            assertClearedAccount(repository, CONTRACT_G, BigInteger.ONE);
            assertTrue(repository.isExist(child));
            assertEquals(BigInteger.ONE, repository.getNonce(child));
        } else {
            assertFalse(repository.isExist(CONTRACT_G));
        }
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
    }

    @ParameterizedTest(name = "RSKIP701: nonce raised after marking {0}")
    @MethodSource("activationStates")
    void contractRaisingItsNonceAfterBeingMarkedIsClearedWithTheNonceAtTheEndOfTheTransaction(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701();
        }
        // CALLDATASIZE ISZERO PUSH1 9 JUMPI | PUSH1 0 CALLDATALOAD SELFDESTRUCT | JUMPDEST CREATE(0, 0, 0) POP STOP
        byte[] codeC = Hex.decode("3615600957600035ff5b600060006000f05000");
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ZERO, codeC, BALANCE_C);
        // The first call marks C, the second runs its CREATE and raises its nonce to 1
        installContract(repository, CONTRACT_X, BigInteger.ONE, codeCallingThenCalling(CONTRACT_C, CONTRACT_C), 0);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        TransactionExecutor executor = execute(repository, signedCall(CONTRACT_X, beneficiaryWord(BENEFICIARY_B)));

        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(CONTRACT_C.getBytes())));
        if (activated) {
            assertClearedAccount(repository, CONTRACT_C, BigInteger.ONE);
        } else {
            assertFalse(repository.isExist(CONTRACT_C));
        }
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
    }

    /** Plan T19 (council C#24): a designator-shaped contract with no flag is marked; cleared with RSKIP701, deleted without. */
    @ParameterizedTest(name = "RSKIP701 case 13: designator-shaped code without the flag {0}")
    @MethodSource("activationStates")
    void contractWithDelegationIndicatorCodeAndNoFlagIsCleared(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701KeepingRskip545();
        }
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
        if (activated) {
            assertClearedAccount(repository, CONTRACT_P, BigInteger.ONE);
        } else {
            assertFalse(repository.isExist(CONTRACT_P));
        }
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
    void contractCreatedByCreate2AtAPrefundedAddressAndDestroyedInTheSameTransactionIsDeleted() {
        MutableRepository repository = createRepository();
        installContract(repository, FACTORY_F, BigInteger.ONE, CODE_FACTORY_F, 0);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        RskAddress created = new RskAddress(HashUtil.calcSaltAddr(FACTORY_F, Bytes.of(INIT_CODE_D), CREATE2_SALT));
        long prefund = 7;
        createAccountWithBalance(repository, created, prefund);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        TransactionExecutor executor = execute(repository, signedCall(FACTORY_F, beneficiaryWord(BENEFICIARY_B)));

        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(created.getBytes())),
                "The created contract must have executed SELFDESTRUCT");
        assertFalse(repository.isExist(created), "An address that held a balance is still created in this transaction");
        assertEquals(Coin.valueOf(BALANCE_B + prefund), repository.getBalance(BENEFICIARY_B));
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

    // -------------------------------------------------------------------------
    // The delegated check applies to the account that runs SELFDESTRUCT, not to a caller or a code address
    // -------------------------------------------------------------------------

    /**
     * Plan T01/T04 negative (council C#10 a): the tx goes to delegated A, whose delegate CALLs C (nonce 1), and C
     * runs SELFDESTRUCT. C is the account that destroys itself, so it is marked and cleared and earns the refund;
     * A only forwarded the call.
     */
    @ParameterizedTest(name = "RSKIP701: contract destroyed by a call from a delegated account {0}")
    @MethodSource("activationStates")
    void contractCalledByADelegatedAccountIsMarkedAndTheDelegatedAccountIsUntouched(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701KeepingRskip545();
        }
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_C);
        installContract(repository, CONTRACT_E, BigInteger.ONE, codeCallingWithCallData(CONTRACT_C), 0);
        createAccountWithBalance(repository, authorityAddress, BALANCE_A);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        installDelegation(repository, authorityKey, BigInteger.ZERO, CONTRACT_E, BigInteger.ZERO);

        TransactionExecutor executor = execute(repository,
                signedCall(BigInteger.ONE, authorityAddress, beneficiaryWord(BENEFICIARY_B)));

        assertEquals(Set.of(DataWord.valueOf(CONTRACT_C.getBytes())), executor.getResult().getDeleteAccounts());
        long gasBeforeRefunds = executor.getResult().getGasUsedBeforeRefunds();
        assertEquals(Math.min(GasCost.SUICIDE_REFUND, gasBeforeRefunds / 2), executor.getResult().getDeductedRefund());
        if (activated) {
            assertClearedAccount(repository, CONTRACT_C, BigInteger.ONE);
        } else {
            assertFalse(repository.isExist(CONTRACT_C));
        }
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
        assertEquals(Coin.valueOf(BALANCE_A), repository.getBalance(authorityAddress));
        assertEquals(BigInteger.ONE, repository.getNonce(authorityAddress));
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(CONTRACT_E), repository.getCode(authorityAddress));
    }

    static Stream<Arguments> codeSharingCallOpcodesAndActivationStates() {
        return Stream.of(
                Arguments.of("DELEGATECALL", OP_DELEGATECALL, "after activation", true),
                Arguments.of("DELEGATECALL", OP_DELEGATECALL, "before activation", false),
                Arguments.of("CALLCODE", OP_CALLCODE, "after activation", true),
                Arguments.of("CALLCODE", OP_CALLCODE, "before activation", false));
    }

    /**
     * Plan T04 negative (council C#10 b): K (nonce 1) uses {0} on delegated A, whose delegate D runs SELFDESTRUCT.
     * The code runs in the context of K, so K is the account that destroys itself; A is only the code address.
     */
    @ParameterizedTest(name = "RSKIP701: contract running the code of a delegated account through {0} {2}")
    @MethodSource("codeSharingCallOpcodesAndActivationStates")
    void contractRunningADelegatedAccountsCodeIsMarkedAndTheDelegatedAccountIsUntouched(
            String opcodeName, byte opcode, String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701KeepingRskip545();
        }
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_D, BigInteger.ONE, CODE_D, 0);
        installContract(repository, CONTRACT_X, BigInteger.ONE, codeForwardingTo(opcode, authorityAddress), BALANCE_C);
        createAccountWithBalance(repository, authorityAddress, BALANCE_A);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        installDelegation(repository, authorityKey, BigInteger.ZERO, CONTRACT_D, BigInteger.ZERO);

        TransactionExecutor executor = execute(repository,
                signedCall(BigInteger.ONE, CONTRACT_X, beneficiaryWord(BENEFICIARY_B)));

        assertEquals(Set.of(DataWord.valueOf(CONTRACT_X.getBytes())), executor.getResult().getDeleteAccounts());
        if (activated) {
            assertClearedAccount(repository, CONTRACT_X, BigInteger.ONE);
        } else {
            assertFalse(repository.isExist(CONTRACT_X));
        }
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
        assertEquals(Coin.valueOf(BALANCE_A), repository.getBalance(authorityAddress));
        assertEquals(BigInteger.ONE, repository.getNonce(authorityAddress));
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(CONTRACT_D), repository.getCode(authorityAddress));
        assertNull(repository.getStorageValue(authorityAddress, DataWord.ZERO), "the delegate wrote into K, not into A");
    }

    // -------------------------------------------------------------------------
    // More shapes
    // -------------------------------------------------------------------------

    /**
     * Plan T26 (council C#13): A sends its own set-code transaction (tx nonce 1, tuple nonce 2) that calls A, and
     * the delegate runs SELFDESTRUCT. With RSKIP701 A keeps nonce 3, so its own past nonce-0 transaction cannot be
     * replayed. Without it A is deleted, its nonce goes back to 0 and the old transaction is valid again.
     */
    @ParameterizedTest(name = "RSKIP701: replay of the authority own past transaction {0}")
    @MethodSource("activationStates")
    void authoritysOwnPastTransactionCannotBeReplayedAfterItsDelegateSelfdestructsOnlyAfterActivation(
            String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701KeepingRskip545();
        }
        long authorityFunds = 10_000_000;
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_D, BigInteger.ONE, CODE_D, 0);
        createAccountWithBalance(repository, authorityAddress, authorityFunds);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        Transaction pastTx = signedTransfer(authorityKey, BigInteger.ZERO, BENEFICIARY_B, 1);
        assertTrue(newRealVmExecutor(pastTx, repository).executeTransaction(), "the authority sends its own nonce-0 transaction");
        SetCodeAuthorization ownTuple = createValidAuthorizationTuple(
                CONTRACT_D, BigInteger.valueOf(2), constants.getChainId(), authorityKey);
        TransactionExecutor t4 = newRealVmExecutor(createSignedType4Transaction(authorityKey, constants.getChainId(), BigInteger.ONE,
                600_000, 1, 1, authorityAddress, 0, beneficiaryWord(BENEFICIARY_B), ownTuple), repository);
        assertTrue(t4.executeTransaction(), "the authority sends its own set-code transaction");
        // Fund A again so that only the nonce decides whether the old transaction is valid
        execute(repository, signedTransfer(senderKey, BigInteger.ZERO, authorityAddress, 200_000));

        boolean replayed = newRealVmExecutor(pastTx, repository).executeTransaction();

        if (activated) {
            assertFalse(replayed, "nonce too low: the authority kept nonce 3");
            assertEquals(BigInteger.valueOf(3), repository.getNonce(authorityAddress));
            assertArrayEquals(DelegationCodeResolver.createDelegatedCode(CONTRACT_D), repository.getCode(authorityAddress));
        } else {
            assertTrue(replayed, "the authority was deleted, so its nonce-0 transaction is valid again");
            assertEquals(BigInteger.ONE, repository.getNonce(authorityAddress));
        }
    }

    /**
     * Plan row for several fates in one transaction (council C#14): X destroys E (nonce 0), destroys C (nonce 1)
     * and calls delegated A, whose delegate runs SELFDESTRUCT. E is deleted, C is cleared, A is intact, and the
     * refund counts the two marked accounts. X writes four slots first so that the gas/2 cap does not hide the count.
     */
    @ParameterizedTest(name = "RSKIP701: deleted, cleared and delegated in one transaction {0}")
    @MethodSource("activationStates")
    void oneTransactionDeletesClearsAndKeepsAccordingToEachAccount(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701KeepingRskip545();
        }
        RskAddress contractNonceZero = CONTRACT_Y;
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_D, BigInteger.ONE, CODE_D, 0);
        installContract(repository, contractNonceZero, BigInteger.ZERO, CODE_D, BALANCE_Y);
        installContract(repository, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_C);
        installContract(repository, CONTRACT_X, BigInteger.ONE,
                codeWritingFourSlotsThenCallingEach(contractNonceZero, CONTRACT_C, authorityAddress), 0);
        createAccountWithBalance(repository, authorityAddress, BALANCE_A);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        installDelegation(repository, authorityKey, BigInteger.ZERO, CONTRACT_D, BigInteger.ZERO);

        TransactionExecutor executor = execute(repository,
                signedCall(BigInteger.ONE, CONTRACT_X, beneficiaryWord(BENEFICIARY_B), MULTI_CALL_GAS_LIMIT));

        long gasBeforeRefunds = executor.getResult().getGasUsedBeforeRefunds();
        assertTrue(3 * GasCost.SUICIDE_REFUND < gasBeforeRefunds / 2,
                "the cap must not hide the number of marked accounts, gas before refunds: " + gasBeforeRefunds);
        assertFalse(repository.isExist(contractNonceZero));
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_Y + BALANCE_C + BALANCE_A), repository.getBalance(BENEFICIARY_B));
        if (activated) {
            assertEquals(2, executor.getResult().getDeleteAccounts().size());
            assertEquals(2 * GasCost.SUICIDE_REFUND, executor.getResult().getDeductedRefund());
            assertClearedAccount(repository, CONTRACT_C, BigInteger.ONE);
            assertEquals(BigInteger.ONE, repository.getNonce(authorityAddress));
            assertArrayEquals(DelegationCodeResolver.createDelegatedCode(CONTRACT_D), repository.getCode(authorityAddress));
            assertEquals(DataWord.valueOf(42), repository.getStorageValue(authorityAddress, DataWord.ZERO));
            assertEquals(Coin.ZERO, repository.getBalance(authorityAddress));
        } else {
            assertEquals(3, executor.getResult().getDeleteAccounts().size());
            assertEquals(3 * GasCost.SUICIDE_REFUND, executor.getResult().getDeductedRefund());
            assertFalse(repository.isExist(CONTRACT_C));
            assertFalse(repository.isExist(authorityAddress));
        }
    }

    /**
     * Plan T06 halt (council C#17): the delegate writes slot 1 after SELFDESTRUCT. SELFDESTRUCT halts the frame, so
     * slot 1 stays unset and the caller sees a successful CALL. With RSKIP701 the account keeps slot 0 = 42.
     */
    @ParameterizedTest(name = "RSKIP701: SELFDESTRUCT in a delegated account halts {0}")
    @MethodSource("activationStates")
    void selfdestructInADelegatedAccountHaltsTheFrameAndTheCallSucceeds(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701KeepingRskip545();
        }
        // CODE_D followed by PUSH1 1 PUSH1 1 SSTORE: never reached
        byte[] codeWritingAfterSelfdestruct = ByteUtil.merge(CODE_D, Hex.decode("6001600155"));
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_D, BigInteger.ONE, codeWritingAfterSelfdestruct, 0);
        installContract(repository, CONTRACT_X, BigInteger.ONE, codeCallingThenStoringSuccess(authorityAddress), 0);
        createAccountWithBalance(repository, authorityAddress, BALANCE_A);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();
        installDelegation(repository, authorityKey, BigInteger.ZERO, CONTRACT_D, BigInteger.ZERO);

        execute(repository, signedCall(BigInteger.ONE, CONTRACT_X, beneficiaryWord(BENEFICIARY_B)));

        assertEquals(DataWord.ONE, repository.getStorageValue(CONTRACT_X, DataWord.ZERO), "the CALL returns 1");
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_A), repository.getBalance(BENEFICIARY_B));
        if (activated) {
            assertEquals(DataWord.valueOf(42), repository.getStorageValue(authorityAddress, DataWord.ZERO));
            assertNull(repository.getStorageValue(authorityAddress, DataWord.ONE), "nothing runs after SELFDESTRUCT");
            assertEquals(BigInteger.ONE, repository.getNonce(authorityAddress));
        } else {
            assertFalse(repository.isExist(authorityAddress));
        }
    }

    /**
     * Plan T21 VM path (council C#18): Y does CREATE2 with init code that runs SELFDESTRUCT, then Y reverts. The
     * child's marks and its creation are not merged into the caller, so the prefunded child address keeps its
     * balance and nothing is deleted or cleared.
     */
    @ParameterizedTest(name = "RSKIP701: SELFDESTRUCT in the init code of a reverted CREATE2 {0}")
    @MethodSource("activationStates")
    void selfdestructInTheInitCodeOfARevertedCreate2LeavesNoMark(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701();
        }
        byte[] initCode = codeDestructingTo(BENEFICIARY_B);
        // PUSH22 <init> PUSH1 0 MSTORE | CREATE2(0, 10, 22, 1) POP | REVERT(0, 0)
        byte[] codeY = ByteUtil.merge(Hex.decode("75"), initCode,
                Hex.decode("600052" + "60016016600a6000f5" + "50" + "60006000fd"));
        RskAddress child = new RskAddress(HashUtil.calcSaltAddr(CONTRACT_Y, Bytes.of(initCode), DataWord.ONE.getData()));
        long prefund = 7;
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_Y, BigInteger.ONE, codeY, 0);
        installContract(repository, CONTRACT_X, BigInteger.ONE, codeCalling(CONTRACT_Y), 0);
        createAccountWithBalance(repository, child, prefund);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        TransactionExecutor executor = execute(repository,
                signedCall(BigInteger.ZERO, CONTRACT_X, EMPTY_DATA, MULTI_CALL_GAS_LIMIT));

        assertTrue(executor.getResult().getDeleteAccounts().isEmpty());
        assertTrue(executor.getResult().getCreatedAccounts().isEmpty());
        assertTrue(repository.isExist(child));
        assertEquals(Coin.valueOf(prefund), repository.getBalance(child));
        assertEquals(BigInteger.ZERO, repository.getNonce(child));
        assertEquals(Coin.valueOf(BALANCE_B), repository.getBalance(BENEFICIARY_B));
    }

    /**
     * Plan T21 VM path (council C#18): W runs CREATE with init code that calls C (nonce 1, which runs SELFDESTRUCT)
     * and then returns 1000 bytes it cannot pay to deposit. The creation fails (RSKIP453 early return), so the mark
     * of C made inside it is dropped and C keeps everything.
     */
    @ParameterizedTest(name = "RSKIP701: SELFDESTRUCT inside a creation whose code deposit runs out of gas {0}")
    @MethodSource("activationStates")
    void selfdestructInsideACreationThatCannotPayItsCodeDepositLeavesNoMark(String phase, boolean activated) {
        if (!activated) {
            deactivateRskip701();
        }
        // PUSH20 B PUSH1 0 MSTORE | CALL(GAS, C, 0, 0, 32, 0, 0) POP | RETURN(0, 1000)
        byte[] initCode = ByteUtil.merge(Hex.decode("73"), BENEFICIARY_B.getBytes(), Hex.decode("600052" + "60006000602060006000" + "73"),
                CONTRACT_C.getBytes(), Hex.decode("5af1" + "50" + "6103e86000f3"));
        assertEquals(64, initCode.length);
        // CODECOPY(0, 15, 64) CREATE(0, 0, 64) STOP, followed by the 64 bytes of the init code. The failed creation
        // consumes all the gas of W (RSK has no 63/64 rule), so W ends with STOP, which costs nothing
        byte[] codeW = ByteUtil.merge(Hex.decode("6040600f6000" + "39" + "604060006000f0" + "00"), initCode);
        MutableRepository repository = createRepository();
        installContract(repository, CONTRACT_C, BigInteger.ONE, CODE_D, BALANCE_C);
        installContract(repository, CONTRACT_X, BigInteger.ONE, codeW, 0);
        createAccountWithBalance(repository, BENEFICIARY_B, BALANCE_B);
        fundSender(repository, SENDER_BALANCE);
        mockExecutionBlockForRealVm();

        TransactionExecutor executor = execute(repository, signedCall(BigInteger.ZERO, CONTRACT_X, EMPTY_DATA, CODE_DEPOSIT_GAS_LIMIT));

        assertTrue(executor.getResult().getDeleteAccounts().isEmpty());
        assertEquals(BigInteger.ONE, repository.getNonce(CONTRACT_C));
        assertArrayEquals(CODE_D, repository.getCode(CONTRACT_C));
        assertEquals(Coin.valueOf(BALANCE_C), repository.getBalance(CONTRACT_C));
        assertNull(repository.getStorageValue(CONTRACT_C, DataWord.ZERO));
        assertEquals(Coin.valueOf(BALANCE_B), repository.getBalance(BENEFICIARY_B));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * RSKIP545 requires RSKIP701, so it is turned off too: the state every shipped network is in before cardamom1000.
     */
    private void deactivateRskip701() {
        activateAllBut(ConsensusRule.RSKIP545, ConsensusRule.RSKIP701);
    }

    /**
     * Keeps RSKIP545 on so that delegated accounts exist with RSKIP701 off. ActivationConfig.read rejects this
     * combination, so no node runs it; it isolates the RSKIP701 gate on the delegated-account paths.
     */
    private void deactivateRskip701KeepingRskip545() {
        activateAllBut(ConsensusRule.RSKIP701);
    }

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

    /**
     * CALLDATACOPY(0, 0, CALLDATASIZE) DELEGATECALL(GAS, target, 0, CALLDATASIZE, 0, 0) POP STOP
     * <pre>
     * 36 6000 6000 37            CALLDATACOPY(0, 0, CALLDATASIZE)
     * 6000 6000 36 6000          retSize=0, retOffset=0, argsSize=CALLDATASIZE, argsOffset=0
     * 73 &lt;target&gt; 5a f4         PUSH20 target, GAS, DELEGATECALL
     * 50 00                      POP STOP
     * </pre>
     * For CALLCODE the call takes one more stack argument, the value, so a PUSH1 0 goes between the
     * arguments offset and the target: CALLCODE(GAS, target, 0, 0, CALLDATASIZE, 0, 0).
     * <pre>
     * 36 6000 6000 37 6000 6000 36 6000 | 6000 | 73 &lt;target&gt; 5a f2 50 00
     * </pre>
     */
    private static byte[] codeForwardingTo(byte callOpcode, RskAddress target) {
        String valueArgument = callOpcode == OP_CALLCODE ? "6000" : "";
        return ByteUtil.merge(
                Hex.decode("36600060003760006000366000" + valueArgument + "73"),
                target.getBytes(),
                new byte[]{(byte) 0x5a, callOpcode, (byte) 0x50, (byte) 0x00});
    }

    /**
     * Stores EXTCODEHASH(target) in slot 0, EXTCODESIZE(target) in slot 1 and 1 in slot 2.
     * PUSH20 target EXTCODEHASH PUSH1 0 SSTORE | PUSH20 target EXTCODESIZE PUSH1 1 SSTORE | PUSH1 1 PUSH1 2 SSTORE STOP
     */
    private static byte[] codeProbing(RskAddress target) {
        return ByteUtil.merge(
                Hex.decode("73"), target.getBytes(), Hex.decode("3f600055" + "73"),
                target.getBytes(), Hex.decode("3b600155" + "6001600255" + "00"));
    }

    /**
     * CALLDATACOPY(0, 0, 32) CALL(GAS, target, 0, 0, 32, 0, 0) POP, then stores BALANCE(target) + 1 in slot 0:
     * PUSH1 1 PUSH20 target BALANCE ADD PUSH1 0 SSTORE STOP. The + 1 tells a zero balance from a probe that did not run.
     */
    private static byte[] codeCallingThenStoringBalancePlusOne(RskAddress target) {
        return ByteUtil.merge(
                Hex.decode("602060006000" + "37" + "60006000602060006000" + "73"), target.getBytes(),
                Hex.decode("5af150" + "6001" + "73"), target.getBytes(),
                Hex.decode("3101" + "600055" + "00"));
    }

    /** CALL(GAS, target, 0, 0, 0, 0, 0) POP STOP */
    private static byte[] codeCalling(RskAddress target) {
        return ByteUtil.merge(Hex.decode("6000600060006000600073"), target.getBytes(), Hex.decode("5af15000"));
    }

    /** CALLDATACOPY(0, 0, 32) CALL(GAS, target, 0, 0, 32, 0, 0) POP STOP: forwards the call data with CALL */
    private static byte[] codeCallingWithCallData(RskAddress target) {
        return ByteUtil.merge(
                Hex.decode("602060006000" + "37" + "60006000602060006000" + "73"), target.getBytes(),
                Hex.decode("5af15000"));
    }

    /** CALLDATACOPY(0, 0, 32) CALL(GAS, target, 0, 0, 32, 0, 0) PUSH1 0 SSTORE STOP: stores the CALL result in slot 0 */
    private static byte[] codeCallingThenStoringSuccess(RskAddress target) {
        return ByteUtil.merge(
                Hex.decode("602060006000" + "37" + "60006000602060006000" + "73"), target.getBytes(),
                Hex.decode("5af1" + "600055" + "00"));
    }

    /**
     * SSTORE 1 into slots 1 to 4, then CALLDATACOPY(0, 0, 32) and CALL(GAS, target, 0, 0, 32, 0, 0) POP for each
     * target in order, then STOP.
     */
    private static byte[] codeWritingFourSlotsThenCallingEach(RskAddress... targets) {
        byte[] code = Hex.decode("6001600155" + "6001600255" + "6001600355" + "6001600455" + "602060006000" + "37");
        for (RskAddress target : targets) {
            code = ByteUtil.merge(code, Hex.decode("60006000602060006000" + "73"), target.getBytes(), Hex.decode("5af150"));
        }
        return ByteUtil.merge(code, Hex.decode("00"));
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
        return signedCall(senderNonce, to, data, GAS_LIMIT);
    }

    private Transaction signedTransfer(ECKey key, BigInteger nonce, RskAddress to, long value) {
        Transaction tx = Transaction.builder()
                .nonce(nonce)
                .gasPrice(Coin.valueOf(1))
                .gasLimit(BigInteger.valueOf(GAS_LIMIT))
                .receiveAddress(to)
                .value(Coin.valueOf(value))
                .chainId(constants.getChainId())
                .build();
        tx.sign(key.getPrivKeyBytes());
        return tx;
    }

    private Transaction signedCall(BigInteger senderNonce, RskAddress to, byte[] data, long gasLimit) {
        Transaction tx = Transaction.builder()
                .nonce(senderNonce)
                .gasPrice(Coin.valueOf(1))
                .gasLimit(BigInteger.valueOf(gasLimit))
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
