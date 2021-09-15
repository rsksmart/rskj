package co.rsk.rpc.modules.eth;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.test.World;
import co.rsk.test.dsl.DslProcessorException;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.EthModuleUtils;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.program.InternalTransaction;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class EthModuleGasEstimationDSLTest {

    public static final long BLOCK_GAS_LIMIT = new TestSystemProperties().getTargetGasLimit();
    private ProgramResult localCallResult;
    private static final Logger LOGGER_FEDE = LoggerFactory.getLogger("fede");

    @Test
    public void testEstimateGas_basicTests() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/basicTests.txt");

        EthModule eth = EthModuleUtils.buildBasicEthModule(world);
        Block block = world.getBlockChain().getBestBlock();

        final CallArguments args = new CallArguments();
        args.setTo("6252703f5ba322ec64d3ac45e56241b7d9e481ad"); // some address;
        args.setValue(TypeConverter.toQuantityJsonHex(0)); // no value
        args.setNonce(TypeConverter.toQuantityJsonHex(0));
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData(""); // no data

        long estimatedGas = estimateGas(eth, args);

        ProgramResult callConstantResult = eth.callConstant(args, block);

        assertEquals(callConstantResult.getGasUsed(), estimatedGas);

        // Call same transaction with estimated gas
        args.setGas(TypeConverter.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas - 1
        try {
            args.setGas(TypeConverter.toQuantityJsonHex(estimatedGas - 1));
            runWithArgumentsAndBlock(eth, args, block);
            fail("shouldn't reach here");
        } catch (GasCost.InvalidGasException e) {
            assertEquals("Got invalid gas value, tried operation: 20999 - 21000", e.getMessage());
        }

        // Try to estimate with not enough gas
        try {
            args.setGas(TypeConverter.toQuantityJsonHex(1000));
            estimateGas(eth, args);
            fail("shouldn't reach here");
        } catch (GasCost.InvalidGasException e) {
            assertEquals("Got invalid gas value, tried operation: 1000 - 21000", e.getMessage());
        }
    }

    @Test
    public void testEstimateGas_contractCallsWithValueTransfer() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/callWithValue.txt");

        // Deploy Check
        TransactionReceipt deployTransactionReceipt = world.getTransactionReceiptByName("tx01");
        byte[] status = deployTransactionReceipt.getStatus();
        RskAddress contractAddress = deployTransactionReceipt.getTransaction().getContractAddress();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);
        assertEquals("6252703f5ba322ec64d3ac45e56241b7d9e481ad", contractAddress.toHexString());

        TransactionReceipt callWithValueReceipt = world.getTransactionReceiptByName("tx02");
        byte[] status2 = callWithValueReceipt.getStatus();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);

        // Call with value estimation
        EthModule eth = EthModuleUtils.buildBasicEthModule(world);

        final CallArguments args = new CallArguments();
        args.setTo(contractAddress.toHexString());
        args.setData("c3cefd36"); // callWithValue()
        args.setValue(TypeConverter.toQuantityJsonHex(10000)); // some value
        args.setNonce(TypeConverter.toQuantityJsonHex(3));
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));

        Block block = world.getBlockChain().getBlockByNumber(2); // block 2 contains 0 tx

        // Evaluate the gas used
        long gasUsed = eth.callConstant(args, block).getGasUsed();
        assertEquals(ByteUtil.byteArrayToLong(callWithValueReceipt.getGasUsed()), gasUsed);

        // Estimate the gas to use
        long estimatedGas = estimateGas(eth, args);
        ProgramResult estimationResult = eth.getEstimationResult();
        assertEquals(0, estimationResult.getDeductedRefund());

        // The estimated gas should be greater than the gas used in the call
        assertTrue(gasUsed < estimatedGas);

        // Call same transaction with estimatedGas - 1, should fail
        args.setGas(TypeConverter.toQuantityJsonHex(gasUsed));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas
        args.setGas(TypeConverter.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas
        args.setGas(TypeConverter.toQuantityJsonHex(estimatedGas - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    @Test
    public void testEstimateGas_storageRefunds() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/updateStorage.txt");

        TransactionReceipt deployTransactionReceipt = world.getTransactionReceiptByName("tx01");
        String contractAddress = deployTransactionReceipt.getTransaction().getContractAddress().toHexString();
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

        EthModule eth = EthModuleUtils.buildBasicEthModule(world);
        Block block = world.getBlockChain().getBestBlock();

        // from non-zero to zero - setValue(1, 0) - it should have a refund
        final CallArguments args = new CallArguments();
        args.setTo(contractAddress); // "6252703f5ba322ec64d3ac45e56241b7d9e481ad";
        args.setValue(TypeConverter.toQuantityJsonHex(0));
        args.setNonce(TypeConverter.toQuantityJsonHex(1));
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("7b8d56e3" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000000"); // setValue(1,0)

        ProgramResult callConstantResult = eth.callConstant(args, block);

        long clearStorageGasUsed = callConstantResult.getGasUsed();
        long clearStoreageEstimatedGas = estimateGas(eth, args);

        assertTrue( 0 < clearStorageGasUsed && clearStorageGasUsed < initStorageGasUsed);
        assertTrue(clearStoreageEstimatedGas < initStorageGasUsed);
        assertTrue(clearStoreageEstimatedGas > clearStorageGasUsed);
        assertEquals(clearStoreageEstimatedGas,
                clearStorageGasUsed + callConstantResult.getDeductedRefund());

        // Call same transaction with estimated gas
        args.setGas(TypeConverter.toQuantityJsonHex(clearStoreageEstimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas minus 1
        args.setGas(TypeConverter.toQuantityJsonHex(clearStoreageEstimatedGas - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        // estimate gas for updating a storage cell from non-zero to non-zero
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("7b8d56e3" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000001"); // setValue(1,1)
        long updateStorageGasUsed = eth.callConstant(args, block).getGasUsed();
        long updateStoreageEstimatedGas = estimateGas(eth, args);

        // The estimated gas should be less than the gas used gas for initializing a storage cell
        assertTrue(updateStorageGasUsed < initStorageGasUsed);
        assertTrue(updateStoreageEstimatedGas < initStorageGasUsed);
        assertEquals(updateStoreageEstimatedGas, updateStorageGasUsed);

        // Call same transaction with estimated gas
        args.setGas(Long.toString(updateStoreageEstimatedGas, 16));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas minus 1
        args.setGas(Long.toString(updateStoreageEstimatedGas - 1, 16));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        // Check against another already initialized (2,42) storage cell
        TransactionReceipt anotherInitStorageTransactionReceipt = world.getTransactionReceiptByName("tx02");
        byte[] status3 = anotherInitStorageTransactionReceipt.getStatus();
        long anotherInitStorageGasUsed = new BigInteger(1, anotherInitStorageTransactionReceipt.getGasUsed()).longValue();

        assertNotNull(status3);
        assertEquals(1, status3.length);
        assertEquals(0x01, status3[0]);

        // Change this storage cell to zero and compare
        args.setData("7b8d56e3" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "0000000000000000000000000000000000000000000000000000000000000000");
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));

        ProgramResult anotherCallConstantResult = eth.callConstant(args, block);
        long anotherClearStorageGasUsed = anotherCallConstantResult.getGasUsed();
        long anotherClearStorageEstimatedGas = estimateGas(eth, args);

        assertEquals(initStorageGasUsed, anotherInitStorageGasUsed);
        assertEquals(clearStoreageEstimatedGas, anotherClearStorageEstimatedGas);
        assertEquals(clearStorageGasUsed, anotherClearStorageGasUsed);
    }

    @Test @Ignore
    public void estimateGas_gasCap() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/gasCap.txt");

        TransactionReceipt deployTransactionReceipt = world.getTransactionReceiptByName("tx01");
        String sender = deployTransactionReceipt.getTransaction().getSender().toHexString();
        String contractAddress = deployTransactionReceipt.getTransaction().getContractAddress().toHexString();
        byte[] status = deployTransactionReceipt.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);

        EthModule eth = EthModuleUtils.buildBasicEthModule(world);
        long gasEstimationCap = new TestSystemProperties().getGasEstimationCap();

        CallArguments callArguments = new CallArguments();
        callArguments.setFrom(sender); // the creator
        callArguments.setTo(contractAddress);  // deployed contract
        callArguments.setGas(TypeConverter.toQuantityJsonHex(gasEstimationCap + 1000000000)); // exceeding the gas cap
        callArguments.setData("31fe52e8"); // call outOfGas()

        String estimatedGas = eth.estimateGas(callArguments);

        assertEquals(gasEstimationCap, Long.decode(estimatedGas).longValue());
    }

    /**
     * A contract call containing one storage refund + one call with value
     * */
    @Test
    public void estimateGas_callWithValuePlusSStoreRefund() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/callWithValuePlusSstoreRefundRefactor.txt");

        TransactionReceipt contractDeployReceipt = world.getTransactionReceiptByName("tx01");
        String contractAddress = contractDeployReceipt.getTransaction().getContractAddress().toHexString();
        byte[] status = contractDeployReceipt.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);

        EthModule eth = EthModuleUtils.buildBasicEthModule(world);
        Block block = world.getBlockChain().getBlockByNumber(1);

        // call clearStorageAndSendValue, it should estimate correctly the stipend cost and the gas refund
        final CallArguments args = new CallArguments();
        args.setTo(contractAddress);
        args.setValue(TypeConverter.toQuantityJsonHex(1));
        args.setNonce(TypeConverter.toQuantityJsonHex(1));
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("5b3f8140"); // clearStorageAndSendValue()

        // todo it'd be nice to test if a callConstant consumes exact the same gas amount than a regular call
        ProgramResult callConstant = eth.callConstant(args, block);
        long callConstantGasUsed = callConstant.getGasUsed();

        long estimatedGas = estimateGas(eth, args);
        assertTrue(estimatedGas > callConstantGasUsed);
        assertEquals(callConstant.getMaxGasUsed(), estimatedGas);

        args.setGas(TypeConverter.toQuantityJsonHex(callConstantGasUsed));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(TypeConverter.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(TypeConverter.toQuantityJsonHex(estimatedGas - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    /**
     * Sending one rBTC across three contracts, they will perfomrm 3 CALLs with value.
     * NOTE: each nested call retains 10000 gas to emit events
     */
    @Test
    public void estimateGas_nestedCallsWithValueAndGasRetain() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/nestedCallsWithValue.txt");

        TransactionReceipt contractDeployA = world.getTransactionReceiptByName("tx01");
        String contractAddressA = contractDeployA.getTransaction().getContractAddress().toHexString();
        byte[] status = contractDeployA.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);
        assertEquals("6252703f5ba322ec64d3ac45e56241b7d9e481ad", contractAddressA);

        TransactionReceipt contractDeployB = world.getTransactionReceiptByName("tx02");
        String contractAddressB = contractDeployB.getTransaction().getContractAddress().toHexString();
        byte[] status2 = contractDeployB.getStatus();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);
        assertEquals("56aa252dd82173789984fa164ee26ce2da9336ff", contractAddressB);

        TransactionReceipt contractDeployC = world.getTransactionReceiptByName("tx03");
        String contractAddressC = contractDeployC.getTransaction().getContractAddress().toHexString();
        byte[] status3 = contractDeployC.getStatus();

        assertNotNull(status3);
        assertEquals(1, status3.length);
        assertEquals(0x01, status3[0]);
        assertEquals("27444fbce96cb2d27b94e116d1506d7739c05862", contractAddressC);

        EthModule eth = EthModuleUtils.buildBasicEthModule(world);
        Block block = world.getBlockChain().getBestBlock();

        // call callAddressWithValue, it should start the nested calls
        final CallArguments args = new CallArguments();
        args.setTo(contractAddressA);
        args.setValue(TypeConverter.toQuantityJsonHex(1));
        args.setNonce(TypeConverter.toQuantityJsonHex(6));
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("fb60f709"); // callAddressWithValue()

        ProgramResult callConstant = eth.callConstant(args, block);
        List<InternalTransaction> internalTransactions = callConstant.getInternalTransactions();

        assertTrue(internalTransactions.stream().allMatch(i -> i.getValue().equals(Coin.valueOf(1))));
        assertEquals(2, internalTransactions.size());
        assertEquals(3, callConstant.getLogInfoList().size());
        assertEvents(callConstant, "NestedCallWV", 2);
        assertEvents(callConstant, "LastCall", 1);

        long callConstantGasUsed = callConstant.getGasUsed();

        LOGGER_FEDE.error("---------------------START ESTIMATE--------------------");
        long estimatedGas = estimateGas(eth, args);
        LOGGER_FEDE.error("---------------------END ESTIMATE--------------------");
        assertEquals(callConstant.getGasUsed(), estimatedGas);

        args.setGas(TypeConverter.toQuantityJsonHex(callConstantGasUsed));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        assertEquals(callConstantGasUsed, estimatedGas);

        args.setGas(TypeConverter.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(TypeConverter.toQuantityJsonHex(estimatedGas - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    /**
     * Send 1 rBTC accross three contracts, then the last contract frees a storage cell and does a CALL with value
     * */
    @Test
    public void estimateGas_nestedCallsWithValueGasRetainAndStorageRefund() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/nestedCallsWithValueAndStorageRefund.txt");

        TransactionReceipt contractDeployA = world.getTransactionReceiptByName("tx01");
        String contractAddressA = contractDeployA.getTransaction().getContractAddress().toHexString();
        byte[] status = contractDeployA.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);
        assertEquals("6252703f5ba322ec64d3ac45e56241b7d9e481ad", contractAddressA);

        TransactionReceipt contractDeployB = world.getTransactionReceiptByName("tx02");
        String contractAddressB = contractDeployB.getTransaction().getContractAddress().toHexString();
        byte[] status2 = contractDeployB.getStatus();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);
        assertEquals("56aa252dd82173789984fa164ee26ce2da9336ff", contractAddressB);

        TransactionReceipt contractDeployC = world.getTransactionReceiptByName("tx03");
        String contractAddressC = contractDeployC.getTransaction().getContractAddress().toHexString();
        byte[] status3 = contractDeployC.getStatus();

        assertNotNull(status3);
        assertEquals(1, status3.length);
        assertEquals(0x01, status3[0]);
        assertEquals("27444fbce96cb2d27b94e116d1506d7739c05862", contractAddressC);

        EthModule eth = EthModuleUtils.buildBasicEthModule(world);
        Block block = world.getBlockChain().getBestBlock();

        // call callAddressWithValue, it should start the nested calls
        final CallArguments args = new CallArguments();
        args.setTo(contractAddressA);
        args.setValue(TypeConverter.toQuantityJsonHex(1));
        args.setNonce(TypeConverter.toQuantityJsonHex(6));
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("fb60f709"); // callAddressWithValue()

        ProgramResult callConstant = eth.callConstant(args, block);
        List<InternalTransaction> internalTransactions = callConstant.getInternalTransactions();

        assertTrue(internalTransactions.stream().allMatch(i -> i.getValue().equals(Coin.valueOf(1))));
        assertEquals(3, internalTransactions.size());
        assertEquals(3, callConstant.getLogInfoList().size());
        assertEvents(callConstant, "NestedCallWV", 2);
        assertEvents(callConstant, "LastCall", 1);


        long callConstantGasUsed = callConstant.getGasUsed();

        LOGGER_FEDE.error("---------------------START ESTIMATE--------------------");
        long estimatedGas = estimateGas(eth, args);
        LOGGER_FEDE.error("---------------------END ESTIMATE--------------------");
        assertTrue(callConstant.getDeductedRefund() > 0);
        assertEquals(callConstantGasUsed + callConstant.getDeductedRefund(), estimatedGas);

        args.setGas(TypeConverter.toQuantityJsonHex(callConstantGasUsed));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(TypeConverter.toQuantityJsonHex(estimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        args.setGas(TypeConverter.toQuantityJsonHex(estimatedGas - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    public boolean runWithArgumentsAndBlock(EthModule ethModule, CallArguments args, Block block) {
        localCallResult = ethModule.callConstant(args, block);

        return localCallResult.getException() == null;
    }

    private long estimateGas(EthModule eth, CallArguments args) {
        return Long.parseLong(eth.estimateGas(args).substring("0x".length()), 16);
    }

    // todo this is duplicated code, should be extracted into a test util
    /**
     * Checks how many times an event is contained on a receipt
     * */
    public void assertEvents(ProgramResult programResult, String eventSignature, int times) {
        Stream<String> events = programResult.getLogInfoList().stream().map(logInfo -> eventSignature(logInfo));
        List<String> eventsSignature = events.filter(event -> isExpectedEventSignature(event, eventSignature,  new String[0]))
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
