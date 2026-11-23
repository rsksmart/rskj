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

import co.rsk.core.exception.TransactionExecutionRejectedException;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.util.TestContract;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.util.ContractRunner;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.Program;
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
import java.util.stream.Stream;

import static org.ethereum.config.Constants.REGTEST_CHAIN_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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

        ProgramResult result = reversibleTransactionExecutor.executeTransaction(
                bestBlock,
                bestBlock.getCoinbase(),
                gasPrice,
                gasLimit,
                contractAddress.getBytes(),
                value,
                helloFn.encode(),
                from
        );

        Assertions.assertNull(result.getException());
        Assertions.assertArrayEquals(
                new String[]{"chinchilla"},
                helloFn.decodeResult(result.getHReturn()));
    }

    @Test
    void executeTransaction_contractCallWithGasLimitAboveLongMax_runsTheCall() {
        TestContract hello = TestContract.hello();
        CallTransaction.Function helloFn = hello.functions.get("hello");
        RskAddress contractAddress = contractRunner.addContract(hello.runtimeBytecode);
        RskAddress from = TestUtils.generateAddress("from");
        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result = reversibleTransactionExecutor.executeTransaction(
                bestBlock, bestBlock.getCoinbase(), Hex.decode("00"), Hex.decode("ffffffffffffffff"),
                contractAddress.getBytes(), Hex.decode("00"), helloFn.encode(), from);

        Assertions.assertNull(result.getException());
        Assertions.assertArrayEquals(new String[]{"chinchilla"}, helloFn.decodeResult(result.getHReturn()));
    }

    @Test
    void executeTransaction_directPrecompileCallWithGasLimitAboveLongMax_runsTheCall() {
        RskAddress from = TestUtils.generateAddress("from");
        byte[] identity = Hex.decode(PrecompiledContracts.IDENTITY_ADDR_STR);
        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result = reversibleTransactionExecutor.executeTransaction(
                bestBlock, bestBlock.getCoinbase(), Hex.decode("00"), Hex.decode("ffffffffffffffff"),
                identity, Hex.decode("00"), Hex.decode("010203"), from);

        Assertions.assertNull(result.getException());
        Assertions.assertArrayEquals(Hex.decode("010203"), result.getHReturn());
    }

    @Test
    void executeTransaction_valueTransferWithGasLimitAboveLongMax_runsTheCall() {
        RskAddress from = TestUtils.generateAddress("from");
        RskAddress to = TestUtils.generateAddress("to");
        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result = reversibleTransactionExecutor.executeTransaction(
                bestBlock, bestBlock.getCoinbase(), Hex.decode("00"), Hex.decode("ffffffffffffffff"),
                to.getBytes(), Hex.decode("01"), null, from);

        Assertions.assertNull(result.getException());
        Assertions.assertArrayEquals(new byte[0], result.getHReturn());
    }

    /**
     * The contract 5a60005260206000f3 is GAS, PUSH1 0, MSTORE, PUSH1 0x20, PUSH1 0, RETURN: it returns the gas left
     * when GAS runs. Any gas limit of 2^63 or more must run with exactly Long.MAX_VALUE.
     * Expected word, by hand from the gas schedule:
     * 0x7fffffffffffffff (Long.MAX_VALUE)
     * - 21000 (intrinsic cost of a call with no data, GasCost.TRANSACTION)
     * - 2 (GAS opcode, base tier, spent before it reads the remaining gas)
     * = 0x7fffffffffffffff - 0x520a = 0x7fffffffffffadf5.
     */
    @ParameterizedTest
    @MethodSource("gasLimitsAtOrAboveTwoToThe63")
    void executeTransaction_gasLimitAtOrAboveTwoToThe63_runsWithLongMaxGas(String gasLimitHex) {
        RskAddress contractAddress = contractRunner.addContract("0x5a60005260206000f3");
        RskAddress from = TestUtils.generateAddress("from");
        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result = reversibleTransactionExecutor.executeTransaction(
                bestBlock, bestBlock.getCoinbase(), Hex.decode("00"), Hex.decode(gasLimitHex),
                contractAddress.getBytes(), Hex.decode("00"), null, from);

        Assertions.assertNull(result.getException());
        Assertions.assertArrayEquals(
                Hex.decode("0000000000000000000000000000000000000000000000007fffffffffffadf5"),
                result.getHReturn());
    }

    /**
     * A gas limit wider than 8 bytes is read by value. The identity precompile with 3 bytes of input costs
     * 21,000 + 3 * 16 (intrinsic, non-zero data bytes) + 15 + 3 (one word) = 21,066 gas.
     * 0x5238 is 21,048, so the call runs out of gas.
     */
    @Test
    void executeTransaction_zeroPaddedGasLimitBelowPrecompileCost_runsOutOfGas() {
        RskAddress from = TestUtils.generateAddress("from");
        byte[] identity = Hex.decode(PrecompiledContracts.IDENTITY_ADDR_STR);
        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result = reversibleTransactionExecutor.executeTransaction(
                bestBlock, bestBlock.getCoinbase(), Hex.decode("00"), Hex.decode("000000000000000000005238"),
                identity, Hex.decode("00"), Hex.decode("010203"), from);

        Assertions.assertInstanceOf(Program.OutOfGasException.class, result.getException());
    }

    private static Stream<String> gasLimitsAtOrAboveTwoToThe63() {
        return Stream.of("8000000000000000", "ffffffffffffffff", "010000000000000000");
    }

    /**
     * Intrinsic gas: 21,000 for a call, 53,000 for a create, plus 4 for a zero data byte and 2 for one RSKIP438
     * init-code word.
     */
    @ParameterizedTest
    @MethodSource("localCallsBelowIntrinsicGas")
    void executeTransaction_gasBelowIntrinsicCost_isRejectedWithMessage(byte[] to, byte[] data, long intrinsic) {
        RskAddress from = TestUtils.generateAddress("from");
        Block bestBlock = factory.getBlockchain().getBestBlock();
        RskAddress coinbase = bestBlock.getCoinbase();
        byte[] gasLimit = Hex.decode("4e20"); // 20,000

        TransactionExecutionRejectedException ex = Assertions.assertThrows(TransactionExecutionRejectedException.class,
                () -> reversibleTransactionExecutor.executeTransaction(
                        bestBlock, coinbase, Hex.decode("00"), gasLimit, to, Hex.decode("00"), data, from));

        Assertions.assertEquals("Not enough gas for transaction execution: tx needs: " + intrinsic + " tx sent: 20000",
                ex.getMessage());
    }

    private static Stream<Arguments> localCallsBelowIntrinsicGas() {
        byte[] eoa = TestUtils.generateAddress("eoa").getBytes();
        byte[] remasc = Hex.decode(PrecompiledContracts.REMASC_ADDR_STR);
        return Stream.of(
                Arguments.of(eoa, new byte[0], 21000L),
                Arguments.of(remasc, new byte[0], 21000L),
                Arguments.of(null, new byte[0], 53000L),
                Arguments.of(null, new byte[]{0}, 53006L)
        );
    }

    @ParameterizedTest
    @MethodSource("localCallsBelowIntrinsicGas")
    void estimateGas_gasBelowIntrinsicCost_isRejectedWithMessage(byte[] to, byte[] data, long intrinsic) {
        RskAddress from = TestUtils.generateAddress("from");
        Block bestBlock = factory.getBlockchain().getBestBlock();
        RskAddress coinbase = bestBlock.getCoinbase();
        RepositorySnapshot snapshot = factory.getRepositoryLocator().snapshotAt(bestBlock.getHeader());
        byte[] gasLimit = Hex.decode("4e20"); // 20,000

        TransactionExecutionRejectedException ex = Assertions.assertThrows(TransactionExecutionRejectedException.class,
                () -> reversibleTransactionExecutor.estimateGas(
                        bestBlock, coinbase, Hex.decode("00"), gasLimit, to, Hex.decode("00"), data, from, snapshot));

        Assertions.assertEquals("Not enough gas for transaction execution: tx needs: " + intrinsic + " tx sent: 20000",
                ex.getMessage());
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

        Assertions.assertNull(result.getException());
        Assertions.assertArrayEquals(
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

        ProgramResult result = reversibleTransactionExecutor.executeTransaction(
                bestBlock,
                bestBlock.getCoinbase(),
                gasPrice,
                gasLimit,
                contractAddress.getBytes(),
                value,
                greeterFn.encode("greet me"),
                from
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

        ProgramResult result = reversibleTransactionExecutor.executeTransaction(
                bestBlock,
                bestBlock.getCoinbase(),
                gasPrice,
                gasLimit,
                contractAddress.getBytes(),
                value,
                callsFn.encodeSignature(),
                from
        );

        Assertions.assertNull(result.getException());
        Assertions.assertArrayEquals(
                new String[]{"calls: 1"},
                callsFn.decodeResult(result.getHReturn()));

        ProgramResult result2 = reversibleTransactionExecutor.executeTransaction(
                bestBlock,
                bestBlock.getCoinbase(),
                gasPrice,
                gasLimit,
                contractAddress.getBytes(),
                value,
                callsFn.encodeSignature(),
                from
        );

        Assertions.assertNull(result2.getException());
        Assertions.assertArrayEquals(
                new String[]{"calls: 1"},
                callsFn.decodeResult(result2.getHReturn()));
    }

    @Test
    void executedTransaction_isUnsignedAndPassesBothSignaturePredicates() {
        RskAddress from = TestUtils.generateAddress("from");
        Repository track = mock(Repository.class);
        when(track.getNonce(from)).thenReturn(BigInteger.ZERO);
        RepositorySnapshot snapshot = mock(RepositorySnapshot.class);
        when(snapshot.startTracking()).thenReturn(track);
        TransactionExecutor executor = mock(TransactionExecutor.class);
        when(executor.setLocalCall(true)).thenReturn(executor);
        when(executor.executeTransaction()).thenReturn(true);
        TransactionExecutorFactory executorFactory = mock(TransactionExecutorFactory.class);
        when(executorFactory.newInstance(any(), anyInt(), any(), any(), any(), anyLong(), any())).thenReturn(executor);
        Block block = mock(Block.class);
        byte[] gasPrice = Hex.decode("00");
        byte[] gasLimit = Hex.decode("f424");
        byte[] toAddress = TestUtils.generateAddress("to").getBytes();
        byte[] value = Hex.decode("00");
        byte[] data = new byte[0];

        new ReversibleTransactionExecutor(mock(RepositoryLocator.class), executorFactory).estimateGas(
                block, RskAddress.nullAddress(), gasPrice, gasLimit, toAddress, value, data, from, snapshot);

        ArgumentCaptor<Transaction> executed = ArgumentCaptor.forClass(Transaction.class);
        verify(executorFactory).newInstance(executed.capture(), eq(0), any(), eq(track), eq(block), eq(0L), any());
        Transaction tx = executed.getValue();
        Assertions.assertNull(tx.getSignature());
        Assertions.assertTrue(tx.hasAcceptableSignatureComponents());
        Assertions.assertTrue(tx.acceptTransactionSignature(REGTEST_CHAIN_ID));
    }
}
