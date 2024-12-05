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

import java.io.FileNotFoundException;

public class MCopyDslTest {

    @Test
    void testMCOPY_whenNotActivated_behavesAsExpected() throws FileNotFoundException, DslProcessorException {

        // Test Config Setup

        TestSystemProperties configWithRskip445Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.lovell700", ConfigValueFactory.fromAnyRef(-1))
        );

        // Test Setup

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testRSKIPNotActivatedTest.txt");
        World world = new World(configWithRskip445Disabled);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyNotActivated = world.getTransactionByName("txTestMCopyNotActivated");
        Assertions.assertNotNull(txTestMCopyNotActivated);

        TransactionReceipt txTestMCopyNotActivatedReceipt = world.getTransactionReceiptByName("txTestMCopyNotActivated");
        Assertions.assertNotNull(txTestMCopyNotActivatedReceipt);

        byte[] txTestMCopyNotActivatedCreationStatus = txTestMCopyNotActivatedReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyNotActivatedCreationStatus);
        Assertions.assertEquals(0, txTestMCopyNotActivatedCreationStatus.length);

    }

    @Test
    void testMCOPY_testCase1_behavesAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testCopying32BytesFromOffset32toOffset0.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    @Test
    void testMCOPY_testCase2_behavesAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testCopying32BytesFromOffset0toOffset0.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    @Test
    void testMCOPY_testCase3_behavesAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testCopying8BytesFromOffset1toOffset0.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    @Test
    void testMCOPY_testCase4_behavesAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testCopying8BytesFromOffset0toOffset1.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    // Advanced Overwrite Test Cases
    // https://github.com/ethereum/execution-spec-tests/blob/c0065176a79f89d93f4c326186fc257ec5b8d5f1/tests/cancun/eip5656_mcopy/test_mcopy.py)

    @Test
    void testMCOPY_overwriteCases_behaveAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testOverwriteCases.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ZERO_INPUTS_OK", null));
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "SINGLE_BYTE_REWRITE_OK", null));
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "FULL_WORD_REWRITE_OK", null));
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "SINGLE_BYTE_FWD_OVERWRITE_OK", null));
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "FULL_WORD_FWD_OVERWRITE_OK", null));
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "MID_WORD_SINGLE_BYTE_REWRITE_OK", null));
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "MID_WORD_SINGLE_WORD_REWRITE_OK", null));
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "MID_WORD_MULTY_WORD_REWRITE_OK", null));
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "TWO_WORDS_FWD_OVERWRITE_OK", null));
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "TWO_WORDS_BWD_OVERWRITE_OK", null));
        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "TWO_WORDS_BWD_OVERWRITE_SINGLE_BYTE_OFFSET_OK", null));

        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    // Full Memory Copy/Rewrite/Clean Tests

    @Test
    void testMCOPY_fullMemoryClean_behaveAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testFullMemoryClean.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    @Test
    void testMCOPY_fullMemoryCopy_behaveAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testFullMemoryCopy.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    @Test
    void testMCOPY_fullMemoryCopyOffset_behaveAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testFullMemoryCopyOffset.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    @Test
    void testMCOPY_fullMemoryRewrite_behaveAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testFullMemoryRewrite.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    // Memory Extension Tests

    @Test
    void testMCOPY_outOfBoundsMemoryExtension_behaveAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testOutOfBoundsMemoryExtension.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    @Test
    void testMCOPY_singleByteMemoryExtension_behaveAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testSingleByteMemoryExtension.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    @Test
    void testMCOPY_singleWordMemoryExtension_behaveAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testSingleWordMemoryExtension.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    @Test
    void testMCOPY_singleWordMinusOneByteMemoryExtension_behaveAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testSingleWordMinusOneByteMemoryExtension.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    @Test
    void testMCOPY_singleWordPlusOneByteMemoryExtension_behaveAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testSingleWordPlusOneByteMemoryExtension.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        Block block1 = world.getBlockByName("b01");
        Assertions.assertNotNull(block1);
        Assertions.assertEquals(1, block1.getTransactionsList().size());

        Transaction txTestMCopy = world.getTransactionByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopy);

        TransactionReceipt txTestMCopyReceipt = world.getTransactionReceiptByName("txTestMCopy");
        Assertions.assertNotNull(txTestMCopyReceipt);

        byte[] creationStatus = txTestMCopyReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);
        Assertions.assertEquals(1, creationStatus.length);
        Assertions.assertEquals(1, creationStatus[0]);

        Block block2 = world.getBlockByName("b02");
        Assertions.assertNotNull(block2);
        Assertions.assertEquals(1, block2.getTransactionsList().size());

        Transaction txTestMCopyOKCall = world.getTransactionByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCall);

        TransactionReceipt txTestMCopyOKCallReceipt = world.getTransactionReceiptByName("txTestMCopyOKCall");
        Assertions.assertNotNull(txTestMCopyOKCallReceipt);

        byte[] txTestMCopyOKCallCreationStatus = txTestMCopyOKCallReceipt.getStatus();
        Assertions.assertNotNull(txTestMCopyOKCallCreationStatus);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus.length);
        Assertions.assertEquals(1, txTestMCopyOKCallCreationStatus[0]);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

}
