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
     * an NPE in Blake2F.getGasForData (via TransactionExecutor.call()). BlockExecutor now
     * catches unexpected exceptions per transaction, rolls back that tx, and continues
     * (executeAndFill uses discardInvalidTxs=true). This test asserts the DSL block still imports successfully.
     */
    @Test
    void blake2fWithNullData_beforeRskip552_blockExecutorHandlesUnexpectedException() throws FileNotFoundException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300", ConfigValueFactory.fromAnyRef(0))
                         .withValue("blockchain.config.consensusRules.rskip552", ConfigValueFactory.fromAnyRef(-1))
        );
        World world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        DslParser parser = DslParser.fromResource("dsl/blake2b/blake2f_null_data.txt");

        Assertions.assertDoesNotThrow(() -> processor.processCommands(parser));
    }

    @Test
    void blake2fWithNullData_shouldNotInvalidateBlock() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300", ConfigValueFactory.fromAnyRef(0))
                         .withValue("blockchain.config.consensusRules.rskip552", ConfigValueFactory.fromAnyRef(0))
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
