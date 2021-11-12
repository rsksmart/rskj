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

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

import org.ethereum.config.Constants;
import org.ethereum.core.Account;
import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionArguments;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.TransactionPoolAddResult;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.TransactionArgumentsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import co.rsk.net.TransactionGateway;

public class EthModuleTransactionBase implements EthModuleTransaction {

	protected static final Logger LOGGER = LoggerFactory.getLogger("web3");

	private final Wallet wallet;
	private final TransactionPool transactionPool;
	private final Constants constants;
	private final TransactionGateway transactionGateway;

	public EthModuleTransactionBase(Constants constants, Wallet wallet, TransactionPool transactionPool, TransactionGateway transactionGateway) {
		this.wallet = wallet;
		this.transactionPool = transactionPool;
		this.constants = constants;
		this.transactionGateway = transactionGateway;
	}

	@Override
	public synchronized String sendTransaction(CallArguments args) {

		Account senderAccount = this.wallet.getAccount(new RskAddress(args.getFrom()));

		if (senderAccount == null) {
			throw RskJsonRpcRequestException.invalidParamError(TransactionArgumentsUtil.ERR_COULD_NOT_FIND_ACCOUNT + args.getFrom());
		}

		String txHash = null;

		try {

			synchronized (transactionPool) {

				TransactionArguments txArgs = TransactionArgumentsUtil.processArguments(args, transactionPool, senderAccount, constants.getChainId());

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
	public String sendRawTransaction(String rawData) {
		String s = null;
		try {
			Transaction tx = new ImmutableTransaction(stringHexToByteArray(rawData));

			if (null == tx.getGasLimit() || null == tx.getGasPrice() || null == tx.getValue()) {
				throw invalidParamError("Missing parameter, gasPrice, gas or value");
			}

			TransactionPoolAddResult result = transactionGateway.receiveTransaction(tx);
			if (!result.transactionsWereAdded()) {
				throw RskJsonRpcRequestException.transactionError(result.getErrorMessage());
			}

			return s = tx.getHash().toJsonString();
		} finally {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("eth_sendRawTransaction({}): {}", rawData, s);
			}
		}
	}


}