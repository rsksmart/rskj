package co.rsk.vm.precompiles;

import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.LogInfo;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * On Iris hardfork, when a user wants to call a contract from another contract,
 * it has the chance to handle that failed call. Before Iris the whole call was marked as failed (reverted?) without
 * any chance to do some error handling.
 *
 * This tests covers that functionality, focusing only on precompiled contracts. Using a contract to invoke all the existing precompiles
 * with trash data. This will produce different kind of events to verify the expected behaviour.
 *
 * Note: iris300 is activated at block 5 (check tests-rskj.conf)
 *
 * Events
 * - ErrorHandlingOk => 1b5dd4fe5efbcfc88bb005fae9fdceab162bf13bef1fc1843ea22406b2a1d347
 * - PrecompiledSuccess(address) => d1c48ee5d8b9dfbcca9046f456364548ef0b27b0a39faf92aa1c253abf816482
 * - PrecompiledFailure(address) => aa679a624a231df95e2bd73419c633e47abb959a4d3bbfd245a07c036c38202e
 * - PrecompiledUnexpected(address) => b7674390129469ce95bae9f76ab79e70536654d8771744491c2ba45c447a8b76
 * */
public class PrecompiledContractsCallErrorHandlingTests {
    public static final String DSL_PRECOMPILED_CALL_ERROR_HANDLING_TXT = "dsl/contract_call/precompiled_error_handling.txt";

    @Test
    public void handleErrorOnFailedPrecompiledContractCall_beforeIris() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource(DSL_PRECOMPILED_CALL_ERROR_HANDLING_TXT);
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        TransactionReceipt rskPrecompiles = world.getTransactionReceiptByName("tx02");
        TransactionReceipt ethPrecompiles = world.getTransactionReceiptByName("tx03");

        checkEvents(rskPrecompiles, 0 ,0);
        checkEvents(ethPrecompiles, 8, 0);

        // todo(fedejinich) check all gas consumed

        Assert.assertFalse(rskPrecompiles.isSuccessful()); // before iris should fail
        Assert.assertTrue(ethPrecompiles.isSuccessful()); // it succeeds because there are no failings for eth precompiles
    }

    @Test
    public void handleErrorOnFailedPrecompiledContractCall_afterIris() throws FileNotFoundException, DslProcessorException {
        // iris300 is activated at block 5 (check test-rskj.conf)

        DslParser parser = DslParser.fromResource(DSL_PRECOMPILED_CALL_ERROR_HANDLING_TXT);
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Transaction containing rsk precompiled calls (remasc, bridge, hd_wallet_utils, block_header)
        TransactionReceipt receiptRsk = world.getTransactionReceiptByName("tx04");

        checkEvents(receiptRsk, 0, 4);

        // Transaction containing eth precompiled calls (taken from eth network)
        TransactionReceipt receiptEth = world.getTransactionReceiptByName("tx05");

        /*
         * It's important to note that there aren't any failure call for any eth precompiled (up to iris),
         * but there might be eth prec-contracts producing errors in the future
         * */
        checkEvents(receiptEth, 8, 0);

        List<Transaction> transactionsBlock1 = world.getBlockByName("b01").getTransactionsList();
        List<Transaction> transactionsBlock2 = world.getBlockByName("b02").getTransactionsList();
        List<Transaction> transactionsBlock5 =  world.getBlockByName("b05").getTransactionsList();

        Assert.assertEquals(1, transactionsBlock1.size()); // contract the creation
        Assert.assertEquals(2, transactionsBlock2.size()); // tx02, tx03
        Assert.assertEquals(2, transactionsBlock5.size()); // tx04, tx05
        Assert.assertTrue(containsTransactions(transactionsBlock5, receiptEth.getTransaction(), receiptRsk.getTransaction()));
    }

    public void checkEvents(TransactionReceipt receipt, int expectedPrecompiledSuccess, int expectedPrecompiledFailure) {
        containsEvents(receipt, "PrecompiledSuccess", Arrays.asList("address"), expectedPrecompiledSuccess);
        containsEvents(receipt, "PrecompiledFailure", Arrays.asList("address"), expectedPrecompiledFailure);
    }

    private boolean containsTransactions(List<Transaction> transactionsInBlock, Transaction... transaction) {
        List<Transaction> transactions =  Arrays.asList(transaction);

        return transactions.stream().anyMatch(t -> transactionsInBlock.contains(t));
    }

    /**
     * Checks how many times an event is contained on a receipt
     * */
    public void containsEvents(TransactionReceipt receipt, String eventSignature, @Nonnull List<String> eventTypeParams, int times) {
        // Events on rsk precompiled calls
        List<String> eventsSignature = invokedEventsSignatures(receipt, eventSignature, (String[]) eventTypeParams.toArray());

        Assert.assertEquals(times, eventsSignature.size());
    }

    /**
     * Returns a list of invoked events signatures
     * Note: this may be unsafe for anonymous events
     *
     * @param receipt the actual receipt
     * @param eventSignature an event signature
     * @param eventTypeParams
     *
     * @return a list with invoked events filtered by event signature
     */
    public static List<String> invokedEventsSignatures(@Nonnull TransactionReceipt receipt, @Nonnull String eventSignature, @Nonnull String[] eventTypeParams) {
        Stream<String> events = receipt.getLogInfoList().stream().map(logInfo -> eventSignature(logInfo));
        List<String> filteredEvents = events.filter(event -> isExpectedEventSignature(event, eventSignature, eventTypeParams))
                .collect(Collectors.toList());

        return filteredEvents;
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
