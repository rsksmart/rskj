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

import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.core.bc.BlockResult;
import co.rsk.db.RepositoryLocator;
import co.rsk.peg.BridgeState;
import co.rsk.peg.BridgeSupport;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Repository;
import org.ethereum.core.TransactionPool;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.converters.CallArgumentsToByteArray;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.AbiTypes;
import org.web3j.abi.datatypes.Type;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.ethereum.rpc.TypeConverter.stringHexToBigInteger;
import static org.ethereum.rpc.TypeConverter.toJsonHex;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

// TODO add all RPC methods
public class EthModule
    implements EthModuleWallet, EthModuleTransaction {

    private static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private final Blockchain blockchain;
    private final TransactionPool transactionPool;
    private final ReversibleTransactionExecutor reversibleTransactionExecutor;
    private final ExecutionBlockRetriever executionBlockRetriever;
    private final RepositoryLocator repositoryLocator;
    private final EthModuleWallet ethModuleWallet;
    private final EthModuleTransaction ethModuleTransaction;
    private final BridgeConstants bridgeConstants;
    private final BridgeSupportFactory bridgeSupportFactory;
    private final byte chainId;

    public EthModule(
            BridgeConstants bridgeConstants,
            byte chainId,
            Blockchain blockchain,
            TransactionPool transactionPool,
            ReversibleTransactionExecutor reversibleTransactionExecutor,
            ExecutionBlockRetriever executionBlockRetriever,
            RepositoryLocator repositoryLocator,
            EthModuleWallet ethModuleWallet,
            EthModuleTransaction ethModuleTransaction,
            BridgeSupportFactory bridgeSupportFactory) {
        this.chainId = chainId;
        this.blockchain = blockchain;
        this.transactionPool = transactionPool;
        this.reversibleTransactionExecutor = reversibleTransactionExecutor;
        this.executionBlockRetriever = executionBlockRetriever;
        this.repositoryLocator = repositoryLocator;
        this.ethModuleWallet = ethModuleWallet;
        this.ethModuleTransaction = ethModuleTransaction;
        this.bridgeConstants = bridgeConstants;
        this.bridgeSupportFactory = bridgeSupportFactory;
    }

    @Override
    public String[] accounts() {
        return ethModuleWallet.accounts();
    }

    public Map<String, Object> bridgeState() throws IOException, BlockStoreException {
        Block bestBlock = blockchain.getBestBlock();
        Repository track = repositoryLocator.startTrackingAt(bestBlock.getHeader());

        BridgeSupport bridgeSupport = bridgeSupportFactory.newInstance(
                track, bestBlock, PrecompiledContracts.BRIDGE_ADDR, null);

        byte[] result = bridgeSupport.getStateForDebugging();

        BridgeState state = BridgeState.create(bridgeConstants, result, null);

        return state.stateToMap();
    }

    public String call(Web3.CallArguments args, String bnOrId) {
        String s = null;
        try {
            BlockResult blockResult = executionBlockRetriever.getExecutionBlock_workaround(bnOrId);
            ProgramResult res;
            if (blockResult.getFinalState() != null) {
                res = callConstant_workaround(args, blockResult);
            } else {
                res = callConstant(args, blockResult.getBlock());
            }

            if (res.isRevert()) {
                Optional<String> revertReason = decodeRevertReason(res);
                if (revertReason.isPresent()) {
                    throw RskJsonRpcRequestException.transactionRevertedExecutionError(revertReason.get());
                } else {
                    throw RskJsonRpcRequestException.transactionRevertedExecutionError();
                }
            }

            return s = toJsonHex(res.getHReturn());
        } finally {
            LOGGER.debug("eth_call(): {}", s);
        }
    }

    public String estimateGas(Web3.CallArguments args) {
        String s = null;
        try {
            ProgramResult res = callConstant(args, blockchain.getBestBlock());
            return s = TypeConverter.toQuantityJsonHex(res.getGasUsed());
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

    public String chainId() {
        return TypeConverter.toJsonHex(new byte[] { chainId });
    }

    public String getCode(String address, String blockId) {
        if (blockId == null) {
            throw new NullPointerException();
        }

        String s = null;
        try {
            RskAddress addr = new RskAddress(address);

            AccountInformationProvider accountInformationProvider = getAccountInformationProvider(blockId);

            if(accountInformationProvider != null) {
                byte[] code = accountInformationProvider.getCode(addr);

                // Code can be null, if there is no account.
                if (code == null) {
                    code = new byte[0];
                }

                s = TypeConverter.toJsonHex(code);
            }

            return s;
        } finally {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("eth_getCode({}, {}): {}", address, blockId, s);
            }
        }
    }

    private AccountInformationProvider getAccountInformationProvider(String id) {
        switch (id.toLowerCase()) {
            case "pending":
                return transactionPool.getPendingState();
            case "earliest":
                return repositoryLocator.snapshotAt(blockchain.getBlockByNumber(0).getHeader());
            case "latest":
                return repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());
            default:
                try {
                    long blockNumber = stringHexToBigInteger(id).longValue();
                    Block requestedBlock = blockchain.getBlockByNumber(blockNumber);
                    if (requestedBlock != null) {
                        return repositoryLocator.snapshotAt(requestedBlock.getHeader());
                    }
                    return null;
                } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                    throw invalidParamError("invalid blocknumber " + id);
                }
        }
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
                hexArgs.getFromAddress()
        );
    }

    @SuppressWarnings("unchecked")
    private Optional<String> decodeRevertReason(ProgramResult res) {
        byte[] bytes = res.getHReturn();
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }

        String value = Hex.toHexString(res.getHReturn());
        if (!value.startsWith("08c379a0")) {
            return Optional.empty();
        }

        List<TypeReference<Type>> revertReasonTypes =
                Collections.singletonList(TypeReference.create((Class<Type>) AbiTypes.getType("string")));
        String encodedRevertReason = value.substring(8);
        List<Type> decoded = FunctionReturnDecoder.decode(encodedRevertReason, revertReasonTypes);
        return Optional.of(decoded.get(0).getValue().toString());
    }

    @Deprecated
    private ProgramResult callConstant_workaround(Web3.CallArguments args, BlockResult executionBlock) {
        CallArgumentsToByteArray hexArgs = new CallArgumentsToByteArray(args);
        return reversibleTransactionExecutor.executeTransaction_workaround(
                new MutableRepository(new TrieStoreImpl(new HashMapDB()), executionBlock.getFinalState()),
                executionBlock.getBlock(),
                executionBlock.getBlock().getCoinbase(),
                hexArgs.getGasPrice(),
                hexArgs.getGasLimit(),
                hexArgs.getToAddress(),
                hexArgs.getValue(),
                hexArgs.getData(),
                hexArgs.getFromAddress()
        );
    }
}
