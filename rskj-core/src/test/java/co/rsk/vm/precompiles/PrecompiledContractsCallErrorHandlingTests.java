package co.rsk.vm.precompiles;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * After Iris hardfork, when a user wants to call a contract from another contract,
 * it has the chance to handle that failed call. Before Iris the whole call was marked as failed without
 * any chance to do some error handling.
 *
 * This tests covers that functionality, focusing only on precompiled contracts. Using a contract to invoke all the existing precompiles
 * with trash data. This will produce different kind of events to verify the expected behaviour.
 *
 * Note: Update this tests when a new precompiled is introduced => add a new transaction to that precompiled
 *
 * Events
 * - PrecompiledSuccess(address) => d1c48ee5d8b9dfbcca9046f456364548ef0b27b0a39faf92aa1c253abf816482
 * - PrecompiledFailure(address) => aa679a624a231df95e2bd73419c633e47abb959a4d3bbfd245a07c036c38202e
 * */
class PrecompiledContractsCallErrorHandlingTests {
    public static final String DSL_PRECOMPILED_CALL_ERROR_HANDLING_TXT = "dsl/contract_call/precompiled_error_handling.txt";

    private World world;

    @Test
    void handleErrorOnFailedPrecompiledContractCall_beforeIris() throws IOException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300", ConfigValueFactory.fromAnyRef(-1))
        );

        DslParser parser = DslParser.fromResource(DSL_PRECOMPILED_CALL_ERROR_HANDLING_TXT);
        world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // RSK precompiles test
        assertTransactionFail("tx02", PrecompiledContracts.BRIDGE_ADDR_STR);
        assertTransactionFail("tx03", PrecompiledContracts.REMASC_ADDR_STR);
        assertTransactionFail("tx04", PrecompiledContracts.HD_WALLET_UTILS_ADDR_STR);
        assertTransactionFail("tx05", PrecompiledContracts.BLOCK_HEADER_ADDR_STR);

        // ETH precompiles test
        assertTransactionOk("tx06", PrecompiledContracts.ECRECOVER_ADDR_STR);
        assertTransactionOk("tx07", PrecompiledContracts.SHA256_ADDR_STR);
        assertTransactionOk("tx08", PrecompiledContracts.RIPEMPD160_ADDR_STR);
        assertTransactionOk("tx09", PrecompiledContracts.IDENTITY_ADDR_STR);
        assertTransactionOk("tx10", PrecompiledContracts.BIG_INT_MODEXP_ADDR_STR);
        assertTransactionOk("tx11", PrecompiledContracts.ALT_BN_128_ADD_ADDR_STR);
        assertTransactionOk("tx12", PrecompiledContracts.ALT_BN_128_MUL_ADDR_STR);
        assertTransactionOk("tx13", PrecompiledContracts.ALT_BN_128_PAIRING_ADDR_STR);
        assertTransactionOk("tx14", PrecompiledContracts.BLAKE2F_ADDR_STR);

        assertTransactionCount(world.getBlockByName("b01").getTransactionsList().size());
    }

    @Test
    void handleErrorOnFailedPrecompiledContractCall_afterIris() throws IOException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300", ConfigValueFactory.fromAnyRef(0))
        );

        DslParser parser = DslParser.fromResource(DSL_PRECOMPILED_CALL_ERROR_HANDLING_TXT);
        world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // RSK precompiles test
        assertTransactionOkWithErrorHandling("tx02", PrecompiledContracts.BRIDGE_ADDR_STR);
        assertTransactionOkWithErrorHandling("tx03", PrecompiledContracts.REMASC_ADDR_STR);
        assertTransactionOkWithErrorHandling("tx04", PrecompiledContracts.HD_WALLET_UTILS_ADDR_STR);
        assertTransactionOkWithErrorHandling("tx05", PrecompiledContracts.BLOCK_HEADER_ADDR_STR);
        // todo(fedejinich) still need to add this tx
//        assertTransactionOkWithErrorHandling("tx15", PrecompiledContracts.INSTALL_CODE_ADDR_STR);

        // ETH precompiles test
        assertTransactionOk("tx06", PrecompiledContracts.ECRECOVER_ADDR_STR);
        assertTransactionOk("tx07", PrecompiledContracts.SHA256_ADDR_STR);
        assertTransactionOk("tx08", PrecompiledContracts.RIPEMPD160_ADDR_STR);
        assertTransactionOk("tx09", PrecompiledContracts.IDENTITY_ADDR_STR);
        assertTransactionOk("tx10", PrecompiledContracts.BIG_INT_MODEXP_ADDR_STR);
        assertTransactionOk("tx11", PrecompiledContracts.ALT_BN_128_ADD_ADDR_STR);
        assertTransactionOk("tx12", PrecompiledContracts.ALT_BN_128_MUL_ADDR_STR);
        assertTransactionOkWithErrorHandling("tx13", PrecompiledContracts.ALT_BN_128_PAIRING_ADDR_STR);
        assertTransactionOkWithErrorHandling("tx14", PrecompiledContracts.BLAKE2F_ADDR_STR);

        assertTransactionCount(world.getBlockByName("b01").getTransactionsList().size());
    }

    /**
     * Assert if a transaction ended properly without any failure.
     *
     * should emmit one PrecompiledSuccess because the whole execution finished ok
     * */
    private void assertTransactionOk(String tx, String precompiledAddress) throws IOException {
        assertTransaction(tx, precompiledAddress,1, 0, true);
    }

    /**
     * Assert if a transaction ended properly but handled the internal error.
     *
     * should emmit one PrecompiledFailure because it's possible to handle a failed precompiled call
     * */
    private void assertTransactionOkWithErrorHandling(String tx, String precompiledAddress) throws IOException {
        assertTransaction(tx, precompiledAddress, 0, 1, true);
    }

    /**
     * Assert if a transaction fails, because of the internal error.
     *
     * shouldn't emmit any event because it failed before and exited the whole execution
     * */
    private void assertTransactionFail(String tx, String precompiledAddress) throws IOException {
        assertTransaction(tx, precompiledAddress,0, 0, false);
    }

    private void assertTransaction(String tx, String precompiledAddress, int expectedPrecompiledSuccessEventCount,
                                   int expectedPrecompiledFailureEventCount, boolean expectedTransactionStatus) throws IOException {
        Transaction transaction = world.getTransactionByName(tx);

        Assertions.assertNotNull(transaction);

        TransactionReceipt transactionReceipt = world.getTransactionReceiptByName(tx);

        assertExpectedData(transactionReceipt.getTransaction(), precompiledAddress);

        Assertions.assertNotNull(transactionReceipt);
        assertEquals(expectedTransactionStatus, transactionReceipt.isSuccessful());

        assertEvents(transactionReceipt, "PrecompiledSuccess", expectedPrecompiledSuccessEventCount);
        assertEvents(transactionReceipt, "PrecompiledFailure", expectedPrecompiledFailureEventCount);
    }

    private void assertTransactionCount(int transactionCount) {
        // there are 12 precompiledContracts and one contract creation (that's why +1)
        int expectedTransactionCount = PrecompiledContracts.GENESIS_ADDRESSES.size() +
                PrecompiledContracts.CONSENSUS_ENABLED_ADDRESSES.size() + 1;

        // todo(fedejinich) fix it properly (should remove -1)
        assertEquals(expectedTransactionCount - 1, transactionCount);

        assertEquals(7, PrecompiledContracts.GENESIS_ADDRESSES.size());
        assertEquals(7, PrecompiledContracts.CONSENSUS_ENABLED_ADDRESSES.size());
    }

    /**
     * Checks if a transaction contains the same data to invoke "callPrec(address)"
     * */
    private void assertExpectedData(Transaction transaction, String precompileToCall) throws IOException {
        // the first 4 bytes corresponds to method signature
        // then the first parameter is a uint32 padded to 32 bytes

        String[] types = new String[1];
        types[0] = "address";
        CallTransaction.Function method = CallTransaction.Function.fromSignature("callPrec",types);
        byte[] signature = method.encodeSignature();
        byte[] params = method.encodeArguments("0x" + precompileToCall);

        ByteArrayOutputStream expectedData = new ByteArrayOutputStream();
        expectedData.write(signature);
        expectedData.write(params);

        Assertions.assertArrayEquals(expectedData.toByteArray(), transaction.getData());
    }

    /**
     * Checks how many times an event is contained on a receipt
     * */
    public void assertEvents(TransactionReceipt receipt, String eventSignature, int times) {
        String[] params = new String[1];
        params[0] = "address";

        // Events on rsk precompiled calls
        Stream<String> events = receipt.getLogInfoList().stream().map(logInfo -> eventSignature(logInfo));
        List<String> eventsSignature = events.filter(event -> isExpectedEventSignature(event, eventSignature, params))
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
