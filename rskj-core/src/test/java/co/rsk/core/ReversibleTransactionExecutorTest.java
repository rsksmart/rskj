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
import org.ethereum.core.TransactionExecutor;
import org.ethereum.db.ContractDetails;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Test;

public class ReversibleTransactionExecutorTest {
    @Test
    public void executeTransactionHello() {
        RskTestFactory factory = new RskTestFactory();
        factory.initGenesis();

        TestContract hello = TestContract.hello();
        CallTransaction.Function helloFn = hello.functions.get("hello");
        ContractDetails contract = factory.addContract(hello.data);

        Web3.CallArguments args = new Web3.CallArguments();
        args.to = TypeConverter.toJsonHex(contract.getAddress());
        args.data = TypeConverter.toJsonHex(helloFn.encode());
        args.gasPrice = "0x0";
        args.value = "0x0";
        args.gas = "0xf424";

        Block bestBlock = factory.getBlockchain().getBestBlock();
        TransactionExecutor executor = ReversibleTransactionExecutor.executeTransaction(
                bestBlock.getCoinbase(),
                factory.getRepository(),
                factory.getBlockStore(),
                factory.getReceiptStore(),
                new ProgramInvokeFactoryImpl(),
                bestBlock,
                args);

        Assert.assertNull(executor.getResult().getException());
        Assert.assertArrayEquals(
                new String[] { "chinchilla" },
                helloFn.decodeResult(executor.getResult().getHReturn()));
    }

    @Test
    public void executeTransactionGreeter() {
        RskTestFactory factory = new RskTestFactory();
        factory.initGenesis();

        TestContract greeter = TestContract.greeter();
        CallTransaction.Function greeterFn = greeter.functions.get("greet");
        ContractDetails contract = factory.addContract(greeter.data);

        Web3.CallArguments args = new Web3.CallArguments();
        args.to = TypeConverter.toJsonHex(contract.getAddress());
        args.data = TypeConverter.toJsonHex(greeterFn.encode("greet me"));
        args.gasPrice = "0x0";
        args.value = "0x0";
        args.gas = "0xf424";

        Block bestBlock = factory.getBlockchain().getBestBlock();
        TransactionExecutor executor = ReversibleTransactionExecutor.executeTransaction(
                bestBlock.getCoinbase(),
                factory.getRepository(),
                factory.getBlockStore(),
                factory.getReceiptStore(),
                new ProgramInvokeFactoryImpl(),
                bestBlock,
                args);

        Assert.assertNull(executor.getResult().getException());
        Assert.assertArrayEquals(
                new String[] { "greet me" },
                greeterFn.decodeResult(executor.getResult().getHReturn()));
    }

    @Test
    public void executeTransactionGreeterOtherSender() {
        RskTestFactory factory = new RskTestFactory();
        factory.initGenesis();

        TestContract greeter = TestContract.greeter();
        CallTransaction.Function greeterFn = greeter.functions.get("greet");
        ContractDetails contract = factory.addContract(greeter.data);

        Web3.CallArguments args = new Web3.CallArguments();
        args.from = "0x23"; // someone else
        args.to = TypeConverter.toJsonHex(contract.getAddress());
        args.data = TypeConverter.toJsonHex(greeterFn.encode("greet me"));
        args.gasPrice = "0x0";
        args.value = "0x0";
        args.gas = "0xf424";

        Block bestBlock = factory.getBlockchain().getBestBlock();
        TransactionExecutor executor = ReversibleTransactionExecutor.executeTransaction(
                bestBlock.getCoinbase(),
                factory.getRepository(),
                factory.getBlockStore(),
                factory.getReceiptStore(),
                new ProgramInvokeFactoryImpl(),
                bestBlock,
                args);

        Assert.assertNotNull(executor.getResult().getException());
        Assert.assertEquals(
                "Invalid operation code: opCode[fd];", // throw with opcode 0xfd, to be changed to revert
                executor.getResult().getException().getMessage());
    }

    @Test
    public void executeTransactionCountCallsMultipleTimes() {
        RskTestFactory factory = new RskTestFactory();
        factory.initGenesis();

        TestContract countcalls = TestContract.countcalls();
        CallTransaction.Function callsFn = countcalls.functions.get("calls");
        ContractDetails contract = factory.addContract(countcalls.data);

        Web3.CallArguments args = new Web3.CallArguments();
        args.from = "0x23"; // someone else
        args.to = TypeConverter.toJsonHex(contract.getAddress());
        args.data = TypeConverter.toJsonHex(callsFn.encodeSignature());
        args.gasPrice = "0x0";
        args.value = "0x0";
        args.gas = "0xf424";

        Block bestBlock = factory.getBlockchain().getBestBlock();
        TransactionExecutor executor = ReversibleTransactionExecutor.executeTransaction(
                bestBlock.getCoinbase(),
                factory.getRepository(),
                factory.getBlockStore(),
                factory.getReceiptStore(),
                new ProgramInvokeFactoryImpl(),
                bestBlock,
                args);

        Assert.assertNull(executor.getResult().getException());
        Assert.assertArrayEquals(
                new String[] { "calls: 1" },
                callsFn.decodeResult(executor.getResult().getHReturn()));

        TransactionExecutor executor2 = ReversibleTransactionExecutor.executeTransaction(
                bestBlock.getCoinbase(),
                factory.getRepository(),
                factory.getBlockStore(),
                factory.getReceiptStore(),
                new ProgramInvokeFactoryImpl(),
                bestBlock,
                args);

        Assert.assertNull(executor2.getResult().getException());
        Assert.assertArrayEquals(
                new String[] { "calls: 1" },
                callsFn.decodeResult(executor2.getResult().getHReturn()));
    }
}
