/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.rpc.modules.eth;

import static org.ethereum.rpc.TypeConverter.toJsonHex;

import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.db.RepositoryLocator;
import co.rsk.peg.BridgeState;
import co.rsk.peg.BridgeStorageConfiguration;
import co.rsk.peg.BridgeSupport;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.rpc.ExecutionBlockRetriever;
import java.io.IOException;
import java.util.Map;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Repository;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.converters.CallArgumentsToByteArray;
import org.ethereum.rpc.dto.CompilationResultDTO;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO add all RPC methods
public class EthModule implements EthModuleSolidity, EthModuleWallet, EthModuleTransaction {

    private static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private final Blockchain blockchain;
    private final ReversibleTransactionExecutor reversibleTransactionExecutor;
    private final ExecutionBlockRetriever executionBlockRetriever;
    private final RepositoryLocator repositoryLocator;
    private final EthModuleSolidity ethModuleSolidity;
    private final EthModuleWallet ethModuleWallet;
    private final EthModuleTransaction ethModuleTransaction;
    private final BridgeConstants bridgeConstants;
    private final ActivationConfig activationConfig;
    private final BtcBlockStoreWithCache.Factory btcBlockStoreFactory;

    public EthModule(
            BridgeConstants bridgeConstants,
            ActivationConfig activationConfig,
            Blockchain blockchain,
            ReversibleTransactionExecutor reversibleTransactionExecutor,
            ExecutionBlockRetriever executionBlockRetriever,
            RepositoryLocator repositoryLocator,
            EthModuleSolidity ethModuleSolidity,
            EthModuleWallet ethModuleWallet,
            EthModuleTransaction ethModuleTransaction,
            BtcBlockStoreWithCache.Factory btcBlockStoreFactory) {
        this.blockchain = blockchain;
        this.reversibleTransactionExecutor = reversibleTransactionExecutor;
        this.executionBlockRetriever = executionBlockRetriever;
        this.repositoryLocator = repositoryLocator;
        this.ethModuleSolidity = ethModuleSolidity;
        this.ethModuleWallet = ethModuleWallet;
        this.ethModuleTransaction = ethModuleTransaction;
        this.bridgeConstants = bridgeConstants;
        this.activationConfig = activationConfig;
        this.btcBlockStoreFactory = btcBlockStoreFactory;
    }

    @Override
    public String[] accounts() {
        return ethModuleWallet.accounts();
    }

    public Map<String, Object> bridgeState() throws IOException, BlockStoreException {
        Block bestBlock = blockchain.getBestBlock();
        Repository repository = repositoryLocator.snapshotAt(bestBlock.getHeader()).startTracking();

        BridgeSupport bridgeSupport =
                new BridgeSupport(
                        bridgeConstants,
                        new BridgeStorageConfiguration(
                                activationConfig.isActive(
                                        ConsensusRule.RSKIP87, bestBlock.getNumber()),
                                activationConfig.isActive(
                                        ConsensusRule.RSKIP123, bestBlock.getNumber())),
                        null,
                        repository,
                        bestBlock,
                        PrecompiledContracts.BRIDGE_ADDR,
                        btcBlockStoreFactory);

        byte[] result = bridgeSupport.getStateForDebugging();

        BridgeState state = BridgeState.create(bridgeConstants, result);

        return state.stateToMap();
    }

    public String call(Web3.CallArguments args, String bnOrId) {
        String s = null;
        try {
            Block executionBlock = executionBlockRetriever.getExecutionBlock(bnOrId);
            ProgramResult res = callConstant(args, executionBlock);
            if (res.isRevert()) {
                throw RskJsonRpcRequestException.transactionRevertedExecutionError();
            }

            return s = toJsonHex(res.getHReturn());
        } finally {
            LOGGER.debug("eth_call(): {}", s);
        }
    }

    @Override
    public Map<String, CompilationResultDTO> compileSolidity(String contract) throws Exception {
        return ethModuleSolidity.compileSolidity(contract);
    }

    public String estimateGas(Web3.CallArguments args) {
        String s = null;
        try {
            ProgramResult res = callConstant(args, blockchain.getBestBlock());
            return s = toJsonHex(res.getGasUsed());
        } finally {
            LOGGER.debug("eth_estimateGas(): {}", s);
        }
    }

    @Override
    public String sendTransaction(Web3.CallArguments args) {
        return ethModuleTransaction.sendTransaction(args);
    }

    @Override
    public String sendRawTransaction(String rawData) {
        return ethModuleTransaction.sendRawTransaction(rawData);
    }

    @Override
    public String sign(String addr, String data) {
        return ethModuleWallet.sign(addr, data);
    }

    private ProgramResult callConstant(Web3.CallArguments args, Block executionBlock) {
        CallArgumentsToByteArray hexArgs = new CallArgumentsToByteArray(args);
        return reversibleTransactionExecutor.executeTransaction(
                executionBlock,
                executionBlock.getCoinbase(),
                hexArgs.getGasPrice(),
                hexArgs.getGasLimit(),
                hexArgs.getToAddress(),
                hexArgs.getValue(),
                hexArgs.getData(),
                hexArgs.getFromAddress());
    }
}
