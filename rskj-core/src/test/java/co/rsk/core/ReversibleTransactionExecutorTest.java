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
import org.bouncycastle.util.encoders.Hex;

import org.ethereum.util.ByteUtil; 
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ContractRunner;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class ReversibleTransactionExecutorTest {

    private final RskTestFactory factory = new RskTestFactory();
    private final ContractRunner contractRunner = new ContractRunner(factory);
    private final ReversibleTransactionExecutor reversibleTransactionExecutor = factory.getReversibleTransactionExecutor();

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

        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new String[]{"chinchilla"},
                helloFn.decodeResult(result.getHReturn()));
    }

    @Test
    public void executeTransactionGreeter() {
        TestContract greeter = TestContract.greeter();
        CallTransaction.Function greeterFn = greeter.functions.get("greet");

        ProgramResult result = contractRunner.createAndRunContract(
                Hex.decode(greeter.bytecode),
                greeterFn.encode("greet me"),
                BigInteger.ZERO,
                true
        );

        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new String[]{"greet me"},
                greeterFn.decodeResult(result.getHReturn()));
        
        //#mish createandrun already prints similar output from TX executor
        //dispRes(result); 
    }

    @Test
    public void executeTransactionGreeterOtherSender() {
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

        Assert.assertTrue(result.isRevert());

        dispRes(result);
    }

    @Test
    public void executeTransactionCountCallsMultipleTimes() {
        TestContract countcalls = TestContract.countcalls();
        CallTransaction.Function callsFn = countcalls.functions.get("calls");
        RskAddress contractAddress = contractRunner.addContract(countcalls.runtimeBytecode);

        RskAddress from = new RskAddress("0000000000000000000000000000000000000023"); // someone else
        byte[] gasPrice = Hex.decode("00");
        byte[] value = Hex.decode("00");
        //#mish increase it from 62500, else it OOGs because we split gas 50:50 into exec and rent gas
        byte[] gasLimit = ByteUtil.longToBytes(100_000L);//Hex.decode("f424"); 

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

        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new String[]{"calls: 1"},
                callsFn.decodeResult(result.getHReturn()));
        
        dispRes(result);        

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

        Assert.assertNull(result2.getException());
        Assert.assertArrayEquals(
                new String[]{"calls: 1"},
                callsFn.decodeResult(result2.getHReturn()));
        
        dispRes(result2);
    }

    // #mish added for storage rent testing to compare results with RPC estimategas
    // this test does not use assertions for rent gas, since rent computations are time dependent.
    // instead used std output.
    // the rentgas consumed also depends on repository access (the following accounts have not really been created, 
    // so the rent will be based on node valuelength being 0 (Zero!) + 128 bytes overhead)
    @Test
    public void sendWithDataRentTest() {
        //TestContract hello = TestContract.hello();
        //CallTransaction.Function helloFn = hello.functions.get("hello");
        //RskAddress contractAddress = contractRunner.addContract(hello.runtimeBytecode);

        RskAddress from = new RskAddress("7986b3df570230288501eea3d890bd66948c9b79"); //TestUtils.randomAddress();
        RskAddress receiver = new RskAddress("d46e8dd67c5d32be8058bb8eb970870f07244567"); //TestUtils.randomAddress();
        byte[] gasPrice = Hex.decode("01"); //"09184e72a000"
        byte[] value = Hex.decode("9184e72a");
        byte[] gasLimit = Hex.decode("e000");
        byte[] txdata = Hex.decode("d46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675");

        Block bestBlock = factory.getBlockchain().getBestBlock();

        ProgramResult result = reversibleTransactionExecutor.executeTransaction(
                bestBlock,
                bestBlock.getCoinbase(),
                gasPrice,
                gasLimit,
                receiver.getBytes(),
                value,
                txdata,
                from
        );
        
        Assert.assertEquals(result.getGasUsed(), 23788L); // execution gas use should be consistent
        
        dispRes(result);
    }



    // #mish just a utility. Not needed when using contract.createandrun    
    public void dispRes(ProgramResult result){
            System.out.println("\nReversible TX summary " + 
                           "\nExec Gas Used " + result.getGasUsed() +
                           "\nRent Gas Used " + result.getRentGasUsed() +
                           "\n\nTx fees (exec + rent): " + (result.getGasUsed()+result.getRentGasUsed()) +
                           "\n\nNo. trie nodes with `updated` rent timestamp: " +  result.getAccessedNodes().size() +
                           "\nNo. new trie nodes created (6 months rent): " +  result.getCreatedNodes().size() + "\n"
                            );
    } 
}
