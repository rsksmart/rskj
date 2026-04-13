/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import co.rsk.net.TransactionGateway;
import co.rsk.util.RLPException;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.util.TransactionArgumentsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class EthModuleTransactionBase implements EthModuleTransaction {

    protected static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private final Wallet wallet;
    private final TransactionPool transactionPool;
    private final Constants constants;
    private final TransactionGateway transactionGateway;
    @Nullable
    private final ActivationConfig activationConfig;
    @Nullable
    private final Blockchain blockchain;

    public EthModuleTransactionBase(Constants constants, Wallet wallet, TransactionPool transactionPool, TransactionGateway transactionGateway) {
        this(constants, wallet, transactionPool, transactionGateway, null, null);
    }

    public EthModuleTransactionBase(Constants constants, Wallet wallet, TransactionPool transactionPool,
                                     TransactionGateway transactionGateway,
                                     @Nullable ActivationConfig activationConfig,
                                     @Nullable Blockchain blockchain) {
        this.wallet = wallet;
        this.transactionPool = transactionPool;
        this.constants = constants;
        this.transactionGateway = transactionGateway;
        this.activationConfig = activationConfig;
        this.blockchain = blockchain;
    }

    @Override
    public synchronized String sendTransaction(CallArgumentsParam argsParam) {
        CallArguments args = argsParam.toCallArguments();

        if (args.getFrom() == null) {
            throw invalidParamError("from is null");
        }

        Account senderAccount = this.wallet.getAccount(new RskAddress(args.getFrom()));

        if (senderAccount == null) {
            throw RskJsonRpcRequestException.invalidParamError(TransactionArgumentsUtil.ERR_COULD_NOT_FIND_ACCOUNT + args.getFrom());
        }

        String txHash = null;

        try {
            TransactionArguments txArgs = TransactionArgumentsUtil.processArguments(args, constants.getChainId());

            checkTypedTransactionActivation(txArgs.getType());

            synchronized (transactionPool) {
                if (txArgs.getNonce() == null) {
                    txArgs.setNonce(transactionPool.getPendingState().getNonce(senderAccount.getAddress()));
                }
                Transaction tx = Transaction.builder().withTransactionArguments(txArgs).build();
                tx.sign(senderAccount.getEcKey().getPrivKeyBytes());
                if (!tx.acceptTransactionSignature(constants.getChainId())) {
                    throw RskJsonRpcRequestException.invalidParamError(TransactionArgumentsUtil.ERR_INVALID_CHAIN_ID + args.getChainId());
                }
                TransactionPoolAddResult result = transactionGateway.receiveTransaction(tx.toImmutableTransaction());

                if (!result.transactionsWereAdded()) {
                    throw RskJsonRpcRequestException.transactionError(result.getErrorMessage());
                }

                txHash = tx.getHash().toJsonString();
            }

            return txHash;

        } finally {
            LOGGER.debug("eth_sendTransaction({}): {}", args, txHash);
        }
    }

    @Override
    public String sendRawTransaction(HexDataParam rawData) {
        String s = null;
        try {
            Transaction tx = new ImmutableTransaction(rawData.getRawDataBytes());

            if (null == tx.getGasLimit() || null == tx.getGasPrice() || null == tx.getValue()) {
                throw invalidParamError("Missing parameter, gasPrice, gas or value");
            }

            checkTypedTransactionActivation(tx.getType());

            if (!tx.acceptTransactionSignature(constants.getChainId())) {
                throw RskJsonRpcRequestException.invalidParamError(TransactionArgumentsUtil.ERR_INVALID_CHAIN_ID + tx.getChainId());
            }

            TransactionPoolAddResult result = transactionGateway.receiveTransaction(tx);
            if (!result.transactionsWereAdded()) {
                throw RskJsonRpcRequestException.transactionError(result.getErrorMessage());
            }

            return s = tx.getHash().toJsonString();
        } catch (RLPException e) {
            throw invalidParamError("Invalid input: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw invalidParamError("Invalid transaction: " + e.getMessage(), e);
        } finally {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("eth_sendRawTransaction({}): {}", rawData, s);
            }
        }
    }

    /**
     * Checks that the transaction type is allowed by the current activation config at the head block.
     * This provides an early, clear error at the RPC layer before the transaction reaches the pool.
     * Typed transactions (RSKIP543) and Type 1/2 specifically (RSKIP546) must be active.
     * The check is skipped when no {@link ActivationConfig} or {@link Blockchain} was provided.
     */
    private void checkTypedTransactionActivation(@Nullable TransactionType type) {
        if (activationConfig == null || blockchain == null || type == null || type == TransactionType.LEGACY) {
            return;
        }
        long bestBlockNumber = blockchain.getBestBlock().getNumber();
        ActivationConfig.ForBlock activations = activationConfig.forBlock(bestBlockNumber);
        if (!activations.isActive(ConsensusRule.RSKIP543)) {
            throw invalidParamError("Typed transactions (type " + type + ") are not supported before RSKIP-543 activation");
        }
        if ((type == TransactionType.TYPE_1 || type == TransactionType.TYPE_2)
                && !activations.isActive(ConsensusRule.RSKIP546)) {
            throw invalidParamError("Type 1 / Type 2 transactions are not supported before RSKIP-546 activation");
        }
    }

}
