package org.ethereum.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.util.TransactionFactoryHelper;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.assertTrue;


public class NestedCallsStackDepthTest {

    private static final CallTransaction.Function CALL_CONTRACTB_FUNCTION = CallTransaction.Function.fromSignature("callContractB");

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
        ethModule = buildEthModule(world);
    }

    /** ------------------------ **
     *  TESTS
     ** ------------------------ **/

    @Test
    void testNestedContractCallsGetCallStackDepth() throws FileNotFoundException, DslProcessorException {
        processor.processCommands(DslParser.fromResource("dsl/nested_environment_calls.txt"));
        world.getRepository().commit();

        assertTrue(world.getTransactionReceiptByName("tx04").isSuccessful());

        final String contractA = getContractAddressString("tx03");
        CallArguments args = buildArgs(contractA, Hex.toHexString(CALL_CONTRACTB_FUNCTION.encode()));
        String call = ethModule.call(TransactionFactoryHelper.toCallArgumentsParam(args), new BlockIdentifierParam("latest"));
        System.out.println("Fin");
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

    private EthModule buildEthModule(World world) {
        final TestSystemProperties config = new TestSystemProperties();
        TransactionExecutorFactory executor = new TransactionExecutorFactory(
                config,
                world.getBlockStore(),
                null,
                null,
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, world.getBridgeSupportFactory(), new BlockTxSignatureCache(new ReceivedTxSignatureCache())),
                null
        );

        return new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                world.getBlockChain(),
                world.getTransactionPool(),
                new ReversibleTransactionExecutor(world.getRepositoryLocator(), executor),
                new ExecutionBlockRetriever(world.getBlockChain(), null, null),
                world.getRepositoryLocator(),
                null,
                null,
                world.getBridgeSupportFactory(),
                config.getGasEstimationCap(),
                config.getCallGasCap());
    }
}
