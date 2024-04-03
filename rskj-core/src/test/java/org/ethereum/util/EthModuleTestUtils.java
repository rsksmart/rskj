/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.util;

import co.rsk.peg.constants.BridgeConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.db.RepositoryLocator;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.EthModuleTransaction;
import co.rsk.rpc.modules.eth.EthModuleWallet;
import co.rsk.test.World;
import org.ethereum.config.Constants;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

public class EthModuleTestUtils {

    public static EthModule buildBasicEthModule(World world) {
        TestSystemProperties config = new TestSystemProperties();
        TransactionExecutorFactory executor = buildBasicExecutorFactory(world, config);

        return buildCustomEthModule(world, executor, config);
    }

    public static EthModule buildCustomEthModule(World world, TransactionExecutorFactory executor, TestSystemProperties config) {
        return new EthModule(
                null,
                Constants.REGTEST_CHAIN_ID,
                world.getBlockChain(),
                null,
                new ReversibleTransactionExecutor(world.getRepositoryLocator(), executor),
                new ExecutionBlockRetriever(world.getBlockChain(), null, null),
                null,
                null,
                null,
                world.getBridgeSupportFactory(),
                config.getGasEstimationCap(),
                config.getCallGasCap());
    }

    public static EthModuleGasEstimation buildBasicEthModuleForGasEstimation(World world) {
        TestSystemProperties config = new TestSystemProperties();
        TransactionExecutorFactory executor = buildBasicExecutorFactory(world, config);

        return new EthModuleGasEstimation(
                null,
                Constants.REGTEST_CHAIN_ID,
                world.getBlockChain(),
                null,
                new ReversibleTransactionExecutor(world.getRepositoryLocator(), executor),
                new ExecutionBlockRetriever(world.getBlockChain(), null, null),
                world.getRepositoryLocator(),
                null,
                null,
                world.getBridgeSupportFactory(),
                config.getGasEstimationCap(),
                config.getCallGasCap());
    }

    private static TransactionExecutorFactory buildBasicExecutorFactory(World world, TestSystemProperties config) {
        return buildCustomExecutorFactory(world, config, null, null, null);
    }

    public static TransactionExecutorFactory buildCustomExecutorFactory(World world, TestSystemProperties config
            , ReceiptStore receiptStore, BlockFactory blockFactory, BlockTxSignatureCache blockTxSignatureCache) {
        return new TransactionExecutorFactory(
                config,
                world.getBlockStore(),
                receiptStore,
                blockFactory,
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, world.getBridgeSupportFactory(), world.getBlockTxSignatureCache()),
                blockTxSignatureCache
        );
    }

    public static class EthModuleGasEstimation extends EthModule {
        private EthModuleGasEstimation(BridgeConstants bridgeConstants, byte chainId, Blockchain blockchain,
                                      TransactionPool transactionPool, ReversibleTransactionExecutor reversibleTransactionExecutor,
                                      ExecutionBlockRetriever executionBlockRetriever, RepositoryLocator repositoryLocator,
                                      EthModuleWallet ethModuleWallet, EthModuleTransaction ethModuleTransaction,
                                      BridgeSupportFactory bridgeSupportFactory, long gasEstimationCap, long gasCap) {
            super(bridgeConstants, chainId, blockchain, transactionPool, reversibleTransactionExecutor,
                    executionBlockRetriever, repositoryLocator, ethModuleWallet, ethModuleTransaction,
                    bridgeSupportFactory, gasEstimationCap, gasCap);
        }

        private ProgramResult estimationResult;

        public ProgramResult getEstimationResult() {
            return estimationResult;
        }

        @Override
        public String internalEstimateGas(ProgramResult reversibleExecutionResult) {
            String estimatedGas = super.internalEstimateGas(reversibleExecutionResult);
            estimationResult = reversibleExecutionResult;

            return estimatedGas;
        }
    }
}

