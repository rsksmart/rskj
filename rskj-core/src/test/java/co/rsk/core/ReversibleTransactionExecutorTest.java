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
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.db.ContractDetails;
import org.ethereum.util.ContractRunner;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Assert;
import org.junit.Test;
import org.bouncycastle.util.encoders.Hex;

public class ReversibleTransactionExecutorTest {

    private final RskTestFactory factory = new RskTestFactory();
    private final ContractRunner contractRunner = new ContractRunner(factory);
    private final ReversibleTransactionExecutor reversibleTransactionExecutor = factory.getReversibleTransactionExecutor();

    @Test
    public void executeTransactionHello() {
        TestContract hello = TestContract.hello();
        CallTransaction.Function helloFn = hello.functions.get("hello");
        ContractDetails contract = contractRunner.addContract(hello.runtimeBytecode);

        RskAddress from = RskAddress.nullAddress();
        byte[] gasPrice = Hex.decode("00");
        byte[] value = Hex.decode("00");
        byte[] gasLimit = Hex.decode("f424");

        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result = reversibleTransactionExecutor.executeTransaction(
                bestBlock,
                bestBlock.getCoinbase(),
                gasPrice,
                gasLimit,
                contract.getAddress(),
                value,
                helloFn.encode(),
                from.getBytes()
        );

        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new String[]{"chinchilla"},
                helloFn.decodeResult(result.getHReturn()));
    }

    @Test
    public void executeTransactionGreeter() {
        TestContract greeter = TestContract.greeter();
        CallTransaction.Function greeterFn = greeter.functions.get("greet");
        ContractDetails contract = contractRunner.addContract(greeter.runtimeBytecode);

        RskAddress from = RskAddress.nullAddress();
        byte[] gasPrice = Hex.decode("00");
        byte[] value = Hex.decode("00");
        byte[] gasLimit = Hex.decode("f424");

        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result = reversibleTransactionExecutor.executeTransaction(
                bestBlock,
                bestBlock.getCoinbase(),
                gasPrice,
                gasLimit,
                contract.getAddress(),
                value,
                greeterFn.encode("greet me"),
                from.getBytes()
        );

        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new String[]{"greet me"},
                greeterFn.decodeResult(result.getHReturn()));
    }

    @Test
    public void executeTransactionGreeterOtherSender() {
        TestContract greeter = TestContract.greeter();
        CallTransaction.Function greeterFn = greeter.functions.get("greet");
        ContractDetails contract = contractRunner.addContract(greeter.runtimeBytecode);

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
                contract.getAddress(),
                value,
                greeterFn.encode("greet me"),
                from.getBytes()
        );

        Assert.assertTrue(result.isRevert());
    }

    @Test
    public void executeTransactionCountCallsMultipleTimes() {
        TestContract countcalls = TestContract.countcalls();
        CallTransaction.Function callsFn = countcalls.functions.get("calls");
        ContractDetails contract = contractRunner.addContract(countcalls.runtimeBytecode);

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
                contract.getAddress(),
                value,
                callsFn.encodeSignature(),
                from.getBytes()
        );

        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new String[]{"calls: 1"},
                callsFn.decodeResult(result.getHReturn()));

        ProgramResult result2 = reversibleTransactionExecutor.executeTransaction(
                bestBlock,
                bestBlock.getCoinbase(),
                gasPrice,
                gasLimit,
                contract.getAddress(),
                value,
                callsFn.encodeSignature(),
                from.getBytes()
        );

        Assert.assertNull(result2.getException());
        Assert.assertArrayEquals(
                new String[]{"calls: 1"},
                callsFn.decodeResult(result2.getHReturn()));
    }
}
