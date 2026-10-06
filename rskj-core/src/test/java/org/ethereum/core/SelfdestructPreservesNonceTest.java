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
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Set;

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
        TransactionExecutor executor = newRealVmExecutor(tx, repository);

        assertTrue(executor.executeTransaction());
        assertNull(executor.getResult().getException());

        assertClearedAccount(repository, CONTRACT_C, BigInteger.ONE);
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
        assertTrue(executor.getResult().getDeleteAccounts().contains(DataWord.valueOf(CONTRACT_C.getBytes())),
                "A cleared account is still recorded as deleted for the same-block creation check");
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
        TransactionExecutor executor = newRealVmExecutor(tx, repository);

        assertTrue(executor.executeTransaction());
        assertNull(executor.getResult().getException());

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
        TransactionExecutor executor = newRealVmExecutor(tx, repository);

        assertTrue(executor.executeTransaction());
        assertNull(executor.getResult().getException());

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
        TransactionExecutor executor = newRealVmExecutor(tx, repository);

        assertTrue(executor.executeTransaction());
        assertNull(executor.getResult().getException());

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
        TransactionExecutor executor = newRealVmExecutor(tx, repository);

        assertTrue(executor.executeTransaction());
        assertNull(executor.getResult().getException());

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
        TransactionExecutor executor = newRealVmExecutor(tx, repository);

        assertTrue(executor.executeTransaction());
        assertNull(executor.getResult().getException());

        assertFalse(repository.isExist(CONTRACT_C));
        assertEquals(Coin.valueOf(BALANCE_B + BALANCE_C), repository.getBalance(BENEFICIARY_B));
    }

    // -------------------------------------------------------------------------
    // Assertions
    // -------------------------------------------------------------------------

    private static void assertClearedAccount(MutableRepository repository, RskAddress address, BigInteger expectedNonce) {
        assertTrue(repository.isExist(address));
        assertEquals(expectedNonce, repository.getNonce(address));
        assertEquals(Coin.ZERO, repository.getBalance(address));
        assertEquals(0, repository.getCodeLength(address));
        assertEquals(0, repository.getStorageKeysCount(address));
        assertFalse(repository.hasInitializedStorage(address));
        assertNull(repository.getStorageValue(address, DataWord.ZERO));
        assertEquals(MutableRepository.KECCAK_256_OF_EMPTY_ARRAY, repository.getCodeHashStandard(address));
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
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.ZERO)
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
