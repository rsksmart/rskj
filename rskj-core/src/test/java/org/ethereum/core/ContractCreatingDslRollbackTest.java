package org.ethereum.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

class ContractCreatingDslRollbackTest {

    @Test
    void testCreateExecutionFailsAfter453IsRollingBack() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties properties = new TestSystemProperties();

        String resourcePath = "dsl/453_rollback_during_creation/not_enough_gas";
        DslParser parser = DslParser.fromResource(resourcePath);
        World world = new World(properties);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        TransactionReceipt transactionReceipt = world.getTransactionReceiptByName("txContractCreation");
        assertNotNull(transactionReceipt);
        assertFalse(transactionReceipt.isSuccessful());
    }

    @Test
    void testCreateExecutionBefore453isNotRollingBackHavingNotEnoughGaa() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties properties = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.consensusRules.rskip453", ConfigValueFactory.fromAnyRef(-1))
        );

        String resourcePath = "dsl/453_rollback_during_creation/not_enough_gas";
        DslParser parser = DslParser.fromResource(resourcePath);
        World world = new World(properties);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        TransactionReceipt transactionReceipt = world.getTransactionReceiptByName("txContractCreation");
        assertNotNull(transactionReceipt);
        assertTrue(transactionReceipt.isSuccessful());
    }

    @Test
    void createExecutionExceedingCodeSizeDoesNotRollbackBefore453() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties properties = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.consensusRules.rskip453", ConfigValueFactory.fromAnyRef(-1))
        );

        String resourcePath = "dsl/453_rollback_during_creation/exceeding_code_size";
        DslParser parser = DslParser.fromResource(resourcePath);
        World world = new World(properties);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        TransactionReceipt transactionReceipt = world.getTransactionReceiptByName("txContractCreation");
        assertNotNull(transactionReceipt);
        assertTrue(transactionReceipt.isSuccessful());
    }

    @Test
    void createExecutionExceedingCodeSizeDoesRollbackAfter453() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties properties = new TestSystemProperties();

        String resourcePath = "dsl/453_rollback_during_creation/exceeding_code_size";
        DslParser parser = DslParser.fromResource(resourcePath);
        World world = new World(properties);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        TransactionReceipt transactionReceipt = world.getTransactionReceiptByName("txContractCreation");
        assertNotNull(transactionReceipt);
        assertFalse(transactionReceipt.isSuccessful());
    }

}
