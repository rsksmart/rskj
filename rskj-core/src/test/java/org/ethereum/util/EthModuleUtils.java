package org.ethereum.util;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.test.World;
import org.ethereum.config.Constants;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

public class EthModuleUtils {

    public static EthModule buildBasicEthModule(World world) {
        TestSystemProperties config = new TestSystemProperties();
        TransactionExecutorFactory executor = new TransactionExecutorFactory(
                config,
                world.getBlockStore(),
                null,
                null,
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, world.getBridgeSupportFactory()),
                null
        );

        return new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                world.getBlockChain(),
                null,
                new ReversibleTransactionExecutor(world.getRepositoryLocator(), executor),
                new ExecutionBlockRetriever(null, world.getBlockChain(), null, null),
                null,
                null,
                null,
                world.getBridgeSupportFactory(),
                config.getGasEstimationCap());
    }
}
