/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

package co.rsk.vm.opcode;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.util.TransactionReceiptUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransientStorageDslTest {

    @Test
    void testTransientStorageOpcodesExecutionsWithRSKIPActivated() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/tload_tstore_basic_tests.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String mainContractTransientStorageCreationTxName = "txTestTransientStorageContract";
        assertTransactionReceiptWithStatus(world, mainContractTransientStorageCreationTxName, "b01", true);

        String secondaryContractTransientStorageCreationTxName = "txTestTransientStorageOtherContract";
        assertTransactionReceiptWithStatus(world, secondaryContractTransientStorageCreationTxName, "b02", true);

        String checkingOpcodesTxName = "txTestTransientStorageOpCodes";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, checkingOpcodesTxName, "b03", true);
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt, "OK", null));

        String checkingOpcodesTxName2 = "txTestTransientStorageOpCodesOtherValue";
        TransactionReceipt txReceipt2 = assertTransactionReceiptWithStatus(world, checkingOpcodesTxName2, "b04", true);
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt2, "OK", null));
    }

    @Test
    void testTransientStorageOpcodesShareMemorySameTransaction() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/tload_tstore_basic_tests.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String mainContractTransientStorageCreationTxName = "txTestTransientStorageContract";
        assertTransactionReceiptWithStatus(world, mainContractTransientStorageCreationTxName, "b01", true);

        String secondaryContractTransientStorageCreationTxName = "txTestTransientStorageOtherContract";
        assertTransactionReceiptWithStatus(world, secondaryContractTransientStorageCreationTxName, "b02", true);

        String checkingOpcodesTxName = "txTestTransientStorageNestedTransactionShareMemory";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, checkingOpcodesTxName, "b05", true);
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt, "OK", null));
    }

    @Test
    void testTransientStorageOpcodesDoesntShareMemoryFromOtherContract() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/tload_tstore_basic_tests.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String mainContractTransientStorageCreationTxName = "txTestTransientStorageContract";
        assertTransactionReceiptWithStatus(world, mainContractTransientStorageCreationTxName, "b01", true);

        String secondaryContractTransientStorageCreationTxName = "txTestTransientStorageOtherContract";
        assertTransactionReceiptWithStatus(world, secondaryContractTransientStorageCreationTxName, "b02", true);

        String checkingOpcodesTxName = "txTestTransientStorageNestedTransactionOtherContractDoesntShareMemory";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, checkingOpcodesTxName, "b06", true);
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt, "ERROR", new String[]{"bytes32"}));
    }

    @Test
    void testTransientStorageOpcodesExecutionFailsWithRSKIPDeactivated() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip446Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );

        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/tload_tstore_basic_tests.txt");
        World world = new World(rskip446Disabled);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String mainContractTransientStorageCreationTxName = "txTestTransientStorageContract";
        assertTransactionReceiptWithStatus(world, mainContractTransientStorageCreationTxName, "b01", true);

        String secondaryContractTransientStorageCreationTxName = "txTestTransientStorageOtherContract";
        assertTransactionReceiptWithStatus(world, secondaryContractTransientStorageCreationTxName, "b02", true);

        String checkingOpcodesTxName = "txTestTransientStorageOpCodes";
       assertTransactionReceiptWithStatus(world, checkingOpcodesTxName, "b03", false);
    }

    @Test
    void testTransientStorageTestsEip1153BasicScenarios() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/eip1153_basic_tests.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String mainContractTransientStorageCreationTxName = "txTestTransientStorageContract";
        assertTransactionReceiptWithStatus(world, mainContractTransientStorageCreationTxName, "b01", true);

        String txTestTloadAfterSstore = "txTestTloadAfterSstore";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txTestTloadAfterSstore, "b02", true);
        Assertions.assertEquals(4, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txTestTloadAfterTstore = "txTestTloadAfterTstore";
        txReceipt = assertTransactionReceiptWithStatus(world, txTestTloadAfterTstore, "b03", true);
        Assertions.assertEquals(4, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txTestTransientUnsetValues = "txTestTransientUnsetValues";
        txReceipt = assertTransactionReceiptWithStatus(world, txTestTransientUnsetValues, "b04", true);
        Assertions.assertEquals(4, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txTestTloadAfterTstoreIsZero = "txTestTloadAfterTstoreIsZero";
        txReceipt = assertTransactionReceiptWithStatus(world, txTestTloadAfterTstoreIsZero, "b04", true);
        Assertions.assertEquals(4, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testOnlyConstructorCodeCreateContext() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/only_constructor_code_create_context.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTestTransientStorageCreateContextsContract = "txTestTransientStorageCreateContextsContract";
       assertTransactionReceiptWithStatus(world, txTestTransientStorageCreateContextsContract, "b01", true);

        String txOnlyConstructorCode = "txOnlyConstructorCode";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txOnlyConstructorCode, "b02", true);
        Assertions.assertEquals(5, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testInConstructorAndCodeCreateContext() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/in_constructor_and_deploy_code_create_context.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTestTransientStorageCreateContextsContract = "txTestTransientStorageCreateContextsContract";
        assertTransactionReceiptWithStatus(world, txTestTransientStorageCreateContextsContract, "b01", true);

        String txInConstructorAndCode = "txInConstructorAndCode";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txInConstructorAndCode, "b02", true);
        Assertions.assertEquals(6, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testAccrossConstructorAndCodeV0CreateContext() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/accross_constructor_and_deploy_code_v0_create_context.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTestTransientStorageCreateContextsContract = "txTestTransientStorageCreateContextsContract";
        assertTransactionReceiptWithStatus(world, txTestTransientStorageCreateContextsContract, "b01", true);

        String txAcrossConstructorAndCodeV0 = "txAcrossConstructorAndCodeV0";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txAcrossConstructorAndCodeV0, "b02", true);
        Assertions.assertEquals(6, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testAccrossConstructorAndCodeV1CreateContext() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/accross_constructor_and_deploy_code_v1_create_context.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTestTransientStorageCreateContextsContract = "txTestTransientStorageCreateContextsContract";
        assertTransactionReceiptWithStatus(world, txTestTransientStorageCreateContextsContract, "b01", true);

        String txAcrossConstructorAndCodeV1 = "txAcrossConstructorAndCodeV1";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txAcrossConstructorAndCodeV1, "b02", true);
        Assertions.assertEquals(7, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testNoConstructorCodeCreateContext() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/no_constructor_code_create_context.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTestTransientStorageCreateContextsContract = "txTestTransientStorageCreateContextsContract";
        assertTransactionReceiptWithStatus(world, txTestTransientStorageCreateContextsContract, "b01", true);

        String txNoConstructorCode = "txNoConstructorCode";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txNoConstructorCode, "b02", true);
        Assertions.assertEquals(5, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testDynamicExecutionContextSimpleScenario() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/dynamic_execution_context_simple.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txCallAndDelegateCallSimpleTest = "txCallAndDelegateCallSimpleTest";
        assertTransactionReceiptWithStatus(world, txCallAndDelegateCallSimpleTest, "b01", true);

        String txExecuteCallCode = "txExecuteCallCode";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txExecuteCallCode, "b02", true);
        Assertions.assertEquals(5, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txExecuteDelegateCall = "txExecuteDelegateCall";
        txReceipt = assertTransactionReceiptWithStatus(world, txExecuteDelegateCall, "b03", true);
        Assertions.assertEquals(5, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testDynamicExecutionContextWithRevert() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/dynamic_execution_context_with_revert.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txDynamicExecutionContextWithRevertTest = "txDynamicExecutionContextWithRevertTest";
        assertTransactionReceiptWithStatus(world, txDynamicExecutionContextWithRevertTest, "b01", true);

        String txExecuteCallCode = "txExecuteCallCode";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txExecuteCallCode, "b02", true);
        Assertions.assertEquals(3, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txExecuteDelegateCall = "txExecuteDelegateCall";
        txReceipt = assertTransactionReceiptWithStatus(world, txExecuteDelegateCall, "b03", true);
        Assertions.assertEquals(3, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txExecuteCall = "txExecuteCall";
        txReceipt = assertTransactionReceiptWithStatus(world, txExecuteCall, "b04", true);
        Assertions.assertEquals(3, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testDynamicExecutionContextWithInvalid() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/dynamic_execution_context_with_invalid.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txDynamicExecutionContextWithInvalidTest = "txDynamicExecutionContextWithInvalidTest";
        assertTransactionReceiptWithStatus(world, txDynamicExecutionContextWithInvalidTest, "b01", true);

        String txExecuteCallCode = "txExecuteCallCode";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txExecuteCallCode, "b02", true);
        Assertions.assertEquals(3, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txExecuteDelegateCall = "txExecuteDelegateCall";
        txReceipt = assertTransactionReceiptWithStatus(world, txExecuteDelegateCall, "b03", true);
        Assertions.assertEquals(3, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txExecuteCall = "txExecuteCall";
        txReceipt = assertTransactionReceiptWithStatus(world, txExecuteCall, "b04", true);
        Assertions.assertEquals(3, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testDynamicExecutionContextWithStackOverflow() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/dynamic_execution_context_with_stack_overflow.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txDynamicExecutionContextWithStackOverflowTest = "txDynamicExecutionContextWithStackOverflowTest";
        assertTransactionReceiptWithStatus(world, txDynamicExecutionContextWithStackOverflowTest, "b01", true);

        String txExecuteCallCode = "txExecuteCallCode";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txExecuteCallCode, "b02", true);
        Assertions.assertEquals(3, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txExecuteDelegateCall = "txExecuteDelegateCall";
        txReceipt = assertTransactionReceiptWithStatus(world, txExecuteDelegateCall, "b03", true);
        Assertions.assertEquals(3, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txExecuteCall = "txExecuteCall";
        txReceipt = assertTransactionReceiptWithStatus(world, txExecuteCall, "b04", true);
        Assertions.assertEquals(3, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testDynamicExecutionCallContextSubcall() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/dynamic_execution_context_call_subcall.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txContextCallSubcallContract = "txContextCallSubcallContract";
        assertTransactionReceiptWithStatus(world, txContextCallSubcallContract, "b01", true);

        String txExecuteCallCode = "txExecuteCallCode";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txExecuteCallCode, "b02", true);
        Assertions.assertEquals(6, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testDynamicExecutionStaticCallSubcallCantUseTstore() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/dynamic_execution_context_staticcall_subcall_cant_call_tstore.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txContextStaticCallCantCallTstoreContract = "txContextStaticCallCantCallTstoreContract";
        assertTransactionReceiptWithStatus(world, txContextStaticCallCantCallTstoreContract, "b01", true);

        String txExecuteStaticCallCode = "txExecuteStaticCallCode";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txExecuteStaticCallCode, "b02", true);
        Assertions.assertEquals(2, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testDynamicReentrancyContextsTstoreBeforeRevertOrInvalidHasNoEffect() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/dynamic_reentrancy_context_tstore_before_revert_or_invalid_has_no_effect.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTstorageDynamicReentrancyContextContract = "txTstorageDynamicReentrancyContextContract";
        assertTransactionReceiptWithStatus(world, txTstorageDynamicReentrancyContextContract, "b01", true);

        String txTestReentrantContextRevert = "txTestReentrantContextRevert";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txTestReentrantContextRevert, "b02", true);
        Assertions.assertEquals(3, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txTestReentrantContextInvalid = "txTestReentrantContextInvalid";
        assertTransactionReceiptWithStatus(world, txTestReentrantContextInvalid, "b03", false);
    }

    @Test
    void testDynamicReentrancyContextsRevertOrInvalidUndoesAll() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/dynamic_reentrancy_context_revert_or_invalid_undoes_all.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTstorageDynamicReentrancyContextContract = "txTstorageDynamicReentrancyContextContract";
        assertTransactionReceiptWithStatus(world, txTstorageDynamicReentrancyContextContract, "b01", true);

        String txTestReentrantContextRevert = "txTestReentrantContextRevert";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txTestReentrantContextRevert, "b02", true);
        Assertions.assertEquals(5, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txTestReentrantContextInvalid = "txTestReentrantContextInvalid";
        assertTransactionReceiptWithStatus(world, txTestReentrantContextInvalid, "b03", false);
    }

    @Test
    void testDynamicReentrancyContextsRevertOrInvalidUndoesTstorageAfterSuccessfullCall() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/dynamic_reentrancy_context_revert_or_invalid_undoes_tstorage_after_successfull_call.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTstorageDynamicReentrancyContextContract = "txTstorageDynamicReentrancyContextContract";
        assertTransactionReceiptWithStatus(world, txTstorageDynamicReentrancyContextContract, "b01", true);

        String txTstoreInDoubleReentrantCallWithRevert = "txTstoreInDoubleReentrantCallWithRevert";
        assertTransactionReceiptWithStatus(world, txTstoreInDoubleReentrantCallWithRevert, "b02", true);

        String txCheckValuesStoredInTstorageForRevert = "txCheckValuesStoredInTstorageForRevert";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txCheckValuesStoredInTstorageForRevert, "b03", true);
        Assertions.assertEquals(4, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));

        String txTstoreInDoubleReentrantCallWithInvalid = "txTstoreInDoubleReentrantCallWithInvalid";
        assertTransactionReceiptWithStatus(world, txTstoreInDoubleReentrantCallWithInvalid, "b04", false);

        String txCheckValuesStoredInTstorageForInvalid = "txCheckValuesStoredInTstorageForInvalid";
        txReceipt = assertTransactionReceiptWithStatus(world, txCheckValuesStoredInTstorageForInvalid, "b05", true);
        Assertions.assertEquals(4, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testReentrancyContextsTstoreAfterReentrantCall() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/reentrancy_context_tstore_after_reentrant_call.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTstorageReentrancyContextTestContract = "txTstorageReentrancyContextTestContract";
        assertTransactionReceiptWithStatus(world, txTstorageReentrancyContextTestContract, "b01", true);

        String txTstoreInReentrantCall = "txTstoreInReentrantCall";
        assertTransactionReceiptWithStatus(world, txTstoreInReentrantCall, "b02", true);

        String txCheckValuesStoredInTstorage = "txCheckValuesStoredInTstorage";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txCheckValuesStoredInTstorage, "b03", true);
        Assertions.assertEquals(3, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testReentrancyContextsTloadAfterReentrantTstore() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/reentrancy_context_tload_after_reentrant_tstore.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTstorageReentrancyContextTestContract = "txTstorageReentrancyContextTestContract";
        assertTransactionReceiptWithStatus(world, txTstorageReentrancyContextTestContract, "b01", true);

        String txTloadAfterReentrantTstore = "txTloadAfterReentrantTstore";
        assertTransactionReceiptWithStatus(world, txTloadAfterReentrantTstore, "b02", true);

        String txCheckValuesStoredInTstorage = "txCheckValuesStoredInTstorage";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txCheckValuesStoredInTstorage, "b03", true);
        Assertions.assertEquals(3, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testReentrancyContextsManipulateInReentrantCall() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/reentrancy_context_manipulate_in_reentrant_call.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTstorageReentrancyContextTestContract = "txTstorageReentrancyContextTestContract";
        assertTransactionReceiptWithStatus(world, txTstorageReentrancyContextTestContract, "b01", true);

        String txManipulateInReentrantCall = "txManipulateInReentrantCall";
        assertTransactionReceiptWithStatus(world, txManipulateInReentrantCall, "b02", true);

        String txCheckValuesStoredInTstorage = "txCheckValuesStoredInTstorage";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txCheckValuesStoredInTstorage, "b03", true);
        Assertions.assertEquals(4, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testReentrancyContextsTstoreInCallThenTloadReturnInStaticCall() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/reentrancy_context_tstore_in_call_then_tload_return_in_static_call.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTstorageReentrancyContextTestContract = "txTstorageReentrancyContextTestContract";
        assertTransactionReceiptWithStatus(world, txTstorageReentrancyContextTestContract, "b01", true);

        String txTstorageInReentrantCallTest = "txTstorageInReentrantCallTest";
        assertTransactionReceiptWithStatus(world, txTstorageInReentrantCallTest, "b02", true);

        String txCheckValuesStoredInTstorage = "txCheckValuesStoredInTstorage";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txCheckValuesStoredInTstorage, "b03", true);
        Assertions.assertEquals(5, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testTransientStorageGasMeasureTests() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/tstorage_gas_measure_tests.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTstorageGasMeasureTestContract = "txTstorageGasMeasureTestContract";
        assertTransactionReceiptWithStatus(world, txTstorageGasMeasureTestContract, "b01", true);

        String txCheckGasMeasures = "txCheckGasMeasures";
        TransactionReceipt txReceipt  = assertTransactionReceiptWithStatus(world, txCheckGasMeasures, "b02", true);
        Assertions.assertEquals(4, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    @Test
    void testTstoreLoopUntilOutOfGas() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/tstore_loop_until_out_of_gas.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTstoreLoopUntilOutOfGasContract = "txTstoreLoopUntilOutOfGasContract";
        assertTransactionReceiptWithStatus(world, txTstoreLoopUntilOutOfGasContract, "b01", true);

        String txRunTstoreUntilOutOfGas = "txRunTstoreUntilOutOfGas";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txRunTstoreUntilOutOfGas, "b02", false);
        long txRunOutOfGas = new BigInteger(1, txReceipt.getGasUsed()).longValue();
        assertEquals(300000, txRunOutOfGas); // Assert that it consumed all the gas configured in the transaction
    }

    @Test
    void testTstoreWideAddressSpaceLoopUntilOutOfGas() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/tstore_wide_address_space_loop_until_out_of_gas.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTstoreWideAddressSpaceLoopUntilOutOfGasContract = "txTstoreWideAddressSpaceLoopUntilOutOfGasContract";
        assertTransactionReceiptWithStatus(world, txTstoreWideAddressSpaceLoopUntilOutOfGasContract, "b01", true);

        String txRunTstoreWideAddressSpaceUntilOutOfGas = "txRunTstoreWideAddressSpaceUntilOutOfGas";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txRunTstoreWideAddressSpaceUntilOutOfGas, "b02", false);
        long txRunOutOfGas = new BigInteger(1, txReceipt.getGasUsed()).longValue();
        assertEquals(500000, txRunOutOfGas); // Assert that it consumed all the gas configured in the transaction
    }

    @Test
    void testTstoreAndTloadLoopUntilOutOfGas() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/tstore_and_tload_loop_until_out_of_gas.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTstoreAndTloadLoopUntilOutOfGasContract = "txTstoreAndTloadLoopUntilOutOfGasContract";
        assertTransactionReceiptWithStatus(world, txTstoreAndTloadLoopUntilOutOfGasContract, "b01", true);

        String txRunTstoreAndTloadUntilOutOfGas = "txRunTstoreAndTloadUntilOutOfGas";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txRunTstoreAndTloadUntilOutOfGas, "b02", false);
        long txRunOutOfGas = new BigInteger(1, txReceipt.getGasUsed()).longValue();
        assertEquals(700000, txRunOutOfGas); // Assert that it consumed all the gas configured in the transaction
    }

    @ParameterizedTest
    @MethodSource("provideParametersForSelfDestructCases")
    void testTstorageSelfDestructCases_OnEachTest_TheEventsEmittedAreTheExpected(String dslFile, Integer numberOfOksEmitted) throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/" + dslFile);
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txDeployTestContract = "txDeployTestContract";
        assertTransactionReceiptWithStatus(world, txDeployTestContract, "b01", true);

        String txCheckValuesStoredInTstorage = "txPerformTest";
        TransactionReceipt txReceipt = assertTransactionReceiptWithStatus(world, txCheckValuesStoredInTstorage, "b02", true);
        Assertions.assertEquals(numberOfOksEmitted, TransactionReceiptUtil.getEventCount(txReceipt, "OK",  null));
    }

    private static Stream<Arguments> provideParametersForSelfDestructCases() {
        return Stream.of(
                Arguments.of("tload_after_selfdestruct_pre_existing_contract.txt", 3 ),
                Arguments.of("tload_after_selfdestruct_new_contract.txt", 3 ),
                Arguments.of("tload_after_inner_selfdestruct_pre_existing_contract.txt", 2 ),
                Arguments.of("tload_after_inner_selfdestruct_new_contract.txt", 2 ),
                Arguments.of("tstore_after_selfdestruct_pre_existing_contract.txt", 4 ),
                Arguments.of("tstore_after_selfdestruct_new_contract.txt", 4 )
        );
    }

    @Test
    void testTloadTstoreCheckingBetweenTransactions() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transient_storage_rskip446/tload_tstore_checking_between_transactions.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String txTloadTstoreCheckingBetweenTransactionsContract = "txTloadTstoreCheckingBetweenTransactionsContract";
        assertTransactionReceiptWithStatus(world, txTloadTstoreCheckingBetweenTransactionsContract, "b01", true);

        String txTstoreAndTloadSomeValue = "txTstoreAndTloadSomeValue";
        Transaction txCreation = world.getTransactionByName(txTstoreAndTloadSomeValue);
        assertNotNull(txCreation);

        Block block = world.getBlockByName("b02");
        assertEquals(2, block.getTransactionsList().size());

        TransactionReceipt txReceipt = world.getTransactionReceiptByName(txTstoreAndTloadSomeValue);
        assertNotNull(txReceipt);
        byte[] status = txReceipt.getStatus();
        assertNotNull(status);
        assertTrue(txReceipt.isSuccessful());

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt, "ValueStored",  new String[]{"uint256","uint256"}));
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt, "ValueLoaded",  new String[]{"uint256","uint256"}));

        String txTloadSomeValueAndCheckWithExpected = "txTloadSomeValueAndCheckWithExpected";
        TransactionReceipt txReceipt2 = world.getTransactionReceiptByName(txTloadSomeValueAndCheckWithExpected);
        assertNotNull(txReceipt2);
        status = txReceipt2.getStatus();
        assertNotNull(status);
        assertTrue(txReceipt2.isSuccessful());

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt2, "ValueLoaded",    new String[]{"uint256","uint256"}));
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txReceipt2, "OK",  null));
    }

    private static TransactionReceipt assertTransactionReceiptWithStatus(World world, String txName, String blockName, boolean withSuccess) {
        Transaction txCreation = world.getTransactionByName(txName);
        assertNotNull(txCreation);

        Block block = world.getBlockByName(blockName);
        assertEquals(1, block.getTransactionsList().size());

        TransactionReceipt txReceipt = world.getTransactionReceiptByName(txName);
        assertNotNull(txReceipt);

        byte[] status = txReceipt.getStatus();
        assertNotNull(status);

        if(withSuccess) {
            assertTrue(txReceipt.isSuccessful());
        } else {
            assertFalse(txReceipt.isSuccessful());
        }
        return txReceipt;
    }

}
