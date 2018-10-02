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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.ethereum.vm.GasCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;

public class EthModuleTransactionEnabled implements EthModuleTransaction {

    private static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private final RskSystemProperties config;
    private final Ethereum eth;
    private final Wallet wallet;
    private final TransactionPool transactionPool;

    public EthModuleTransactionEnabled(RskSystemProperties config, Ethereum eth, Wallet wallet, TransactionPool transactionPool) {
        this.config = config;
        this.eth = eth;
        this.wallet = wallet;
        this.transactionPool = transactionPool;
    }

    @Override
    public synchronized String sendTransaction(Web3.CallArguments args) {
        Account account = this.wallet.getAccount(new RskAddress(args.from));
        String s = null;
        try {
            String toAddress = args.to != null ? Hex.toHexString(stringHexToByteArray(args.to)) : null;

            BigInteger value = args.value != null ? TypeConverter.stringNumberAsBigInt(args.value) : BigInteger.ZERO;
            BigInteger gasPrice = args.gasPrice != null ? TypeConverter.stringNumberAsBigInt(args.gasPrice) : BigInteger.ZERO;
            BigInteger gasLimit = args.gas != null ? TypeConverter.stringNumberAsBigInt(args.gas) : BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);

            if (args.data != null && args.data.startsWith("0x")) {
                args.data = args.data.substring(2);
            }

            synchronized (transactionPool) {
                BigInteger accountNonce = args.nonce != null ? TypeConverter.stringNumberAsBigInt(args.nonce) : transactionPool.getPendingState().getNonce(account.getAddress());
                Transaction tx = Transaction.create(config, toAddress, value, accountNonce, gasPrice, gasLimit, args.data);
                tx.sign(account.getEcKey().getPrivKeyBytes());
                eth.submitTransaction(tx.toImmutableTransaction());
                s = tx.getHash().toJsonString();
            }
            return s;
        } finally {
            LOGGER.debug("eth_sendTransaction({}): {}", args, s);
        }
    }
}