/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.core;

import co.rsk.db.RepositorySnapshot;
import co.rsk.util.TestContract;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.util.AccessListCodec;
import org.ethereum.crypto.HashUtil;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.ContractRunner;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReversibleTransactionExecutorTest {

    @TempDir
    public Path tempDir;

    private RskTestFactory factory;
    private ContractRunner contractRunner;
    private ReversibleTransactionExecutor reversibleTransactionExecutor;
    private byte[] gasPrice = {0x07};
    @BeforeEach
    void setup() {
        factory = new RskTestFactory(tempDir);
        contractRunner = new ContractRunner(factory);
        reversibleTransactionExecutor = factory.getReversibleTransactionExecutor();
    }

    @Test
    void executeTransactionHello() {
        TestContract hello = TestContract.hello();
        CallTransaction.Function helloFn = hello.functions.get("hello");
        RskAddress contractAddress = contractRunner.addContract(hello.runtimeBytecode);

        RskAddress from = TestUtils.generateAddress("from");
        byte[] gasPrice = Hex.decode("00");
        byte[] value = Hex.decode("00");
        byte[] gasLimit = Hex.decode("f424");

        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result = reversibleTransactionExecutor.executeTransactionAtBlock(
                bestBlock,
                bestBlock.getCoinbase(),
                new ReversibleTransactionExecutor.ReversibleTransactionParams(
                        gasPrice, gasLimit, contractAddress.getBytes(), value, helloFn.encode(), from,
                        null, (byte) 0, TransactionType.LEGACY, null, null, null
                )
        );

        assertNull(result.getException());
        assertArrayEquals(
                new String[]{"chinchilla"},
                helloFn.decodeResult(result.getHReturn()));
    }

    @Test
    void executeTransactionGreeter() {
        TestContract greeter = TestContract.greeter();
        CallTransaction.Function greeterFn = greeter.functions.get("greet");

        ProgramResult result = contractRunner.createAndRunContract(
                Hex.decode(greeter.bytecode),
                greeterFn.encode("greet me"),
                BigInteger.ZERO,
                true
        );

        assertNull(result.getException());
        assertArrayEquals(
                new String[]{"greet me"},
                greeterFn.decodeResult(result.getHReturn()));
    }

    @Test
    void executeTransactionGreeterOtherSender() {
        TestContract greeter = TestContract.greeter();
        CallTransaction.Function greeterFn = greeter.functions.get("greet");
        RskAddress contractAddress = contractRunner.addContract(greeter.runtimeBytecode);

        RskAddress from = new RskAddress("0000000000000000000000000000000000000023"); // someone else
        byte[] gasPrice = Hex.decode("00");
        byte[] value = Hex.decode("00");
        byte[] gasLimit = Hex.decode("f424");

        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result = reversibleTransactionExecutor.executeTransactionAtBlock(
                bestBlock,
                bestBlock.getCoinbase(),
                new ReversibleTransactionExecutor.ReversibleTransactionParams(
                        gasPrice, gasLimit, contractAddress.getBytes(), value, greeterFn.encode("greet me"), from,
                        null, (byte) 0, TransactionType.LEGACY, null, null, null
                )
        );

        Assertions.assertTrue(result.isRevert());
    }

    @Test
    void executeTransactionCountCallsMultipleTimes() {
        TestContract countcalls = TestContract.countcalls();
        CallTransaction.Function callsFn = countcalls.functions.get("calls");
        RskAddress contractAddress = contractRunner.addContract(countcalls.runtimeBytecode);

        RskAddress from = new RskAddress("0000000000000000000000000000000000000023"); // someone else
        byte[] gasPrice = Hex.decode("00");
        byte[] value = Hex.decode("00");
        byte[] gasLimit = Hex.decode("f424");

        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result = reversibleTransactionExecutor.executeTransactionAtBlock(
                bestBlock,
                bestBlock.getCoinbase(),
                new ReversibleTransactionExecutor.ReversibleTransactionParams(
                        gasPrice, gasLimit, contractAddress.getBytes(), value, callsFn.encodeSignature(), from,
                        null, (byte) 0, TransactionType.LEGACY, null, null, null
                )
        );

        assertNull(result.getException());
        assertArrayEquals(
                new String[]{"calls: 1"},
                callsFn.decodeResult(result.getHReturn()));

        ProgramResult result2 = reversibleTransactionExecutor.executeTransactionAtBlock(
                bestBlock,
                bestBlock.getCoinbase(),
                new ReversibleTransactionExecutor.ReversibleTransactionParams(
                        gasPrice, gasLimit, contractAddress.getBytes(), value, callsFn.encodeSignature(), from,
                        null, (byte) 0, TransactionType.LEGACY, null, null, null
                )
        );

        assertNull(result2.getException());
        assertArrayEquals(
                new String[]{"calls: 1"},
                callsFn.decodeResult(result2.getHReturn()));
    }

    @Test
    void reversibleTransaction_type1_preservesAccessList() {
        byte[] accessList = accessListBytes();

        Transaction tx = executeAndCaptureTransaction(params(TransactionType.TYPE_1, accessList, null, null, null, new byte[]{0x07}));

        assertEquals(TransactionType.TYPE_1, tx.getType());
        assertEquals((byte) 33, tx.getChainId());
        assertArrayEquals(accessList, tx.getAccessListBytes());
        assertEquals(Coin.valueOf(7), tx.getGasPrice());
        assertNull(tx.getMaxPriorityFeePerGas());
        assertNull(tx.getMaxFeePerGas());
        assertNull(tx.getAuthorizationList());
    }

    @Test
    void reversibleTransaction_type2_preservesFeeCapsAndAccessList() {
        byte[] accessList = accessListBytes();

        Transaction tx = executeAndCaptureTransaction(params(TransactionType.TYPE_2, accessList, null, new byte[]{0x02}, new byte[]{0x05}, new byte[]{0x07}));

        assertEquals(TransactionType.TYPE_2, tx.getType());
        assertEquals((byte) 33, tx.getChainId());

        assertArrayEquals(accessList, tx.getAccessListBytes());
        assertEquals(Coin.valueOf(2), tx.getMaxPriorityFeePerGas());
        assertEquals(Coin.valueOf(5), tx.getMaxFeePerGas());
        // effective gas price = min(priority, maxFee)
        assertEquals(Coin.valueOf(2), tx.getGasPrice());
        assertNull(tx.getAuthorizationList());
    }

    @Test
    void reversibleTransaction_type4_preservesAuthorizationFeesAndAccessList() {
        byte[] accessList = accessListBytes();

        SetCodeAuthorization authorization = Rskip545TestSupport.minimalAuthorization((byte) 33);

        Transaction tx = executeAndCaptureTransaction(params(TransactionType.TYPE_4, accessList, List.of(authorization), new byte[]{0x02}, new byte[]{0x05}, new byte[]{0x07}));

        assertEquals(TransactionType.TYPE_4, tx.getType());
        assertEquals((byte) 33, tx.getChainId());

        assertArrayEquals(accessList, tx.getAccessListBytes());

        assertEquals(Coin.valueOf(2), tx.getMaxPriorityFeePerGas());
        assertEquals(Coin.valueOf(5), tx.getMaxFeePerGas());

        assertNotNull(tx.getAuthorizationList());
        assertEquals(List.of(authorization), tx.getAuthorizationList());
    }

    @Test
    void reversibleTransaction_type2WithPriorityOnly_defaultsMaxFeeToGasPrice() {
        Transaction tx = executeAndCaptureTransaction(params(TransactionType.TYPE_2, null, null, new byte[]{0x03},
                null, new byte[]{0x07}));

        assertEquals(TransactionType.TYPE_2, tx.getType());

        assertEquals(
                Coin.valueOf(3),
                tx.getMaxPriorityFeePerGas()
        );

        assertEquals(
                Coin.valueOf(7),
                tx.getMaxFeePerGas()
        );
    }

    @Test
    void reversibleTransaction_type2WithMaxFeeOnly_defaultsPriorityToGasPrice() {
        Transaction tx = executeAndCaptureTransaction(params(TransactionType.TYPE_2, null, null, null, new byte[]{0x09}, gasPrice));

        assertEquals(TransactionType.TYPE_2, tx.getType());

        assertEquals(Coin.valueOf(7), tx.getMaxPriorityFeePerGas());
        assertEquals(Coin.valueOf(9), tx.getMaxFeePerGas());
    }

    @Test
    void reversibleTransaction_type4WithoutFeeCaps_usesGasPriceForBoth() {
        SetCodeAuthorization authorization =
                Rskip545TestSupport.minimalAuthorization((byte) 33);

        Transaction tx = executeAndCaptureTransaction(params(TransactionType.TYPE_4, null, List.of(authorization),
                null, null, gasPrice));

        assertEquals(TransactionType.TYPE_4, tx.getType());

        assertEquals(valueOf(gasPrice), tx.getMaxPriorityFeePerGas());
        assertEquals(valueOf(gasPrice), tx.getMaxFeePerGas());
        assertEquals(valueOf(gasPrice), tx.getGasPrice());
    }

    @Test
    void reversibleTransaction_type4WithAllTypedFields_preservesAllFields() {
        byte[] accessList = accessListBytes();

        SetCodeAuthorization authorization = Rskip545TestSupport.minimalAuthorization((byte) 33);

        Transaction tx = executeAndCaptureTransaction(
                new ReversibleTransactionExecutor.ReversibleTransactionParams(
                        new byte[]{0x07},
                        BigInteger.valueOf(500_000).toByteArray(),
                        TestUtils.generateAddress("to").getBytes(),
                        new byte[]{0},
                        new byte[0],
                        TestUtils.generateAddress("from"),
                        List.of(authorization),
                        (byte) 33,
                        TransactionType.TYPE_4,
                        accessList,
                        new byte[]{0x03},
                        new byte[]{0x09}
                )
        );

        assertAll(
                () -> assertEquals(TransactionType.TYPE_4, tx.getType()),
                () -> assertEquals((byte) 33, tx.getChainId()),
                () -> assertArrayEquals(accessList, tx.getAccessListBytes()),
                () -> assertEquals(Coin.valueOf(3), tx.getMaxPriorityFeePerGas()),
                () -> assertEquals(Coin.valueOf(9), tx.getMaxFeePerGas()),
                () -> assertEquals(List.of(authorization), tx.getAuthorizationList())
        );
    }

    static Stream<Arguments> transactionTypes() {
        byte[] accessList = accessListBytes();
        SetCodeAuthorization auth = Rskip545TestSupport.minimalAuthorization((byte) 33);

        return Stream.of(
                Arguments.of(TransactionType.LEGACY, null, null, null, null),
                Arguments.of(TransactionType.TYPE_1, accessList, null, null, null),
                Arguments.of(TransactionType.TYPE_2, accessList, null, new byte[]{0x02}, new byte[]{0x05}),
                Arguments.of(TransactionType.TYPE_4, accessList, List.of(auth), new byte[]{0x02}, new byte[]{0x05})
        );
    }

    @ParameterizedTest
    @MethodSource("transactionTypes")
    void reversibleTransaction_buildsExpectedTransactionType(TransactionType expectedType, byte[] accessList, List<SetCodeAuthorization> authorizationList, byte[] priority, byte[] maxFee) {
        Transaction tx = executeAndCaptureTransaction(params(expectedType, accessList, authorizationList, priority, maxFee, new byte[]{0x07}));

        assertEquals(expectedType, tx.getType());
        assertEquals((byte) 33, tx.getChainId());

        if (expectedType == TransactionType.TYPE_1 || expectedType == TransactionType.TYPE_2 || expectedType == TransactionType.TYPE_4) {
            assertArrayEquals(accessList, tx.getAccessListBytes());
        } else {
            assertNull(tx.getAccessListBytes());
        }

        if (expectedType == TransactionType.TYPE_2 || expectedType == TransactionType.TYPE_4) {
            assertNotNull(tx.getMaxPriorityFeePerGas());
            assertNotNull(tx.getMaxFeePerGas());
        } else {
            assertNull(tx.getMaxPriorityFeePerGas());
            assertNull(tx.getMaxFeePerGas());
        }

        if (expectedType == TransactionType.TYPE_4) {
            assertEquals(authorizationList, tx.getAuthorizationList());
        } else {
            assertNull(tx.getAuthorizationList());
        }
    }

    @Test
    void type4WithOnlyPriorityFee_defaultsMaxFeeFromGasPrice() {
        SetCodeAuthorization authorization =
                Rskip545TestSupport.minimalAuthorization((byte) 33);

        Transaction tx = executeAndCaptureTransaction(params(TransactionType.TYPE_4, null, List.of(authorization), new byte[]{0x03}, null, gasPrice));
        assertEquals(TransactionType.TYPE_4, tx.getType());

        assertEquals(Coin.valueOf(3), tx.getMaxPriorityFeePerGas());
        assertEquals(valueOf(gasPrice), tx.getMaxFeePerGas());
    }

    @Test
    void type4WithOnlyMaxFee_defaultsPriorityFromGasPrice() {
        SetCodeAuthorization authorization =
                Rskip545TestSupport.minimalAuthorization((byte) 33);

        Transaction tx = executeAndCaptureTransaction(params(TransactionType.TYPE_4, null, List.of(authorization), null, new byte[]{0x09}, gasPrice));
        assertEquals(TransactionType.TYPE_4, tx.getType());
        assertEquals(valueOf(gasPrice), tx.getMaxPriorityFeePerGas());
        assertEquals(Coin.valueOf(9), tx.getMaxFeePerGas());
    }

    @Test
    void reversibleTransaction_nonce128_isEncodedUnsigned() {
        Transaction tx = executeAndCaptureTransaction(
                params(TransactionType.LEGACY, null, null, null, null, gasPrice),
                BigInteger.valueOf(128));

        assertArrayEquals(new byte[]{(byte) 0x80}, tx.getNonce());
    }

    @Test
    void reversibleTransaction_nonce128ContractCreation_computesCanonicalContractAddress() {
        RskAddress from = TestUtils.generateAddress("from");
        ReversibleTransactionExecutor.ReversibleTransactionParams createParams =
                new ReversibleTransactionExecutor.ReversibleTransactionParams(
                        gasPrice,
                        BigInteger.valueOf(500_000).toByteArray(),
                        null,
                        new byte[]{0},
                        new byte[0],
                        from,
                        null,
                        (byte) 33,
                        TransactionType.LEGACY,
                        null,
                        null,
                        null);

        Transaction tx = executeAndCaptureTransaction(createParams, BigInteger.valueOf(128));

        RskAddress expectedContractAddress = new RskAddress(HashUtil.calcNewAddr(from.getBytes(), new byte[]{(byte) 0x80}));
        assertEquals(expectedContractAddress, tx.getContractAddress());
    }

    public static Coin valueOf(byte[] value) {
        return new Coin(new BigInteger(1, value));
    }

    private static byte[] accessListBytes() {
        CallArguments.AccessListEntry entry = new CallArguments.AccessListEntry();
        entry.setAddress("0x0000000000000000000000000000000000000001");
        entry.setStorageKeys(List.of("0x" + "0".repeat(63) + "1"));

        return AccessListCodec.encodeAccessList(List.of(entry));
    }

    private Transaction executeAndCaptureTransaction(ReversibleTransactionExecutor.ReversibleTransactionParams params) {
        return executeAndCaptureTransaction(params, BigInteger.ZERO);
    }

    private Transaction executeAndCaptureTransaction(ReversibleTransactionExecutor.ReversibleTransactionParams params, BigInteger nonce) {

        RepositorySnapshot snapshot = mock(RepositorySnapshot.class);
        Repository track = mock(Repository.class);
        TransactionExecutorFactory executorFactory = mock(TransactionExecutorFactory.class);
        TransactionExecutor transactionExecutor = mock(TransactionExecutor.class);
        PrecompiledContracts precompiledContracts = mock(PrecompiledContracts.class);

        Block block = mock(Block.class);
        RskAddress coinbase = TestUtils.generateAddress("coinbase");

        when(snapshot.startTracking()).thenReturn(track);
        when(track.getNonce(any(RskAddress.class))).thenReturn(nonce);

        when(executorFactory.newInstance(
                any(Transaction.class),
                eq(0),
                eq(coinbase),
                eq(track),
                eq(block),
                eq(0L),
                eq(precompiledContracts)
        )).thenReturn(transactionExecutor);

        when(transactionExecutor.setLocalCall(true)).thenReturn(transactionExecutor);

        ReversibleTransactionExecutor executor = new ReversibleTransactionExecutor(
                        mock(co.rsk.db.RepositoryLocator.class),
                        executorFactory,
                        precompiledContracts);

        executor.executeTransactionOnSnapshot(snapshot, block, coinbase, precompiledContracts, params);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);

        verify(executorFactory).newInstance(txCaptor.capture(), eq(0), eq(coinbase), eq(track), eq(block), eq(0L), eq(precompiledContracts));
        return txCaptor.getValue();
    }

    private static ReversibleTransactionExecutor.ReversibleTransactionParams params(
            TransactionType type,
            byte[] accessList,
            List<SetCodeAuthorization> authorizationList,
            byte[] maxPriorityFeePerGas,
            byte[] maxFeePerGas,
            byte[] gasPrice) {

        return new ReversibleTransactionExecutor.ReversibleTransactionParams(
                gasPrice,
                BigInteger.valueOf(500_000).toByteArray(),
                TestUtils.generateAddress("to").getBytes(),
                new byte[]{0},
                new byte[0],
                TestUtils.generateAddress("from"),
                authorizationList,
                (byte) 33,
                type,
                accessList,
                maxPriorityFeePerGas,
                maxFeePerGas
        );
    }
}
