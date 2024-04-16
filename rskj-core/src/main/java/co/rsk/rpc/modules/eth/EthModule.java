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
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.peg.BridgeState;
import co.rsk.peg.BridgeSupport;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.util.HexUtils;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.converters.CallArgumentsToByteArray;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

import static java.util.Arrays.copyOfRange;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

// TODO add all RPC methods
public class EthModule
        implements EthModuleWallet, EthModuleTransaction {

    private static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private static final CallTransaction.Function ERROR_ABI_FUNCTION = CallTransaction.Function.fromSignature("Error", "string");
    private static final byte[] ERROR_ABI_FUNCTION_SIGNATURE = ERROR_ABI_FUNCTION.encodeSignature(); //08c379a0

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
    private final long gasEstimationCap;
    private final long gasCallCap;

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
            BridgeSupportFactory bridgeSupportFactory,
            long gasEstimationCap,
            long gasCallCap) {
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
        this.gasEstimationCap = gasEstimationCap;
        this.gasCallCap = gasCallCap;
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

    public String call(CallArgumentsParam argsParam, BlockIdentifierParam bnOrId) {
        String hReturn = null;
        CallArguments args = argsParam.toCallArguments();
        try {
            ExecutionBlockRetriever.Result result = executionBlockRetriever.retrieveExecutionBlock(bnOrId.getIdentifier());
            Block block = result.getBlock();
            Trie finalState = result.getFinalState();
            ProgramResult res;
            if (finalState != null) {
                res = callConstantWithState(args, block, finalState);
            } else {
                res = callConstant(args, block);
            }

            if (res.isRevert()) {
                Optional<String> revertReason = decodeRevertReason(res);
                if (revertReason.isPresent()) {
                    throw RskJsonRpcRequestException.transactionRevertedExecutionError(revertReason.get());
                } else {
                    throw RskJsonRpcRequestException.transactionRevertedExecutionError();
                }
            }

            hReturn = HexUtils.toUnformattedJsonHex(res.getHReturn());

            return hReturn;
        } finally {
            LOGGER.debug("eth_call(): {}", hReturn);
        }
    }

    public String estimateGas(CallArgumentsParam args, @Nonnull BlockIdentifierParam bnOrId) {
        ExecutionBlockRetriever.Result result = executionBlockRetriever.retrieveExecutionBlock(bnOrId.getIdentifier());
        Block block = result.getBlock();
        Trie finalState = result.getFinalState();
        RepositorySnapshot snapshot = finalState == null
                ? repositoryLocator.snapshotAt(block.getHeader())
                : new MutableRepository(new TrieStoreImpl(new HashMapDB()), finalState);

        String estimation = null;
        try {
            CallArgumentsToByteArray hexArgs = new CallArgumentsToByteArray(args.toCallArguments());

            TransactionExecutor executor = reversibleTransactionExecutor.estimateGas(
                    block,
                    block.getCoinbase(),
                    hexArgs.getGasPrice(),
                    hexArgs.gasLimitForGasEstimation(gasEstimationCap),
                    hexArgs.getToAddress(),
                    hexArgs.getValue(),
                    hexArgs.getData(),
                    hexArgs.getFromAddress(),
                    snapshot
            );

            estimation = internalEstimateGas(executor.getResult());

            return estimation;
        } finally {
            LOGGER.debug("eth_estimateGas(): {}", estimation);
        }
    }

    public List<Transaction> ethPendingTransactions() {
        return ethModuleWallet.ethPendingTransactions();
    }

    protected String internalEstimateGas(ProgramResult reversibleExecutionResult) {
        long estimatedGas = reversibleExecutionResult.getMovedRemainingGasToChild() ?
                reversibleExecutionResult.getGasUsed() + reversibleExecutionResult.getDeductedRefund() :
                reversibleExecutionResult.getMaxGasUsed();

        if (reversibleExecutionResult.isCallWithValuePerformed()) {
            estimatedGas += GasCost.STIPEND_CALL;
        }

        // ensure not returning blockGasLimit+stipend
        if (estimatedGas > gasEstimationCap) {
            LOGGER.warn("Estimation {} was bigger than cap {}", estimatedGas, gasEstimationCap);
            estimatedGas = gasEstimationCap;
        }

        return HexUtils.toQuantityJsonHex(estimatedGas);
    }

    @Override
    public String sendTransaction(CallArgumentsParam args) {
        return ethModuleTransaction.sendTransaction(args);
    }

    @Override
    public String sendRawTransaction(HexDataParam rawData) {
        return ethModuleTransaction.sendRawTransaction(rawData);
    }

    @Override
    public String sign(String addr, String data) {
        return ethModuleWallet.sign(addr, data);
    }

    public String chainId() {
        return HexUtils.toJsonHex(new byte[]{chainId});
    }

    public String getCode(HexAddressParam address, String blockId) {
        if (blockId == null) {
            throw new NullPointerException();
        }

        String s = null;
        RskAddress addr = address.getAddress();
        try {

            AccountInformationProvider accountInformationProvider = getAccountInformationProvider(blockId);

            if (accountInformationProvider != null) {
                byte[] code = accountInformationProvider.getCode(addr);

                // Code can be null, if there is no account.
                if (code == null) {
                    code = new byte[0];
                }

                s = HexUtils.toUnformattedJsonHex(code);
            }

            return s;
        } finally {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("eth_getCode({}, {}): {}", addr.toHexString(), blockId, s);
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
                    long blockNumber = HexUtils.stringHexToBigInteger(id).longValue();
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

    @VisibleForTesting
    public ProgramResult callConstant(CallArguments args, Block executionBlock) {
        CallArgumentsToByteArray hexArgs = new CallArgumentsToByteArray(args);
        return reversibleTransactionExecutor.executeTransaction(
                executionBlock,
                executionBlock.getCoinbase(),
                hexArgs.getGasPrice(),
                hexArgs.gasLimitForCall(this.gasCallCap),
                hexArgs.getToAddress(),
                hexArgs.getValue(),
                hexArgs.getData(),
                hexArgs.getFromAddress()
        );
    }

    /**
     * Look for { Error("msg") } function, if it matches decode the "msg" param.
     * The 4 first bytes are the function signature.
     *
     * @param res
     * @return revert reason, empty if didnt match.
     */
    public static Optional<String> decodeRevertReason(ProgramResult res) {
        byte[] bytes = res.getHReturn();
        if (bytes == null || bytes.length < 4) {
            return Optional.empty();
        }

        final byte[] signature = copyOfRange(res.getHReturn(), 0, 4);
        if (!Arrays.equals(signature, ERROR_ABI_FUNCTION_SIGNATURE)) {
            return Optional.empty();
        }

        final Object[] decode = ERROR_ABI_FUNCTION.decode(res.getHReturn());
        return decode != null && decode.length > 0 ? Optional.of((String) decode[0]) : Optional.empty();
    }

    private ProgramResult callConstantWithState(CallArguments args, Block executionBlock, Trie state) {
        CallArgumentsToByteArray hexArgs = new CallArgumentsToByteArray(args);
        return reversibleTransactionExecutor.executeTransaction(
                new MutableRepository(new TrieStoreImpl(new HashMapDB()), state),
                executionBlock,
                executionBlock.getCoinbase(),
                hexArgs.getGasPrice(),
                hexArgs.gasLimitForCall(this.gasCallCap),
                hexArgs.getToAddress(),
                hexArgs.getValue(),
                hexArgs.getData(),
                hexArgs.getFromAddress()
        );
    }
}
