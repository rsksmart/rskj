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
import org.ethereum.core.genesis.BlockTag;
import org.ethereum.crypto.HashUtil;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.EthModuleTestUtils;
import org.ethereum.util.TransactionFactoryHelper;
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

        long estimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
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
        e = Assertions.assertThrows(GasCost.InvalidGasException.class, () -> estimateGas(eth, args, BlockTag.LATEST.getTag()));
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
        long estimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        assertEquals(35520, estimatedGas);

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
        long clearStorageEstimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        assertEquals(26649, clearStorageEstimatedGas);

        assertTrue(eth.getEstimationResult().getDeductedRefund() > 0);

        assertTrue(0 < clearStorageGasUsed && clearStorageGasUsed < initStorageGasUsed);
        assertTrue(clearStorageEstimatedGas < initStorageGasUsed);
        assertTrue(clearStorageEstimatedGas > clearStorageGasUsed);
        assertEquals(clearStorageEstimatedGas,
                clearStorageGasUsed + callConstantResult.getDeductedRefund());

        // Call same transaction with estimated gas
        args.setGas(HexUtils.toQuantityJsonHex(clearStorageEstimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas minus 1
        args.setGas(HexUtils.toQuantityJsonHex(clearStorageEstimatedGas - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        // estimate gas for updating a storage cell from non-zero to non-zero
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("0x7b8d56e3" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000001"); // setValue(1,1)
        long updateStorageGasUsed = eth.callConstant(args, block).getGasUsed();
        long updateStorageEstimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        assertEquals(26661, updateStorageEstimatedGas);

        assertEquals(0, eth.getEstimationResult().getDeductedRefund());

        // The estimated gas should be less than the gas used gas for initializing a storage cell
        assertTrue(updateStorageGasUsed < initStorageGasUsed);
        assertTrue(updateStorageEstimatedGas < initStorageGasUsed);
        assertEquals(updateStorageEstimatedGas, updateStorageGasUsed);

        // Call same transaction with estimated gas
        args.setGas("0x" + Long.toString(updateStorageEstimatedGas, 16));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas minus 1
        args.setGas("0x" + Long.toString(updateStorageEstimatedGas - 1, 16));
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
        long anotherClearStorageEstimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        assertEquals(26649, anotherClearStorageEstimatedGas);

        assertTrue(eth.getEstimationResult().getDeductedRefund() > 0);

        assertEquals(initStorageGasUsed, anotherInitStorageGasUsed);
        assertEquals(clearStorageEstimatedGas, anotherClearStorageEstimatedGas);
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

        String estimatedGas = eth.estimateGas(TransactionFactoryHelper.toCallArgumentsParam(callArguments), new BlockIdentifierParam(BlockTag.LATEST.getTag()));
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

        long estimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        assertEquals(40563, estimatedGas);

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

        long estimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        assertEquals(48633, estimatedGas);

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

        long estimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        assertEquals(39251, estimatedGas);

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

        long estimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        assertEquals(48466, estimatedGas);

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
        long estimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        assertEquals(34788, estimatedGas);

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

        long estimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        assertEquals(84002, estimatedGas);

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

        long estimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        assertEquals(83992, estimatedGas);

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

    /**
     * Regression test motivated by the eth_estimateGas under-counting that broke
     * an OZ ERC1967Proxy deployment on RSK testnet (tx
     * 0xddb735039288b9b8e08c9929e26577866e9a0f38edf8c61f01da69e827face96).
     *
     * Captures the shape of the failing path: a contract-creation transaction
     * whose constructor delegate-calls an initializer that performs many
     * SSTOREs and clears one slot to produce an SSTORE_CLEAR refund — i.e. the
     * "movedRemainingGasToChild=true AND deductedRefund>0" branch of
     * EthModule.internalEstimateGas (rskj-core/src/main/java/co/rsk/rpc/modules/eth/EthModule.java:269).
     *
     * The contract here:
     *   - asserts movedRemainingGasToChild is set on the outer simulation,
     *   - asserts deductedRefund is non-zero,
     *   - asserts that estimateGas() and estimateGas() * 1.3 are both viable as
     *     the transaction's gasLimit (no OOG when the tx is replayed).
     *
     * Today this passes on master. If the estimator is later rewritten to do
     * binary search à la geth's eth/gasestimator, this test must keep passing —
     * any regression that makes the returned estimate too small for the
     * proxy+initializer pattern will be caught here. Reproducing the exact
     * testnet failure (estimate < required) likely requires the deployed
     * implementation's exact bytecode and state; that variant should be added
     * separately if/when the underlying root cause is reproduced in isolation.
     */
    @Test
    void testEstimateGas_proxyConstructorDelegatecallToInitializer()
            throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld(
                "dsl/eth_module/estimateGas/proxyDelegatecallInitializer.txt");

        TransactionReceipt implReceipt = world.getTransactionReceiptByName("tx01");
        assertNotNull(implReceipt.getStatus());
        assertEquals(1, implReceipt.getStatus().length);
        assertEquals(0x01, implReceipt.getStatus()[0]);
        String implAddr = "0x" + implReceipt.getTransaction()
                .getContractAddress().toHexString();

        EthModuleTestUtils.EthModuleGasEstimation eth =
                EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
        Block block = world.getBlockChain().getBestBlock();

        // Build initialize() calldata. The function takes 8 statics + a
        // uint256[] (20 entries to drive many SSTOREs against the proxy's
        // freshly-allocated storage) + a bytes blob.
        CallTransaction.Function initFn = CallTransaction.Function.fromSignature(
                "initialize",
                "address", "address", "address",
                "uint256", "uint256", "uint256", "uint256", "uint256",
                "uint256[]", "bytes"
        );
        BigInteger[] extras = new BigInteger[20];
        for (int i = 0; i < extras.length; i++) {
            extras[i] = BigInteger.valueOf(10L + i);
        }
        byte[] initData = initFn.encode(
                "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222",
                "0x3333333333333333333333333333333333333333",
                BigInteger.valueOf(1), BigInteger.valueOf(2), BigInteger.valueOf(3),
                BigInteger.valueOf(4), BigInteger.valueOf(5),
                extras,
                new byte[64]);

        // Wrap (impl, initData) as ABI-encoded constructor args for the proxy.
        CallTransaction.Function proxyCtorEncoder = CallTransaction.Function
                .fromSignature("dummy", "address", "bytes");
        byte[] ctorArgs = proxyCtorEncoder.encodeArguments(implAddr, initData);

        String proxyDeployData = "0x" + PROXY_CREATION_BYTECODE
                + ByteUtil.toHexString(ctorArgs);

        CallArguments args = new CallArguments();
        args.setFrom("0x" + world.getAccountByName("acc1")
                .getAddress().toHexString());
        args.setTo(null); // contract creation
        args.setValue(HexUtils.toQuantityJsonHex(0));
        args.setNonce(HexUtils.toQuantityJsonHex(1));
        args.setData(proxyDeployData);
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));

        long estimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        ProgramResult sim = eth.getEstimationResult();

        // Diagnostics — printed so a regression failure is easy to read.
        System.out.println("[estimateGas regression] estimatedGas=" + estimatedGas
                + " gasUsed=" + sim.getGasUsed()
                + " maxGasUsed=" + sim.getMaxGasUsed()
                + " deductedRefund=" + sim.getDeductedRefund()
                + " movedRemainingGasToChild=" + sim.getMovedRemainingGasToChild()
                + " exception=" + sim.getException());

        assertNull(sim.getException(), "simulation must succeed under the cap");
        assertTrue(sim.getMovedRemainingGasToChild(),
                "outer constructor's delegatecall should have forwarded gas() to the initializer");
        assertTrue(sim.getDeductedRefund() > 0,
                "initializer must produce an SSTORE refund so the gasUsed+refund branch is exercised");
        assertEquals(sim.getGasUsed() + sim.getDeductedRefund(), estimatedGas,
                "current formula: estimate equals gasUsed (net) + deductedRefund when movedRemainingGasToChild is true");

        // Submitting with gasLimit == estimateGas() must not OOG.
        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block),
                "tx with gasLimit == estimateGas() should not OOG");

        // Mirrors Foundry's default: estimateGas() * 1.3 must comfortably succeed.
        long bufferedGas = (long) Math.ceil(estimatedGas * 1.3);
        args.setGas(HexUtils.toQuantityJsonHex(bufferedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block),
                "tx with gasLimit == estimateGas() * 1.3 should not OOG");

        // Submitting with one less than the estimate must fail — proves the
        // estimate is the *minimum* viable gasLimit, not just a comfortable upper bound.
        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block),
                "tx with gasLimit < estimateGas() should OOG (estimate must be tight)");
    }

    /**
     * Reproduces the failed RSK testnet transaction that motivated this work
     * (tx 0xddb735039288b9b8e08c9929e26577866e9a0f38edf8c61f01da69e827face96).
     * The caller deployed an ERC1967Proxy whose constructor delegate-called
     * {@code initializeStreams(...)}. The submitted gasLimit was 961_818
     * (Foundry's estimateGas() * 1.3) and the tx OOG'd at SWAP2.
     *
     * The bug, captured by this test: when the caller passes a {@code gas}
     * hint to eth_estimateGas that is smaller than the actual gas required,
     * the simulation runs against that tight budget, OOGs silently
     * (sim.getException() = OutOfGasException), and gasUsed is set to the
     * entire budget via spendAllGas. EthModule.internalEstimateGas
     * (rskj-core/src/main/java/co/rsk/rpc/modules/eth/EthModule.java:268)
     * never checks {@code sim.getException()} and returns
     * {@code gasUsed + deductedRefund} = budget. The caller takes that as
     * the "estimate", multiplies by 1.3, and submits — and the real tx
     * OOGs because (budget * 1.3) is still below the true minimum.
     *
     * This test sweeps a range of caller gas hints and asserts that the
     * estimator never returns a value that OOGs on replay. It currently
     * fails on master and will pass once EthModule.estimateGas treats the
     * caller's gas as a *cap on the result*, not a budget for the
     * simulation — or detects OutOfGasException in the simulation result
     * and bumps the budget / signals an error.
     *
     * The fixture deploys the implementation's exact runtime bytecode
     * (fetched from testnet via eth_getCode at
     * 0x0b75fc65eda9ded22f774f3c7045b52024959eb3) wrapped in a fake init
     * code, since OZ upgradeable implementations don't depend on their own
     * constructor running. The proxy creation calldata is taken verbatim
     * from the failed tx, with the implementation address patched to
     * point at the fixture's deployed copy.
     */
    @Test
    void testEstimateGas_proxyConstructorDelegatecallToInitializer_onchainBytecode()
            throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld(
                "dsl/eth_module/estimateGas/proxyDelegatecallInitializerOnchainBytecode.txt");

        TransactionReceipt implReceipt = world.getTransactionReceiptByName("tx01");
        assertNotNull(implReceipt.getStatus());
        assertEquals(1, implReceipt.getStatus().length);
        assertEquals(0x01, implReceipt.getStatus()[0],
                "implementation runtime must deploy successfully");
        String implAddrHex = implReceipt.getTransaction()
                .getContractAddress().toHexString().toLowerCase();

        // Patch testnet impl address (0x0b75fc...eb3) to point at our deployed copy.
        String onchainImpl = "0b75fc65eda9ded22f774f3c7045b52024959eb3";
        assertEquals(onchainImpl.length(), implAddrHex.length(),
                "address widths must match for the patch");
        String patchedTxInput = JEREMY_PROXY_TX_INPUT.replace(onchainImpl, implAddrHex);
        assertNotEquals(JEREMY_PROXY_TX_INPUT, patchedTxInput,
                "patch must have replaced the testnet impl address");

        EthModuleTestUtils.EthModuleGasEstimation eth =
                EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
        Block block = world.getBlockChain().getBestBlock();

        CallArguments args = new CallArguments();
        args.setFrom("0x" + world.getAccountByName("acc1")
                .getAddress().toHexString());
        args.setTo(null);
        args.setValue(HexUtils.toQuantityJsonHex(0));
        args.setNonce(HexUtils.toQuantityJsonHex(1));
        args.setData("0x" + patchedTxInput);
        args.setGas(HexUtils.toQuantityJsonHex(BLOCK_GAS_LIMIT));

        long estimatedGas = estimateGas(eth, args, BlockTag.LATEST.getTag());
        ProgramResult sim = eth.getEstimationResult();

        System.out.println("[estimateGas onchain bytecode, gasArg=blockLimit] "
                + "estimatedGas=" + estimatedGas
                + " gasUsed=" + sim.getGasUsed()
                + " maxGasUsed=" + sim.getMaxGasUsed()
                + " deductedRefund=" + sim.getDeductedRefund()
                + " movedRemainingGasToChild=" + sim.getMovedRemainingGasToChild()
                + " exception=" + sim.getException());

        // Probe with the default (block-limit) budget: this must succeed.
        args.setGas(HexUtils.toQuantityJsonHex(estimatedGas));
        boolean okAtEstimate = runWithArgumentsAndBlock(eth, args, block);
        long bufferedGas = (long) Math.ceil(estimatedGas * 1.3);
        args.setGas(HexUtils.toQuantityJsonHex(bufferedGas));
        boolean okAt130Pct = runWithArgumentsAndBlock(eth, args, block);
        System.out.println("[estimateGas onchain bytecode] okAtEstimate=" + okAtEstimate
                + " okAt130Pct=" + okAt130Pct);
        assertNull(sim.getException(), "simulation must succeed");
        assertTrue(okAtEstimate, "tx with gasLimit == estimateGas() must not OOG");
        assertTrue(okAt130Pct, "tx with gasLimit == estimateGas() * 1.3 must not OOG");

        // Now probe what Foundry actually does: it can pass a tighter `gas`
        // hint to eth_estimateGas. Walk a range of caller-provided budgets and
        // record whether the returned estimate is a viable gasLimit.
        long[] callerHints = new long[]{
                BLOCK_GAS_LIMIT, 3_000_000L, 1_500_000L,
                1_100_000L, 1_030_362L, 961_818L, 800_000L, 740_168L
        };
        long minViable = Long.MAX_VALUE;
        long maxNonViable = 0;
        for (long hint : callerHints) {
            args.setGas(HexUtils.toQuantityJsonHex(hint));
            try {
                long est = estimateGas(eth, args, BlockTag.LATEST.getTag());
                ProgramResult r = eth.getEstimationResult();
                args.setGas(HexUtils.toQuantityJsonHex(est));
                boolean replayOk = runWithArgumentsAndBlock(eth, args, block);
                System.out.println(String.format(
                        "[estimateGas hint sweep] hint=%d -> estimate=%d "
                                + "(gasUsed=%d maxGasUsed=%d refund=%d moved=%s simException=%s) replayOk=%s",
                        hint, est, r.getGasUsed(), r.getMaxGasUsed(),
                        r.getDeductedRefund(), r.getMovedRemainingGasToChild(),
                        r.getException() == null ? "null"
                                : r.getException().getClass().getSimpleName(),
                        replayOk));
                if (replayOk) {
                    minViable = Math.min(minViable, est);
                } else {
                    maxNonViable = Math.max(maxNonViable, est);
                }
            } catch (Exception e) {
                System.out.println("[estimateGas hint sweep] hint=" + hint
                        + " threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        System.out.println("[estimateGas hint sweep] minViable=" + minViable
                + " maxNonViable=" + maxNonViable);
        assertEquals(0L, maxNonViable,
                "no caller-provided gas hint should make estimateGas return a value that OOGs on replay");
    }

    /**
     * Verbatim {@code input} field of testnet tx
     * 0xddb735039288b9b8e08c9929e26577866e9a0f38edf8c61f01da69e827face96
     * (fetched via eth_getTransactionByHash). The first ~1 KB is the proxy
     * creation bytecode; the trailing ~2 KB are the constructor args
     * (impl address, then ABI-encoded {@code initializeStreams} calldata).
     */
    private static final String JEREMY_PROXY_TX_INPUT =
            "608060405260405161041038038061041083398101604081905261002291610268565b61002c8282610033565b5050610358565b61003c82610092565b6040516001600160a01b038316907fbc7cd75a20ee27fd9adebab32041f755214dbc6bffa90cc0225b39da2e5c2d3b90600090a280511561008657610081828261010e565b505050565b61008e610185565b5050565b806001600160a01b03163b6000036100cd57604051634c9c8ce360e01b81526001600160a01b03821660048201526024015b60405180910390fd5b7f360894a13ba1a3210667c828492db98dca3e2076cc3735a920a3ca505d382bbc80546001600160a01b0319166001600160a01b0392909216919091179055565b6060600080846001600160a01b03168460405161012b919061033c565b600060405180830381855af49150503d8060008114610166576040519150601f19603f3d011682016040523d82523d6000602084013e61016b565b606091505b50909250905061017c8583836101a6565b95945050505050565b34156101a45760405163b398979f60e01b815260040160405180910390fd5b565b6060826101bb576101b682610205565b6101fe565b81511580156101d257506001600160a01b0384163b155b156101fb57604051639996b31560e01b81526001600160a01b03851660048201526024016100c4565b50805b9392505050565b8051156102155780518082602001fd5b60405163d6bda27560e01b815260040160405180910390fd5b634e487b7160e01b600052604160045260246000fd5b60005b8381101561025f578181015183820152602001610247565b50506000910152565b6000806040838503121561027b57600080fd5b82516001600160a01b038116811461029257600080fd5b60208401519092506001600160401b038111156102ae57600080fd5b8301601f810185136102bf57600080fd5b80516001600160401b038111156102d8576102d861022e565b604051601f8201601f19908116603f011681016001600160401b03811182821017156103065761030661022e565b60405281815282820160200187101561031e57600080fd5b61032f826020830160208601610244565b8093505050509250929050565b6000825161034e818460208701610244565b9190910192915050565b60aa806103666000396000f3fe6080604052600a600c565b005b60186014601a565b6051565b565b6000604c7f360894a13ba1a3210667c828492db98dca3e2076cc3735a920a3ca505d382bbc546001600160a01b031690565b905090565b3660008037600080366000845af43d6000803e808015606f573d6000f35b3d6000fdfea2646970667358221220ff10d9aa169b34cc6531f0f540f1b3ef2159a45794f8673d2cdd6ba1d114f5dd64736f6c634300081e00330000000000000000000000000b75fc65eda9ded22f774f3c7045b52024959eb30000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000080446dc62e400000000000000000000000001d0f7225c69a54ce719612b3a787ff61efe7084000000000000000000000000b9e42ac31386aa6b2cc5c5cce391b2e5dae5fae00000000000000000000000003458f034f25bb5ad97d9c99058af260387bb60db00000000000000000000000000000000000000000000000000000000000003200000000000000000000000000000000000000000000000000000000000000064000000000000000000000000000000000000000000000000004fefa17b7240000000000000000000000000000000000000000000000000000008e1bc9bf040000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000000500000000000000000000000000000000000000000000000000000000000186a0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000009600000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000f4240000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000009600000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000989680000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000009600000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000005f5e100000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000009600000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000003b9aca00000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000009600000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000";

    /**
     * Creation bytecode of MinimalProxy.sol (compiled with solc 0.8.20,
     * --optimize --optimize-runs 200). Mirrors OpenZeppelin's ERC1967Proxy:
     * stores the implementation in the EIP-1967 slot, then delegate-calls into
     * it forwarding gas(). See
     * src/test/resources/dsl/eth_module/estimateGas/proxyDelegatecallInitializer.txt
     * for the matching Implementation.sol.
     */
    private static final String PROXY_CREATION_BYTECODE =
            "608060405260405161025e38038061025e833981016040819052610022916100ed565b817f360894a13ba1a3210667c828492db98dca3e2076cc3735a920a3ca505d382bbc555f80836001600160a01b03168360405161005f91906101b5565b5f60405180830381855af49150503d805f8114610097576040519150601f19603f3d011682016040523d82523d5f602084013e61009c565b606091505b5091509150816100ae57805160208201fd5b505050506101d0565b634e487b7160e01b5f52604160045260245ffd5b5f5b838110156100e55781810151838201526020016100cd565b50505f910152565b5f80604083850312156100fe575f80fd5b82516001600160a01b0381168114610114575f80fd5b60208401519092506001600160401b0380821115610130575f80fd5b818501915085601f830112610143575f80fd5b815181811115610155576101556100b7565b604051601f8201601f19908116603f0116810190838211818310171561017d5761017d6100b7565b81604052828152886020848701011115610195575f80fd5b6101a68360208301602088016100cb565b80955050505050509250929050565b5f82516101c68184602087016100cb565b9190910192915050565b6082806101dc5f395ff3fe608060405236600a57005b7f360894a13ba1a3210667c828492db98dca3e2076cc3735a920a3ca505d382bbc8054365f80375f80365f845af490503d5f803e8080156048573d5ff35b3d5ffdfea2646970667358221220c69507ddaa45264afbc0bbb56cf7c588c4482bbf2fe8d71d8fcd32eb3cfc674364736f6c63430008140033";

    public boolean runWithArgumentsAndBlock(EthModuleTestUtils.EthModuleGasEstimation ethModule, CallArguments args, Block block) {
        ProgramResult localCallResult = ethModule.callConstant(args, block);

        return localCallResult.getException() == null;
    }

    private long estimateGas(EthModuleTestUtils.EthModuleGasEstimation eth, CallArguments args, String bnOrId) {
        return Long.parseLong(eth.estimateGas(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam(bnOrId)).substring("0x".length()), 16);
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
