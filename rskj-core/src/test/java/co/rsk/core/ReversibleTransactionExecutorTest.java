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

import co.rsk.util.TestContract;
import java.math.BigInteger;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ContractRunner;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Assert;
import org.junit.Test;

public class ReversibleTransactionExecutorTest {

    private final RskTestFactory factory = new RskTestFactory();
    private final ContractRunner contractRunner = new ContractRunner(factory);
    private final ReversibleTransactionExecutor reversibleTransactionExecutor =
            factory.getReversibleTransactionExecutor();

    @Test
    public void executeTransactionHello() {
        TestContract hello = TestContract.hello();
        CallTransaction.Function helloFn = hello.functions.get("hello");
        RskAddress contractAddress = contractRunner.addContract(hello.runtimeBytecode);

        RskAddress from = TestUtils.randomAddress();
        byte[] gasPrice = Hex.decode("00");
        byte[] value = Hex.decode("00");
        byte[] gasLimit = Hex.decode("f424");

        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result =
                reversibleTransactionExecutor.executeTransaction(
                        bestBlock,
                        bestBlock.getCoinbase(),
                        gasPrice,
                        gasLimit,
                        contractAddress.getBytes(),
                        value,
                        helloFn.encode(),
                        from);

        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new String[] {"chinchilla"}, helloFn.decodeResult(result.getHReturn()));
    }

    @Test
    public void executeTransactionGreeter() {
        TestContract greeter = TestContract.greeter();
        CallTransaction.Function greeterFn = greeter.functions.get("greet");

        ProgramResult result =
                contractRunner.createAndRunContract(
                        Hex.decode(greeter.bytecode),
                        greeterFn.encode("greet me"),
                        BigInteger.ZERO,
                        true);

        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new String[] {"greet me"}, greeterFn.decodeResult(result.getHReturn()));
    }

    @Test
    public void executeTransactionGreeterOtherSender() {
        TestContract greeter = TestContract.greeter();
        CallTransaction.Function greeterFn = greeter.functions.get("greet");
        RskAddress contractAddress = contractRunner.addContract(greeter.runtimeBytecode);

        RskAddress from =
                new RskAddress("0000000000000000000000000000000000000023"); // someone else
        byte[] gasPrice = Hex.decode("00");
        byte[] value = Hex.decode("00");
        byte[] gasLimit = Hex.decode("f424");

        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result =
                reversibleTransactionExecutor.executeTransaction(
                        bestBlock,
                        bestBlock.getCoinbase(),
                        gasPrice,
                        gasLimit,
                        contractAddress.getBytes(),
                        value,
                        greeterFn.encode("greet me"),
                        from);

        Assert.assertTrue(result.isRevert());
    }

    @Test
    public void executeTransactionCountCallsMultipleTimes() {
        TestContract countcalls = TestContract.countcalls();
        CallTransaction.Function callsFn = countcalls.functions.get("calls");
        RskAddress contractAddress = contractRunner.addContract(countcalls.runtimeBytecode);

        RskAddress from =
                new RskAddress("0000000000000000000000000000000000000023"); // someone else
        byte[] gasPrice = Hex.decode("00");
        byte[] value = Hex.decode("00");
        byte[] gasLimit = Hex.decode("f424");

        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result =
                reversibleTransactionExecutor.executeTransaction(
                        bestBlock,
                        bestBlock.getCoinbase(),
                        gasPrice,
                        gasLimit,
                        contractAddress.getBytes(),
                        value,
                        callsFn.encodeSignature(),
                        from);

        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new String[] {"calls: 1"}, callsFn.decodeResult(result.getHReturn()));

        ProgramResult result2 =
                reversibleTransactionExecutor.executeTransaction(
                        bestBlock,
                        bestBlock.getCoinbase(),
                        gasPrice,
                        gasLimit,
                        contractAddress.getBytes(),
                        value,
                        callsFn.encodeSignature(),
                        from);

        Assert.assertNull(result2.getException());
        Assert.assertArrayEquals(
                new String[] {"calls: 1"}, callsFn.decodeResult(result2.getHReturn()));
    }
}
