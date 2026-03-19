package co.rsk.vm.precompiles.blake2b;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

/**
 * Regression test for Blake2F precompile null data handling.
 *
 * A transaction sent to the Blake2F precompile (0x09) with empty calldata
 * (data = null from RLP) must not cause a NullPointerException that crashes
 * block execution. The precompile should handle null data gracefully: the
 * block remains valid and other transactions in the same block are unaffected.
 */
class Blake2fNullDataTest {

    /**
     * Before RSKIP-552 is active, a direct transaction to Blake2F with null data triggers
     * an NPE in TransactionExecutor.call() at getGasForData(tx.getData()), which is outside
     * any try-catch and propagates up through BlockExecutor, crashing block processing.
     * This is the actual DoS vector: an attacker submits a transaction with no calldata
     * directly to address 0x09.
     */
    @Test
    void blake2fWithNullData_beforeRskip552_shouldCrashBlockExecution() throws FileNotFoundException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300", ConfigValueFactory.fromAnyRef(0))
                         .withValue("blockchain.config.hardforkActivationHeights.vetiver900", ConfigValueFactory.fromAnyRef(-1))
        );
        World world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        DslParser parser = DslParser.fromResource("dsl/blake2b/blake2f_null_data.txt");

        Assertions.assertThrows(NullPointerException.class, () -> processor.processCommands(parser));
    }

    @Test
    void blake2fWithNullData_shouldNotInvalidateBlock() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300", ConfigValueFactory.fromAnyRef(0))
                         .withValue("blockchain.config.hardforkActivationHeights.vetiver900", ConfigValueFactory.fromAnyRef(0))
        );
        World world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        DslParser parser = DslParser.fromResource("dsl/blake2b/blake2f_null_data.txt");

        processor.processCommands(parser);

        Transaction tx01 = world.getTransactionByName("tx01");
        Assertions.assertNotNull(tx01);
        Assertions.assertNull(tx01.getData(), "ImmutableTransaction with empty calldata should produce null data");

        TransactionReceipt tx01Receipt = world.getTransactionReceiptByName("tx01");
        Assertions.assertNotNull(tx01Receipt, "Blake2F tx receipt should exist (block was not invalidated)");

        TransactionReceipt tx02Receipt = world.getTransactionReceiptByName("tx02");
        Assertions.assertNotNull(tx02Receipt, "Following tx receipt should exist (block was not invalidated)");
        Assertions.assertTrue(tx02Receipt.isSuccessful(), "Normal transfer in the same block should succeed");
    }
}
