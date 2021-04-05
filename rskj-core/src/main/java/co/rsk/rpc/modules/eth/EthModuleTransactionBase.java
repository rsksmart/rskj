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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.*;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.GasCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class EthModuleTransactionBase implements EthModuleTransaction {

    private static final String ERR_INVALID_CHAIN_ID = "Invalid chainId: ";

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
    public synchronized String sendTransaction(Web3.CallArguments args) {
        Account account = this.wallet.getAccount(new RskAddress(args.from));
        String s = null;
        try {
            String toAddress = args.to != null ? ByteUtil.toHexString(stringHexToByteArray(args.to)) : null;

            BigInteger value = args.value != null ? TypeConverter.stringNumberAsBigInt(args.value) : BigInteger.ZERO;
            BigInteger gasPrice = args.gasPrice != null ? TypeConverter.stringNumberAsBigInt(args.gasPrice) : BigInteger.ZERO;
            BigInteger gasLimit = args.gas != null ? TypeConverter.stringNumberAsBigInt(args.gas) : BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);

            if (args.data != null && args.data.startsWith("0x")) {
                args.data = args.data.substring(2);
            }

            byte txChainId = hexToChainId(args.chainId);
            if (txChainId == 0) {
                txChainId = constants.getChainId();
            }

            synchronized (transactionPool) {
                BigInteger accountNonce = args.nonce != null ? TypeConverter.stringNumberAsBigInt(args.nonce) : transactionPool.getPendingState().getNonce(account.getAddress());
                Transaction tx = Transaction
                        .builder()
                        .nonce(accountNonce)
                        .gasPrice(gasPrice)
                        .gasLimit(gasLimit)
                        .destination(toAddress == null ? null : Hex.decode(toAddress))
                        .data(args.data == null ? null : Hex.decode(args.data))
                        .chainId(txChainId)
                        .value(value)
                        .build();
                tx.sign(account.getEcKey().getPrivKeyBytes());

                if (!tx.acceptTransactionSignature(constants.getChainId())) {
                    throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_CHAIN_ID + args.chainId);
                }

                TransactionPoolAddResult result = transactionGateway.receiveTransaction(tx.toImmutableTransaction());
                if(!result.transactionsWereAdded()) {
                    throw RskJsonRpcRequestException.transactionError(result.getErrorMessage());
                }

                s = tx.getHash().toJsonString();
            }

            return s;

        } finally {
            LOGGER.debug("eth_sendTransaction({}): {}", args, s);
        }
    }

    @Override
    public String sendRawTransaction(String rawData) {
        String s = null;
        try {
            Transaction tx = new ImmutableTransaction(stringHexToByteArray(rawData));

            if (null == tx.getGasLimit()
                    || null == tx.getGasPrice()
                    || null == tx.getValue()) {
                throw invalidParamError("Missing parameter, gasPrice, gas or value");
            }

            TransactionPoolAddResult result = transactionGateway.receiveTransaction(tx);
            if(!result.transactionsWereAdded()) {
                throw RskJsonRpcRequestException.transactionError(result.getErrorMessage());
            }

            return s = tx.getHash().toJsonString();
        } finally {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("eth_sendRawTransaction({}): {}", rawData, s);
            }
        }
    }

    private static byte hexToChainId(String hex) {
        if (hex == null) {
            return 0;
        }
        try {
            byte[] bytes = TypeConverter.stringHexToByteArray(hex);
            if (bytes.length != 1) {
                throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_CHAIN_ID + hex);
            }

            return bytes[0];
        } catch (Exception e) {
            throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_CHAIN_ID + hex, e);
        }
    }
}