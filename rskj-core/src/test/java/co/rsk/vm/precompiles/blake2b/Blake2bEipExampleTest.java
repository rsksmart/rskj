package co.rsk.vm.precompiles.blake2b;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.util.TransactionReceiptUtil;
import org.ethereum.util.TestInjectorUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

/**
 * Testing EIP-152 provided example
 * https://github.com/ethereum/EIPs/blob/master/EIPS/eip-152.md
 * */
class Blake2bEipExampleTest {

    @BeforeEach
    public void setUp() {
        TestInjectorUtil.initEmpty();
    }

    @Test
    void runTest() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300", ConfigValueFactory.fromAnyRef(0))
        );
        World world = new World(config);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        DslParser parser = DslParser.fromResource("dsl/blake2b/eip_152_example.txt");

        processor.processCommands(parser);

        TransactionReceipt tx02Receipt = world.getTransactionReceiptByName("tx02");

        Assertions.assertEquals(1, TransactionReceiptUtil.getEventCount(tx02Receipt, "ResultOk", null));
    }
}
