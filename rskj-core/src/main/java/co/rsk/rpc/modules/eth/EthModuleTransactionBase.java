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
import org.ethereum.core.*;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class EthModuleTransactionBase implements EthModuleTransaction {

    protected static final Logger LOGGER = LoggerFactory.getLogger("web3");
    public static final String ERR_COULD_NOT_FIND_ACCOUNT = "Could not find account for address: ";
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
            throw RskJsonRpcRequestException.invalidParamError(ERR_COULD_NOT_FIND_ACCOUNT + args.getFrom());
        }

        String txHash = null;

        try {
            synchronized (transactionPool) {
                Transaction tx = new Transaction(args, getAccountNextNonce(senderAccount),  constants.getChainId());
                tx.sign(senderAccount.getEcKey().getPrivKeyBytes());
                tx.checkInvalidChain(constants, ""+tx.getChainId());
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

    @Nonnull
    private Supplier<String> getAccountNextNonce(Account senderAccount) {
        return () -> transactionPool.getPendingState().getNonce(senderAccount.getAddress()).toString();
    }

    @Override
    public String sendRawTransaction(HexDataParam rawData) {
        String s = null;
        try {
            Transaction tx = new ImmutableTransaction(rawData.getRawDataBytes());
            tx.checkInvalidChain(constants, ""+tx.getChainId());

            TransactionPoolAddResult result = transactionGateway.receiveTransaction(tx);

            if (!result.transactionsWereAdded()) {
                throw RskJsonRpcRequestException.transactionError(result.getErrorMessage());
            }

            return  tx.getHash().toJsonString();
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
}
