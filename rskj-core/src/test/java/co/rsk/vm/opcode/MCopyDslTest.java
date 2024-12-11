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
import java.util.stream.Stream;

class MCopyDslTest {

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

        assertBlockExistsAndContainsExpectedNumberOfTxs(world, "b01", 1);
        assertTxExistsWithExpectedReceiptStatus(world, "txTestMCopy", true);

        assertBlockExistsAndContainsExpectedNumberOfTxs(world, "b02", 1);
        assertTxExistsWithExpectedReceiptStatus(world, "txTestMCopyNotActivated", false);

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

        assertBlockExistsAndContainsExpectedNumberOfTxs(world, "b01", 1);
        assertTxExistsWithExpectedReceiptStatus(world, "txTestMCopy", true);

        assertBlockExistsAndContainsExpectedNumberOfTxs(world, "b02", 1);
        TransactionReceipt txTestMCopyOKCallReceipt = assertTxExistsWithExpectedReceiptStatus(world, "txTestMCopyOKCall", true);

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

    @ParameterizedTest
    @MethodSource("provideParametersForMCOPYTestCases")
    void testMCOPY_OnEachTestCase_ExecutesAsExpected(String dslFile) throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/" + dslFile);
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        assertBlockExistsAndContainsExpectedNumberOfTxs(world, "b01", 1);
        assertTxExistsWithExpectedReceiptStatus(world, "txTestMCopy", true);

        assertBlockExistsAndContainsExpectedNumberOfTxs(world, "b02", 1);
        TransactionReceipt txTestMCopyOKCallReceipt = assertTxExistsWithExpectedReceiptStatus(world, "txTestMCopyOKCall", true);

        // Event Assertions

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    @Test
    void testMCOPY_zeroLengthOutOfBoundsDestination_runsOutOfGasAsExpected() throws FileNotFoundException, DslProcessorException {

        DslParser parser = DslParser.fromResource("dsl/opcode/mcopy/testZeroLengthOutOfBoundsDestination.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // Assertions

        assertBlockExistsAndContainsExpectedNumberOfTxs(world, "b01", 1);
        assertTxExistsWithExpectedReceiptStatus(world, "txTestMCopy", true);

        assertBlockExistsAndContainsExpectedNumberOfTxs(world, "b02", 1);
        TransactionReceipt txTestMCopyOKCallReceipt = assertTxExistsWithExpectedReceiptStatus(world, "txTestMCopyOKCall", false);

        // Event Assertions

        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "OK", null));
        Assertions.assertEquals(0, TransactionReceiptUtil.getEventCount(txTestMCopyOKCallReceipt, "ERROR", null));

    }

    private static Stream<Arguments> provideParametersForMCOPYTestCases() {
        return Stream.of(

                // Basic Test Cases from 1 to 4 on EIP
                Arguments.of("testCopying32BytesFromOffset32toOffset0.txt"),
                Arguments.of("testCopying32BytesFromOffset0toOffset0.txt"),
                Arguments.of("testCopying8BytesFromOffset1toOffset0.txt"),
                Arguments.of("testCopying8BytesFromOffset0toOffset1.txt"),

                // Full Memory Copy/Rewrite/Clean Tests
                Arguments.of("testFullMemoryClean.txt"),
                Arguments.of("testFullMemoryCopy.txt"),
                Arguments.of("testFullMemoryCopyOffset.txt"),
                Arguments.of("testFullMemoryRewrite.txt"),

                // Memory Extension Tests
                Arguments.of("testOutOfBoundsMemoryExtension.txt"),
                Arguments.of("testSingleByteMemoryExtension.txt"),
                Arguments.of("testSingleWordMemoryExtension.txt"),
                Arguments.of("testSingleWordMinusOneByteMemoryExtension.txt"),
                Arguments.of("testSingleWordPlusOneByteMemoryExtension.txt")

        );
    }

    private static void assertBlockExistsAndContainsExpectedNumberOfTxs(World world, String blockName, int expectedNumberOfTxs) {
        Block block = world.getBlockByName(blockName);
        Assertions.assertNotNull(block);
        Assertions.assertEquals(expectedNumberOfTxs, block.getTransactionsList().size());
    }

    private static TransactionReceipt assertTxExistsWithExpectedReceiptStatus(World world, String txName, boolean mustBeSuccessful) {
        Transaction tx = world.getTransactionByName(txName);
        Assertions.assertNotNull(tx);

        TransactionReceipt txReceipt = world.getTransactionReceiptByName(txName);
        Assertions.assertNotNull(txReceipt);
        byte[] creationStatus = txReceipt.getStatus();
        Assertions.assertNotNull(creationStatus);

        if (mustBeSuccessful) {
            Assertions.assertEquals(1, creationStatus.length);
            Assertions.assertEquals(1, creationStatus[0]);
        } else {
            Assertions.assertEquals(0, creationStatus.length);
        }

        return txReceipt;
    }

}
