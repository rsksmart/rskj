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

package co.rsk.rpc.modules.eth;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.test.World;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.util.HexUtils;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.EthModuleTestUtils;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.program.InternalTransaction;
import org.ethereum.vm.program.ProgramResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EthModuleGasEstimationDSLTest {

    private static final long BLOCK_GAS_LIMIT = new TestSystemProperties().getTargetGasLimit();

    @Test
    void testEstimateGas_basicTests() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/basicTests.txt");

        EthModuleTestUtils.EthModuleGasEstimation eth = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
        Block block = world.getBlockChain().getBestBlock();

        final CallArguments args = new CallArguments();
        args.setTo("0x6252703f5ba322ec64d3ac45e56241b7d9e481ad"); // some address;
        args.setValue(HexUtils.toQuantityJsonHex(0)); // no value
        args.setNonce(HexUtils.toQuantityJsonHex(0));
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData(""); // no data

        long estimatedGas = estimateGas(eth, args);
        assertEquals(21000, estimatedGas);

        assertEquals(0, eth.getEstimationResult().getDeductedRefund());

        ProgramResult callConstantResult = eth.callConstant(args, block);

        assertEquals(callConstantResult.getGasUsed(), estimatedGas);

        // Call same transaction with estimated gas
        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas - 1
        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas - 1));
        Exception e = Assertions.assertThrows(GasCost.InvalidGasException.class, () -> runWithArgumentsAndBlock(eth, args, block));
        assertEquals("Got invalid gas value, tried operation: 20999 - 21000", e.getMessage());

        // Try to estimate with not enough gas
        args.setGas(HexUtils.toQuantityJsonHex(1000));
        e = Assertions.assertThrows(GasCost.InvalidGasException.class, () -> estimateGas(eth, args));
        assertEquals("Got invalid gas value, tried operation: 1000 - 21000", e.getMessage());
    }

    /**
     * A contract with an internal CALL with value transfer, it should take into account the STIPEND_CALL amount
     */
    @Test
    void testEstimateGas_contractCallsWithValueTransfer() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/callWithValue.txt");

        // Deploy Check
        TransactionReceipt deployTransactionReceipt = world.getTransactionReceiptByName("tx01");
        byte[] status = deployTransactionReceipt.getStatus();
        RskAddress contractAddress = deployTransactionReceipt.getTransaction().getContractAddress();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);
        assertEquals("0x6252703f5ba322ec64d3ac45e56241b7d9e481ad", "0x" + contractAddress.toHexString());

        TransactionReceipt callWithValueReceipt = world.getTransactionReceiptByName("tx02");
        byte[] status2 = callWithValueReceipt.getStatus();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);

        // Call with value estimation
        EthModuleTestUtils.EthModuleGasEstimation eth = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);

        final CallArguments args = new CallArguments();
        args.setTo("0x" + contractAddress.toHexString());
        args.setData("0xc3cefd36"); // callWithValue()
        args.setValue(HexUtils.toQuantityJsonHex(10_000)); // some value
        args.setNonce(HexUtils.toQuantityJsonHex(3));
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));

        Block block = world.getBlockChain().getBlockByNumber(2); // block 2 contains 0 tx

        // Evaluate the gas used
        ProgramResult callConstant = eth.callConstant(args, block);
        long gasUsed = callConstant.getGasUsed();
        assertEquals(ByteUtil.byteArrayToLong(callWithValueReceipt.getGasUsed()), gasUsed);
        assertFalse(callConstant.getMovedRemainingGasToChild()); // it just moved STIPEND_CALL (2300) to child

        // Estimate the gas to use
        long estimatedGas = estimateGas(eth, args);
        assertEquals(35728, estimatedGas);

        assertEquals(0, eth.getEstimationResult().getDeductedRefund());

        // The estimated gas should be greater than the gas used in the call
        assertTrue(gasUsed < estimatedGas);

        // Call same transaction with estimatedGas - 1, should fail
        args.setGas(HexUtils.toQuantityJsonHex(gasUsed));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas
        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas
        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas - GasCost.STIPEND_CALL - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    /**
     * A contract with already initialized storage cells, the estimation should take into account the storage refunds
     */
    @Test
    @SuppressWarnings("squid:S5961")
    void testEstimateGas_storageRefunds() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/updateStorage.txt");

        TransactionReceipt deployTransactionReceipt = world.getTransactionReceiptByName("tx01");
        String contractAddress = "0x" + deployTransactionReceipt.getTransaction().getContractAddress().toHexString();
        byte[] status = deployTransactionReceipt.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);

        TransactionReceipt initStorageTransactionReceipt = world.getTransactionReceiptByName("tx02");
        byte[] status2 = initStorageTransactionReceipt.getStatus();
        long initStorageGasUsed = new BigInteger(1, initStorageTransactionReceipt.getGasUsed()).longValue();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);

        EthModuleTestUtils.EthModuleGasEstimation eth = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
        Block block = world.getBlockChain().getBestBlock();

        // from non-zero to zero - setValue(1, 0) - it should have a refund
        final CallArguments args = new CallArguments();
        args.setTo(contractAddress); // "6252703f5ba322ec64d3ac45e56241b7d9e481ad";
        args.setValue(HexUtils.toQuantityJsonHex(0));
        args.setNonce(HexUtils.toQuantityJsonHex(1));
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("0x7b8d56e3" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000000"); // setValue(1,0)

        ProgramResult callConstantResult = eth.callConstant(args, block);

        long clearStorageGasUsed = callConstantResult.getGasUsed();
        long clearStoreageEstimatedGas = estimateGas(eth, args);
        assertEquals(26909, clearStoreageEstimatedGas);

        assertTrue(eth.getEstimationResult().getDeductedRefund() > 0);

        assertTrue(0 < clearStorageGasUsed && clearStorageGasUsed < initStorageGasUsed);
        assertTrue(clearStoreageEstimatedGas < initStorageGasUsed);
        assertTrue(clearStoreageEstimatedGas > clearStorageGasUsed);
        assertEquals(clearStoreageEstimatedGas,
                clearStorageGasUsed + callConstantResult.getDeductedRefund());

        // Call same transaction with estimated gas
        args.setGas(HexUtils.toQuantityJsonHex(clearStoreageEstimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas minus 1
        args.setGas(HexUtils.toQuantityJsonHex(clearStoreageEstimatedGas - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        // estimate gas for updating a storage cell from non-zero to non-zero
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("0x7b8d56e3" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000001"); // setValue(1,1)
        long updateStorageGasUsed = eth.callConstant(args, block).getGasUsed();
        long updateStoreageEstimatedGas = estimateGas(eth, args);
        assertEquals(26973, updateStoreageEstimatedGas);

        assertEquals(0, eth.getEstimationResult().getDeductedRefund());

        // The estimated gas should be less than the gas used gas for initializing a storage cell
        assertTrue(updateStorageGasUsed < initStorageGasUsed);
        assertTrue(updateStoreageEstimatedGas < initStorageGasUsed);
        assertEquals(updateStoreageEstimatedGas, updateStorageGasUsed);

        // Call same transaction with estimated gas
        args.setGas("0x" + Long.toString(updateStoreageEstimatedGas, 16));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas minus 1
        args.setGas("0x" + Long.toString(updateStoreageEstimatedGas - 1, 16));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        // Check against another already initialized (2,42) storage cell
        TransactionReceipt anotherInitStorageTransactionReceipt = world.getTransactionReceiptByName("tx02");
        byte[] status3 = anotherInitStorageTransactionReceipt.getStatus();
        long anotherInitStorageGasUsed = new BigInteger(1, anotherInitStorageTransactionReceipt.getGasUsed()).longValue();

        assertNotNull(status3);
        assertEquals(1, status3.length);
        assertEquals(0x01, status3[0]);

        // Change this storage cell to zero and compare
        args.setData("0x7b8d56e3" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "0000000000000000000000000000000000000000000000000000000000000000");
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));

        ProgramResult anotherCallConstantResult = eth.callConstant(args, block);
        long anotherClearStorageGasUsed = anotherCallConstantResult.getGasUsed();
        long anotherClearStorageEstimatedGas = estimateGas(eth, args);
        assertEquals(26909, anotherClearStorageEstimatedGas);

        assertTrue(eth.getEstimationResult().getDeductedRefund() > 0);

        assertEquals(initStorageGasUsed, anotherInitStorageGasUsed);
        assertEquals(clearStoreageEstimatedGas, anotherClearStorageEstimatedGas);
        assertEquals(clearStorageGasUsed, anotherClearStorageGasUsed);
    }

    /**
     * Test if a user can estimate a transaction that exceeds the block limit
     */
    @Test
    void estimateGas_gasCap() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/gasCap.txt");

        TransactionReceipt deployTransactionReceipt = world.getTransactionReceiptByName("tx01");
        String sender = "0x" + deployTransactionReceipt.getTransaction().getSender().toHexString();
        String contractAddress = "0x" + deployTransactionReceipt.getTransaction().getContractAddress().toHexString();
        byte[] status = deployTransactionReceipt.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);

        EthModuleTestUtils.EthModuleGasEstimation eth = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
        long gasEstimationCap = new TestSystemProperties().getGasEstimationCap();

        CallArguments callArguments = new CallArguments();
        callArguments.setFrom(sender); // the creator
        callArguments.setTo(contractAddress);  // deployed contract
        callArguments.setGas(HexUtils.toQuantityJsonHex(gasEstimationCap + 1_000_000_000)); // exceeding the gas cap
        callArguments.setData("0x31fe52e8"); // call outOfGas()

        String estimatedGas = eth.estimateGas(new CallArgumentsParam(callArguments));
        assertEquals("0x67c280", estimatedGas);

        assertEquals(gasEstimationCap, Long.decode(estimatedGas).longValue());
    }

    /**
     * A contract call containing one storage refund + one call with value
     */
    @Test
    void estimateGas_callWithValuePlusSStoreRefund() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/callWithValuePlusSstoreRefund.txt");

        TransactionReceipt contractDeployReceipt = world.getTransactionReceiptByName("tx01");
        String contractAddress = "0x" + contractDeployReceipt.getTransaction().getContractAddress().toHexString();
        byte[] status = contractDeployReceipt.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);

        EthModuleTestUtils.EthModuleGasEstimation eth = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
        Block block = world.getBlockChain().getBlockByNumber(1);

        // call clearStorageAndSendValue, it should estimate correctly the stipend cost and the gas refund
        final CallArguments args = new CallArguments();
        args.setTo(contractAddress);
        args.setValue(HexUtils.toQuantityJsonHex(1));
        args.setNonce(HexUtils.toQuantityJsonHex(1));
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("0x5b3f8140"); // clearStorageAndSendValue()

        ProgramResult callConstant = eth.callConstant(args, block);
        long callConstantGasUsed = callConstant.getGasUsed();

        long estimatedGas = estimateGas(eth, args);
        assertEquals(40771, estimatedGas);

        assertTrue(estimatedGas > callConstantGasUsed);
        assertEquals(callConstant.getMaxGasUsed() + GasCost.STIPEND_CALL, estimatedGas);
        assertFalse(callConstant.getMovedRemainingGasToChild()); // it just moved STIPEND_CALL (2300) to child
        assertTrue(eth.getEstimationResult().getDeductedRefund() > 0);

        args.setGas(HexUtils.toQuantityJsonHex(callConstantGasUsed));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas - GasCost.STIPEND_CALL - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    /**
     * Sending one rBTC across three contracts, they will perform 3 CALLs with value.
     * NOTE: each nested call retains 10000 gas to emit events
     */
    @Test
    void estimateGas_nestedCallsWithValueAndGasRetain() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/nestedCallsWithValue.txt");

        TransactionReceipt contractDeployA = world.getTransactionReceiptByName("tx01");
        String contractAddressA = "0x" + contractDeployA.getTransaction().getContractAddress().toHexString();
        byte[] status = contractDeployA.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);
        assertEquals("0x6252703f5ba322ec64d3ac45e56241b7d9e481ad", contractAddressA);

        TransactionReceipt contractDeployB = world.getTransactionReceiptByName("tx02");
        String contractAddressB = "0x" + contractDeployB.getTransaction().getContractAddress().toHexString();
        byte[] status2 = contractDeployB.getStatus();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);
        assertEquals("0x56aa252dd82173789984fa164ee26ce2da9336ff", contractAddressB);

        TransactionReceipt contractDeployC = world.getTransactionReceiptByName("tx03");
        String contractAddressC = "0x" + contractDeployC.getTransaction().getContractAddress().toHexString();
        byte[] status3 = contractDeployC.getStatus();

        assertNotNull(status3);
        assertEquals(1, status3.length);
        assertEquals(0x01, status3[0]);
        assertEquals("0x27444fbce96cb2d27b94e116d1506d7739c05862", contractAddressC);

        EthModuleTestUtils.EthModuleGasEstimation eth = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
        Block block = world.getBlockChain().getBestBlock();

        // call callAddressWithValue, it should start the nested calls
        final CallArguments args = new CallArguments();
        args.setTo(contractAddressA);
        args.setValue(HexUtils.toQuantityJsonHex(1));
        args.setNonce(HexUtils.toQuantityJsonHex(6));
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("0xfb60f709"); // callAddressWithValue()

        ProgramResult callConstant = eth.callConstant(args, block);
        List<InternalTransaction> internalTransactions = callConstant.getInternalTransactions();

        assertTrue(internalTransactions.stream().allMatch(i -> i.getValue().equals(Coin.valueOf(1))));
        assertEquals(2, internalTransactions.size());
        assertEquals(3, callConstant.getLogInfoList().size());
        assertEvents(callConstant, "NestedCallWV", 2);
        assertEvents(callConstant, "LastCall", 1);
        assertTrue(callConstant.getMovedRemainingGasToChild());

        long callConstantGasUsed = callConstant.getGasUsed();

        long estimatedGas = estimateGas(eth, args);
        assertEquals(48841, estimatedGas);

        assertEquals(0, eth.getEstimationResult().getDeductedRefund());

        assertEquals(callConstant.getGasUsed() + GasCost.STIPEND_CALL, estimatedGas);

        args.setGas(HexUtils.toQuantityJsonHex(callConstantGasUsed));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas - GasCost.STIPEND_CALL - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    /**
     * One contract makes a CALL to the second one without value, which then makes a CALL with value to third one
     * NOTE: subsequent CALL with value should increase gas estimation by stipend of 2300 gas units
     * NOTE 2: sequence if calls:
     * [USER] -call-without-value-> [CONTRUCT #1] -call-without-value-> [CONTRUCT #2] -call-with-value-> [CONTRUCT #3]
     */
    @Test
    void estimateGas_subsequentCallWithValueAndGasStipendCase1() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/subsequentCallWithValueCase1.txt");

        TransactionReceipt contractDeployA = world.getTransactionReceiptByName("tx01");
        String contractAddressA = "0x" + contractDeployA.getTransaction().getContractAddress().toHexString();
        byte[] status = contractDeployA.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);
        assertEquals("0x6252703f5ba322ec64d3ac45e56241b7d9e481ad", contractAddressA);

        TransactionReceipt contractDeployB = world.getTransactionReceiptByName("tx02");
        String contractAddressB = "0x" + contractDeployB.getTransaction().getContractAddress().toHexString();
        byte[] status2 = contractDeployB.getStatus();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);
        assertEquals("0x56aa252dd82173789984fa164ee26ce2da9336ff", contractAddressB);

        TransactionReceipt contractDeployC = world.getTransactionReceiptByName("tx03");
        String contractAddressC = "0x" + contractDeployC.getTransaction().getContractAddress().toHexString();
        byte[] status3 = contractDeployC.getStatus();

        assertNotNull(status3);
        assertEquals(1, status3.length);
        assertEquals(0x01, status3[0]);
        assertEquals("0x27444fbce96cb2d27b94e116d1506d7739c05862", contractAddressC);

        EthModuleTestUtils.EthModuleGasEstimation eth = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
        Block block = world.getBlockChain().getBestBlock();

        // call callNextAddress, it should start the nested calls
        final CallArguments args = new CallArguments();
        args.setTo(contractAddressA);
        args.setValue(HexUtils.toQuantityJsonHex(0));
        args.setNonce(HexUtils.toQuantityJsonHex(7));
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("0x18d3af63"); // callNextAddress()

        ProgramResult callConstant = eth.callConstant(args, block);
        List<InternalTransaction> internalTransactions = callConstant.getInternalTransactions();

        assertEquals(internalTransactions.get(internalTransactions.size() - 1).getValue(), Coin.valueOf(1000));
        assertEquals(2, internalTransactions.size());
        assertEquals(2, callConstant.getLogInfoList().size());
        assertEvents(callConstant, "NestedCallWithoutValue", 1);
        assertEvents(callConstant, "NestedCallWithValue", 1);
        assertTrue(callConstant.getMovedRemainingGasToChild());
        assertTrue(callConstant.isCallWithValuePerformed());

        long estimatedGas = estimateGas(eth, args);
        assertEquals(39459, estimatedGas);

        assertEquals(0, eth.getEstimationResult().getDeductedRefund());

        assertEquals(callConstant.getGasUsed() + GasCost.STIPEND_CALL, estimatedGas);

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas - GasCost.STIPEND_CALL - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    /**
     * One contract makes a CALL to the second one without value, which then makes a CALL with value to third one
     * NOTE: subsequent CALL with value should increase gas estimation by stipend of 2300 gas units
     * NOTE 2: sequence if calls:
     * [USER] -call-without-value-> [CONTRUCT #1] -call-with-value-> [CONTRUCT #2] -call-with-value-> [CONTRUCT #3]
     */
    @Test
    void estimateGas_subsequentCallWithValueAndGasStipendCase2() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/subsequentCallWithValueCase2.txt");

        TransactionReceipt contractDeployA = world.getTransactionReceiptByName("tx01");
        String contractAddressA = "0x" + contractDeployA.getTransaction().getContractAddress().toHexString();
        byte[] status = contractDeployA.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);
        assertEquals("0x6252703f5ba322ec64d3ac45e56241b7d9e481ad", contractAddressA);

        TransactionReceipt contractDeployB = world.getTransactionReceiptByName("tx02");
        String contractAddressB = "0x" + contractDeployB.getTransaction().getContractAddress().toHexString();
        byte[] status2 = contractDeployB.getStatus();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);
        assertEquals("0x56aa252dd82173789984fa164ee26ce2da9336ff", contractAddressB);

        TransactionReceipt contractDeployC = world.getTransactionReceiptByName("tx03");
        String contractAddressC = "0x" + contractDeployC.getTransaction().getContractAddress().toHexString();
        byte[] status3 = contractDeployC.getStatus();

        assertNotNull(status3);
        assertEquals(1, status3.length);
        assertEquals(0x01, status3[0]);
        assertEquals("0x27444fbce96cb2d27b94e116d1506d7739c05862", contractAddressC);

        EthModuleTestUtils.EthModuleGasEstimation eth = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
        Block block = world.getBlockChain().getBestBlock();

        // call callNextAddress, it should start the nested calls
        final CallArguments args = new CallArguments();
        args.setTo(contractAddressA);
        args.setValue(HexUtils.toQuantityJsonHex(0));
        args.setNonce(HexUtils.toQuantityJsonHex(9));
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("0x18d3af63"); // callNextAddress()

        ProgramResult callConstant = eth.callConstant(args, block);
        List<InternalTransaction> internalTransactions = callConstant.getInternalTransactions();

        assertEquals(internalTransactions.get(internalTransactions.size() - 1).getValue(), Coin.valueOf(1000));
        assertEquals(2, internalTransactions.size());
        assertEquals(2, callConstant.getLogInfoList().size());
        assertEvents(callConstant, "NestedCallWithoutValue", 0);
        assertEvents(callConstant, "NestedCallWithValue", 2);
        assertTrue(callConstant.getMovedRemainingGasToChild());
        assertTrue(callConstant.isCallWithValuePerformed());

        long estimatedGas = estimateGas(eth, args);
        assertEquals(48674, estimatedGas);

        assertEquals(0, eth.getEstimationResult().getDeductedRefund());

        assertEquals(callConstant.getGasUsed() + GasCost.STIPEND_CALL, estimatedGas);

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas - GasCost.STIPEND_CALL - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    @Test
    void estimateGas_firstCallMoveAllRemainingSecondNot() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/firstCallMoveAllRemainingSecondNot.txt");

        // Deploy Check
        TransactionReceipt deployTransactionReceipt = world.getTransactionReceiptByName("tx01");
        byte[] status = deployTransactionReceipt.getStatus();
        RskAddress contractAddress = deployTransactionReceipt.getTransaction().getContractAddress();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);
        assertEquals("0x6252703f5ba322ec64d3ac45e56241b7d9e481ad", "0x" + contractAddress.toHexString());

        TransactionReceipt callWithValueReceipt = world.getTransactionReceiptByName("tx02");
        byte[] status2 = callWithValueReceipt.getStatus();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);

        // Call with value estimation
        EthModuleTestUtils.EthModuleGasEstimation eth = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);

        final CallArguments args = new CallArguments();
        args.setTo("0x" + contractAddress.toHexString());
        args.setData("0xc3cefd36"); // callWithValue()
        args.setValue(HexUtils.toQuantityJsonHex(10_000)); // some value
        args.setNonce(HexUtils.toQuantityJsonHex(3));
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));

        Block block = world.getBlockChain().getBlockByNumber(2); // block 2 contains 0 tx

        // Evaluate the gas used
        ProgramResult callConstant = eth.callConstant(args, block);
        long gasUsed = callConstant.getGasUsed();
        assertEquals(ByteUtil.byteArrayToLong(callWithValueReceipt.getGasUsed()), gasUsed);

        // Estimate the gas to use
        long estimatedGas = estimateGas(eth, args);
        assertEquals(34996, estimatedGas);

        assertEquals(0, eth.getEstimationResult().getDeductedRefund());

        // The estimated gas should be greater than the gas used in the call
        assertTrue(gasUsed < estimatedGas);

        // Call same transaction with gasUsed (< gasNeeded), should fail
        args.setGas(HexUtils.toQuantityJsonHex(gasUsed));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas should work
        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with less gas than estimated

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas - GasCost.STIPEND_CALL - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    /**
     * Send 1 rBTC accross three contracts, then the last contract frees a storage cell and does a CALL with value
     * NOTE: each nested call retains 10000 gas to emit events
     */
    @Test
    void estimateGas_nestedCallsWithValueGasRetainAndStorageRefund() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/nestedCallsWithValueAndStorageRefund.txt");

        TransactionReceipt contractDeployA = world.getTransactionReceiptByName("tx01");
        String contractAddressA = "0x" + contractDeployA.getTransaction().getContractAddress().toHexString();
        byte[] status = contractDeployA.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);
        assertEquals("0x6252703f5ba322ec64d3ac45e56241b7d9e481ad", contractAddressA);

        TransactionReceipt contractDeployB = world.getTransactionReceiptByName("tx02");
        String contractAddressB = "0x" + contractDeployB.getTransaction().getContractAddress().toHexString();
        byte[] status2 = contractDeployB.getStatus();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);
        assertEquals("0x56aa252dd82173789984fa164ee26ce2da9336ff", contractAddressB);

        TransactionReceipt contractDeployC = world.getTransactionReceiptByName("tx03");
        String contractAddressC = "0x" + contractDeployC.getTransaction().getContractAddress().toHexString();
        byte[] status3 = contractDeployC.getStatus();

        assertNotNull(status3);
        assertEquals(1, status3.length);
        assertEquals(0x01, status3[0]);
        assertEquals("0x27444fbce96cb2d27b94e116d1506d7739c05862", contractAddressC);

        EthModuleTestUtils.EthModuleGasEstimation eth = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
        Block block = world.getBlockChain().getBestBlock();

        // call callAddressWithValue, it should start the nested calls
        final CallArguments args = new CallArguments();
        args.setTo(contractAddressA);
        args.setValue(HexUtils.toQuantityJsonHex(1));
        args.setNonce(HexUtils.toQuantityJsonHex(6));
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("0xfb60f709"); // callAddressWithValue()

        ProgramResult callConstant = eth.callConstant(args, block);
        List<InternalTransaction> internalTransactions = callConstant.getInternalTransactions();

        assertTrue(internalTransactions.stream().allMatch(i -> i.getValue().equals(Coin.valueOf(1))));
        assertEquals(3, internalTransactions.size());
        assertEquals(3, callConstant.getLogInfoList().size());
        assertEvents(callConstant, "NestedCallWV", 2);
        assertEvents(callConstant, "LastCall", 1);
        assertTrue(callConstant.getMovedRemainingGasToChild());


        long callConstantGasUsed = callConstant.getGasUsed();

        long estimatedGas = estimateGas(eth, args);
        assertEquals(84210, estimatedGas);

        assertTrue(eth.getEstimationResult().getDeductedRefund() > 0);

        assertTrue(callConstant.getDeductedRefund() > 0);
        assertEquals(callConstant.getGasUsedBeforeRefunds() / 2, callConstant.getDeductedRefund());
        assertEquals(callConstantGasUsed + callConstant.getDeductedRefund() + GasCost.STIPEND_CALL, estimatedGas);

        args.setGas(HexUtils.toQuantityJsonHex(callConstantGasUsed));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas - GasCost.STIPEND_CALL - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    /**
     * Send 1 rBTC accross three contracts, then the last contract frees a storage cell and does a CALL with value
     * NOTE: this test uses a fixed amount of gas for each internal call
     */
    @Test
    void estimateGas_nestedCallsWithValueFixedGasRetainAndStorageRefund() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/nestedCallsWithValueStorageRefundAndFixedGas.txt");

        TransactionReceipt contractDeployA = world.getTransactionReceiptByName("tx01");
        String contractAddressA = "0x" + contractDeployA.getTransaction().getContractAddress().toHexString();
        byte[] status = contractDeployA.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);
        assertEquals("0x6252703f5ba322ec64d3ac45e56241b7d9e481ad", contractAddressA);

        TransactionReceipt contractDeployB = world.getTransactionReceiptByName("tx02");
        String contractAddressB = "0x" + contractDeployB.getTransaction().getContractAddress().toHexString();
        byte[] status2 = contractDeployB.getStatus();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);
        assertEquals("0x56aa252dd82173789984fa164ee26ce2da9336ff", contractAddressB);

        TransactionReceipt contractDeployC = world.getTransactionReceiptByName("tx03");
        String contractAddressC = "0x" + contractDeployC.getTransaction().getContractAddress().toHexString();
        byte[] status3 = contractDeployC.getStatus();

        assertNotNull(status3);
        assertEquals(1, status3.length);
        assertEquals(0x01, status3[0]);
        assertEquals("0x27444fbce96cb2d27b94e116d1506d7739c05862", contractAddressC);

        EthModuleTestUtils.EthModuleGasEstimation eth = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
        Block block = world.getBlockChain().getBestBlock();

        // call callAddressWithValue, it should start the nested calls
        final CallArguments args = new CallArguments();
        args.setTo(contractAddressA);
        args.setValue(HexUtils.toQuantityJsonHex(1));
        args.setNonce(HexUtils.toQuantityJsonHex(6));
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("0xfb60f709"); // callAddressWithValue()

        ProgramResult callConstant = eth.callConstant(args, block);
        List<InternalTransaction> internalTransactions = callConstant.getInternalTransactions();

        assertTrue(internalTransactions.stream().allMatch(i -> i.getValue().equals(Coin.valueOf(1))));
        assertEquals(3, internalTransactions.size());
        assertEquals(3, callConstant.getLogInfoList().size());
        assertEvents(callConstant, "NestedCallWV", 2);
        assertEvents(callConstant, "LastCall", 1);

        long callConstantGasUsed = callConstant.getGasUsed();

        long estimatedGas = estimateGas(eth, args);
        assertEquals(84200, estimatedGas);

        assertTrue(eth.getEstimationResult().getDeductedRefund() > 0);

        assertTrue(callConstant.getDeductedRefund() > 0);
        assertEquals(callConstant.getGasUsedBeforeRefunds() / 2, callConstant.getDeductedRefund());
        assertEquals(callConstantGasUsed + callConstant.getDeductedRefund() + GasCost.STIPEND_CALL, estimatedGas);
        assertTrue(callConstant.getMovedRemainingGasToChild());

        args.setGas(HexUtils.toQuantityJsonHex(callConstantGasUsed));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas - GasCost.STIPEND_CALL - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    public boolean runWithArgumentsAndBlock(EthModuleTestUtils.EthModuleGasEstimation ethModule, CallArguments args, Block block) {
        ProgramResult localCallResult = ethModule.callConstant(args, block);

        return localCallResult.getException() == null;
    }

    private long estimateGas(EthModuleTestUtils.EthModuleGasEstimation eth, CallArguments args) {
        return Long.parseLong(eth.estimateGas(new CallArgumentsParam(args)).substring("0x".length()), 16);
    }

    // todo this is duplicated code, should be extracted into a test util

    /**
     * Checks how many times an event is contained on a receipt
     */
    public void assertEvents(ProgramResult programResult, String eventSignature, int times) {
        Stream<String> events = programResult.getLogInfoList().stream().map(logInfo -> eventSignature(logInfo));
        List<String> eventsSignature = events.filter(event -> isExpectedEventSignature(event, eventSignature, new String[0]))
                .collect(Collectors.toList());

        assertEquals(times, eventsSignature.size());
    }

    private static String eventSignature(LogInfo logInfo) {
        // The first topic usually consists of the signature
        // (a keccak256 hash) of the name of the event that occurred
        return logInfo.getTopics().get(0).toString();
    }

    private static boolean isExpectedEventSignature(String encodedEvent, String expectedEventSignature, String[] eventTypeParams) {
        CallTransaction.Function fun = CallTransaction.Function.fromSignature(expectedEventSignature, eventTypeParams);
        String encodedExpectedEvent = HashUtil.toPrintableHash(fun.encodeSignatureLong());

        return encodedEvent.equals(encodedExpectedEvent);
    }
}
