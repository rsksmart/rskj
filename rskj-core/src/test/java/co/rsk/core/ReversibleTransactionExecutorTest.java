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
import org.ethereum.vm.program.ProgramResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.nio.file.Path;

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
