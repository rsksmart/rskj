package org.ethereum.core;

import co.rsk.core.RskAddress;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.util.EthModuleTestUtils;
import org.ethereum.util.TransactionFactoryHelper;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class NestedCallsStackDepthTest {

    private static final CallTransaction.Function CALL_GCSD_FUNCTION = CallTransaction.Function.fromSignature("callGetCallStackDepth");

    private World world;
    private WorldDslProcessor processor;
    private EthModule ethModule;

    /** ------------------------ **
     *  SETUP
     ** ------------------------ **/
    @BeforeEach
    void setup() {
        world = new World();
        processor = new WorldDslProcessor(world);
        ethModule = EthModuleTestUtils.buildBasicEthModule(world);
    }

    /** ------------------------ **
     *  TESTS
     ** ------------------------ **/

    @Test
    void testNestedContractCallsGetCallStackDepth() throws FileNotFoundException, DslProcessorException {
        processor.processCommands(DslParser.fromResource("dsl/nested_environment_calls.txt"));
        world.getRepository().commit();

        final String contractA = getContractAddressString("tx03");
        CallArguments args = buildArgs(contractA, Hex.toHexString(CALL_GCSD_FUNCTION.encode()));
        String call = ethModule.call(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam("latest"));
        assertEquals("0x" + DataWord.valueOf(3).toString(), call);
    }

    @Test
    void testContractCallsGetCallStackDepth() throws FileNotFoundException, DslProcessorException {
        processor.processCommands(DslParser.fromResource("dsl/nested_environment_calls.txt"));
        world.getRepository().commit();

        final String contractA = getContractAddressString("tx01");
        CallArguments args = buildArgs(contractA, Hex.toHexString(CALL_GCSD_FUNCTION.encode()));
        String call = ethModule.call(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam("latest"));
        assertEquals("0x" + DataWord.valueOf(1).toString(), call);
    }

    /** ------------------------ **
     *  UTILITIES
     ** ------------------------ **/

    private RskAddress getContractAddress(String contractTx) {
        return world.getTransactionByName(contractTx).getContractAddress();
    }

    private String getContractAddressString(String contractTx) {
        return "0x" + getContractAddress(contractTx).toHexString();
    }

    private CallArguments buildArgs(String toAddress, String data) {
        final CallArguments args = new CallArguments();
        args.setTo(toAddress);
        args.setData("0x" + data); // call to contract
        args.setValue("0");
        args.setNonce("1");
        args.setGas("10000000");
        return args;
    }
}
