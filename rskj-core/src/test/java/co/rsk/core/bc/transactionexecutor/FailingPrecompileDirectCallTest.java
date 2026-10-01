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

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.ethereum.vm.trace.SummarizedProgramTrace;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.core.bc.BlockExecutorTest.createAccount;
import static co.rsk.core.bc.transactionexecutor.FailingTestPrecompile.EMIT_LOG;
import static co.rsk.core.bc.transactionexecutor.FailingTestPrecompile.ERROR_CODE_OUTPUT;
import static co.rsk.core.bc.transactionexecutor.FailingTestPrecompile.FAILING_PRECOMPILE_ADDR;
import static co.rsk.core.bc.transactionexecutor.FailingTestPrecompile.GAS_FOR_DATA_THROWS;
import static co.rsk.core.bc.transactionexecutor.FailingTestPrecompile.RETURN_ERROR_CODE;
import static co.rsk.core.bc.transactionexecutor.FailingTestPrecompile.STORAGE_KEY;
import static co.rsk.core.bc.transactionexecutor.FailingTestPrecompile.STORAGE_VALUE;
import static co.rsk.core.bc.transactionexecutor.FailingTestPrecompile.THROW_JVM_ERROR;
import static co.rsk.core.bc.transactionexecutor.FailingTestPrecompile.WRITE_STORAGE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Direct call to a precompiled contract that writes state and/or emits a log and then throws.
 * Before RSKIP692 those effects are kept; after activation they are discarded.
 */
class FailingPrecompileDirectCallTest {

    private static final byte[] ZERO_BLOOM = new byte[256];

    private static final long GAS_PRICE = 1L;
    private static final long GAS_LIMIT = 100_000L;
    private static final long SENDER_INITIAL_BALANCE = 1_000_000L;
    private static final long VALUE = 1_000L;

    private TestSystemProperties config;
    private Repository track;
    private Account sender;
    private Block block;
    private TransactionExecutorFactory transactionExecutorFactory;

    private void setUp(boolean rskip692Active) {
        TestSystemProperties baseConfig = new TestSystemProperties();
        ActivationConfig activationConfig = withRskip692(baseConfig.getActivationConfig(), rskip692Active);
        config = spy(baseConfig);
        doReturn(activationConfig).when(config).getActivationConfig();

        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        transactionExecutorFactory = new TransactionExecutorFactory(
                config,
                new BlockStoreDummy(),
                null,
                new BlockFactory(activationConfig),
                new ProgramInvokeFactoryImpl(),
                new FailingTestPrecompile.PrecompiledContractsWithFailingContract(config, signatureCache),
                signatureCache
        );

        track = createRepository().startTracking();
        sender = createAccount("failingPrecompileSender", track, Coin.valueOf(SENDER_INITIAL_BALANCE));
        track.createAccount(FAILING_PRECOMPILE_ADDR);
        track.initializeStorage(FAILING_PRECOMPILE_ADDR);
        track.commit();

        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), activationConfig);
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(track.getRoot());
        block = blockGenerator.createChildBlock(genesis, Collections.emptyList(), Collections.emptyList(), 1, null);
    }

    // RSKIP-692 test case 5
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void storageWrittenBeforeFailureIsKeptOnlyBeforeActivation(boolean rskip692Active) {
        setUp(rskip692Active);

        TransactionExecutor executor = execute(legacyCall(new byte[]{WRITE_STORAGE}));

        assertNotNull(executor.getResult().getException());
        DataWord stored = track.getStorageValue(FAILING_PRECOMPILE_ADDR, STORAGE_KEY);
        if (rskip692Active) {
            assertNull(stored);
        } else {
            assertEquals(STORAGE_VALUE, stored);
        }
    }

    // RSKIP-692 test case 6
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void logEmittedBeforeFailureIsRecordedOnlyBeforeActivation(boolean rskip692Active) {
        setUp(rskip692Active);

        TransactionExecutor executor = execute(legacyCall(new byte[]{EMIT_LOG}));

        assertLogRecordedOnlyBeforeActivation(executor, rskip692Active);
    }

    // RSKIP-692 test case 7
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void storageAndLogBeforeFailureAreKeptOnlyBeforeActivation(boolean rskip692Active) {
        setUp(rskip692Active);

        TransactionExecutor executor = execute(legacyCall(new byte[]{WRITE_STORAGE | EMIT_LOG}));

        DataWord stored = track.getStorageValue(FAILING_PRECOMPILE_ADDR, STORAGE_KEY);
        if (rskip692Active) {
            assertNull(stored);
        } else {
            assertEquals(STORAGE_VALUE, stored);
        }
        assertLogRecordedOnlyBeforeActivation(executor, rskip692Active);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void senderNonceAndFeeRemainAndValueStaysWithSender(boolean rskip692Active) {
        setUp(rskip692Active);

        TransactionExecutor executor = execute(legacyCall(new byte[]{WRITE_STORAGE | EMIT_LOG}));

        // Legacy tx, no refund terms: fee is gasLimit * gasPrice = 100,000; the value is never transferred.
        assertEquals(Coin.valueOf(100_000L), executor.getPaidFees());
        assertEquals(BigInteger.ONE, track.getNonce(sender.getAddress()));
        assertEquals(Coin.valueOf(900_000L), track.getBalance(sender.getAddress()));
        assertEquals(Coin.ZERO, track.getBalance(FAILING_PRECOMPILE_ADDR));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void delegationProcessedBeforeFailingCallSurvives(boolean rskip692Active) {
        setUp(rskip692Active);
        byte chainId = config.getNetworkConstants().getChainId();
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());
        RskAddress delegate = new RskAddress(new ECKey().getAddress());

        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(chainId)
                .nonce(BigInteger.ZERO)
                .gasLimit(BigInteger.valueOf(GAS_LIMIT))
                .maxPriorityFeePerGas(Coin.valueOf(GAS_PRICE))
                .maxFeePerGas(Coin.valueOf(GAS_PRICE))
                .receiveAddress(FAILING_PRECOMPILE_ADDR)
                .value(BigInteger.valueOf(VALUE))
                .data(new byte[]{WRITE_STORAGE | EMIT_LOG})
                .authorizationList(List.of(
                        Rskip545TestSupport.createSignedAuthorization(authorityKey, delegate, BigInteger.ZERO, chainId)))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        TransactionExecutor executor = execute(tx);

        assertNotNull(executor.getResult().getException());
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(delegate), track.getCode(authority));
        assertEquals(BigInteger.ONE, track.getNonce(authority));
        if (rskip692Active) {
            assertNull(track.getStorageValue(FAILING_PRECOMPILE_ADDR, STORAGE_KEY));
            assertTrue(executor.getReceipt().getLogInfoList().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void errorCodeOutputIsASuccessfulCall(boolean rskip692Active) {
        setUp(rskip692Active);

        TransactionExecutor executor = execute(legacyCall(new byte[]{WRITE_STORAGE | EMIT_LOG | RETURN_ERROR_CODE}));

        assertNull(executor.getResult().getException());
        assertArrayEquals(ERROR_CODE_OUTPUT, executor.getResult().getHReturn());
        TransactionReceipt receipt = executor.getReceipt();
        assertTrue(receipt.isSuccessful());
        assertEquals(1, receipt.getLogInfoList().size());
        assertEquals(STORAGE_VALUE, track.getStorageValue(FAILING_PRECOMPILE_ADDR, STORAGE_KEY));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void exceptionFromGasForDataPropagatesRegardlessOfActivation(boolean rskip692Active) {
        setUp(rskip692Active);
        TransactionExecutor executor = newExecutor(legacyCall(new byte[]{GAS_FOR_DATA_THROWS}));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, executor::executeTransaction);

        assertEquals("test gas for data failure", thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void jvmErrorFromExecutePropagatesRegardlessOfActivation(boolean rskip692Active) {
        setUp(rskip692Active);
        TransactionExecutor executor = newExecutor(legacyCall(new byte[]{WRITE_STORAGE | EMIT_LOG | THROW_JVM_ERROR}));

        StackOverflowError thrown = assertThrows(StackOverflowError.class, executor::executeTransaction);

        assertEquals("test precompile error", thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void failedCallReportsErrorInTraceOnlyAfterActivation(boolean rskip692Active) {
        setUp(rskip692Active);
        Transaction tx = legacyCall(new byte[]{WRITE_STORAGE | EMIT_LOG});

        SummarizedProgramTrace trace = traceOf(execute(tx), tx);

        if (rskip692Active) {
            assertEquals("class org.ethereum.vm.exception.VMException: test precompile failure", trace.getError());
        } else {
            assertNull(trace.getError());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void callWithInsufficientGasReportsOutOfGasInTrace(boolean rskip692Active) {
        setUp(rskip692Active);
        // Intrinsic cost 21_000 + 16 for the one non-zero data byte, plus the 1_000 the contract declares: 22_016.
        Transaction tx = legacyCall(new byte[]{EMIT_LOG}, 22_015L);

        TransactionExecutor executor = execute(tx);
        SummarizedProgramTrace trace = traceOf(executor, tx);

        assertFalse(executor.getReceipt().isSuccessful());
        assertTrue(trace.getError().startsWith(
                "class org.ethereum.vm.program.Program$OutOfGasException: Out of Gas calling precompiled contract"),
                trace.getError());
    }

    private void assertLogRecordedOnlyBeforeActivation(TransactionExecutor executor, boolean rskip692Active) {
        assertNotNull(executor.getResult().getException());
        TransactionReceipt receipt = executor.getReceipt();
        byte[] receiptBloom = receipt.getBloomFilter().getData();
        if (rskip692Active) {
            assertFalse(receipt.isSuccessful());
            assertTrue(receipt.getLogInfoList().isEmpty());
            assertArrayEquals(ZERO_BLOOM, receiptBloom);
        } else {
            assertTrue(receipt.isSuccessful());
            assertEquals(1, receipt.getLogInfoList().size());
            assertArrayEquals(FAILING_PRECOMPILE_ADDR.getBytes(), receipt.getLogInfoList().get(0).getAddress());
            assertFalse(Arrays.equals(ZERO_BLOOM, receiptBloom));
        }
    }

    private static SummarizedProgramTrace traceOf(TransactionExecutor executor, Transaction tx) {
        ProgramTraceProcessor traceProcessor = new ProgramTraceProcessor();
        executor.extractTrace(traceProcessor);
        return (SummarizedProgramTrace) traceProcessor.getProgramTrace(tx.getHash());
    }

    private Transaction legacyCall(byte[] data) {
        return legacyCall(data, GAS_LIMIT);
    }

    private Transaction legacyCall(byte[] data, long gasLimit) {
        Transaction tx = Transaction.builder()
                .nonce(track.getNonce(sender.getAddress()))
                .gasPrice(BigInteger.valueOf(GAS_PRICE))
                .gasLimit(BigInteger.valueOf(gasLimit))
                .receiveAddress(FAILING_PRECOMPILE_ADDR)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.valueOf(VALUE))
                .data(data)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private TransactionExecutor execute(Transaction tx) {
        TransactionExecutor executor = newExecutor(tx);
        assertTrue(executor.executeTransaction());
        return executor;
    }

    private TransactionExecutor newExecutor(Transaction tx) {
        return transactionExecutorFactory.newInstance(tx, 0, block.getCoinbase(), track, block, 0L);
    }

    private static ActivationConfig withRskip692(ActivationConfig defaults, boolean active) {
        Map<ConsensusRule, Long> heights = new EnumMap<>(ConsensusRule.class);
        for (ConsensusRule rule : ConsensusRule.values()) {
            heights.put(rule, defaults.isActive(rule, 0L) ? 0L : -1L);
        }
        heights.put(ConsensusRule.RSKIP692, active ? 0L : -1L);
        return new ActivationConfig(heights, new HashMap<>());
    }
}
