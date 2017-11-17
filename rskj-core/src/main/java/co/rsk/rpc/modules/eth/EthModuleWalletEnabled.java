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

import co.rsk.core.Wallet;
import org.ethereum.core.Account;
import org.ethereum.core.PendingState;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.exception.JsonRpcInvalidParamException;
import org.ethereum.vm.GasCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Arrays;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;

public class EthModuleWalletEnabled implements EthModuleWallet {

    private static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private final Ethereum eth;
    private final Wallet wallet;

    public EthModuleWalletEnabled(Ethereum eth, Wallet wallet) {
        this.eth = eth;
        this.wallet = wallet;
    }

    @Override
    public String sendTransaction(Web3.CallArguments args) {
        Account account = this.getAccount(args.from);
        String s = null;
        try {
            if (account == null)
                throw new JsonRpcInvalidParamException("From address private key could not be found in this node");

            String toAddress = args.to != null ? Hex.toHexString(stringHexToByteArray(args.to)) : null;

            BigInteger value = args.value != null ? TypeConverter.stringNumberAsBigInt(args.value) : BigInteger.ZERO;
            BigInteger gasPrice = args.gasPrice != null ? TypeConverter.stringNumberAsBigInt(args.gasPrice) : BigInteger.ZERO;
            BigInteger gasLimit = args.gas != null ? TypeConverter.stringNumberAsBigInt(args.gas) : BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);

            if (args.data != null && args.data.startsWith("0x"))
                args.data = args.data.substring(2);

            // TODO inject PendingState through constructor if necessary
            PendingState pendingState = eth.getWorldManager().getPendingState();
            synchronized (pendingState) {
                BigInteger accountNonce = args.nonce != null ? TypeConverter.stringNumberAsBigInt(args.nonce) : (pendingState.getRepository().getNonce(account.getAddress()));
                Transaction tx = Transaction.create(toAddress, value, accountNonce, gasPrice, gasLimit, args.data);
                tx.sign(account.getEcKey().getPrivKeyBytes());
                eth.submitTransaction(tx.toImmutableTransaction());
                s = TypeConverter.toJsonHex(tx.getHash());
            }
            return s;
        } finally {
            LOGGER.debug("eth_sendTransaction({}): {}", args, s);
        }
    }

    @Override
    public String sign(String addr, String data) {
        String s = null;
        try {
            Account account = this.wallet.getAccount(stringHexToByteArray(addr));
            if (account == null)
                throw new JsonRpcInvalidParamException("Account not found");

            return s = this.sign(data, account.getEcKey());
        } finally {
            LOGGER.debug("eth_sign({}, {}): {}", addr, data, s);
        }
    }

    @Override
    public String[] accounts() {
        String[] s = null;
        try {
            return s = wallet.getAccountAddressesAsHex();
        } finally {
            LOGGER.debug("eth_accounts(): " + Arrays.toString(s));
        }
    }

    private Account getAccount(String address) {
        return this.wallet.getAccount(stringHexToByteArray(address));
    }

    private String sign(String data, ECKey ecKey) {
        byte[] dataHash = TypeConverter.stringHexToByteArray(data);
        ECKey.ECDSASignature signature = ecKey.sign(dataHash);

        String signatureAsString = signature.r.toString() + signature.s.toString() + signature.v;

        return TypeConverter.toJsonHex(signatureAsString);
    }
}