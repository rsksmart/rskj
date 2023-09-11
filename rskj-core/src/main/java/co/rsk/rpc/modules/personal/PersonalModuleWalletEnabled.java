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

package co.rsk.rpc.modules.personal;

import co.rsk.config.RskSystemProperties;
import co.rsk.config.WalletAccount;
import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import co.rsk.util.HexUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.*;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDurationParam;
import org.ethereum.rpc.parameters.HexKeyParam;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.TransactionArgumentsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class PersonalModuleWalletEnabled implements PersonalModule {

    private static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private final Ethereum eth;
    private final Wallet wallet;
    private final TransactionPool transactionPool;
    private final RskSystemProperties config;
    private final Constants constants;

    public PersonalModuleWalletEnabled(RskSystemProperties config, Ethereum eth, Wallet wallet, TransactionPool transactionPool) {
        this.config = config;
        this.eth = eth;
        this.wallet = wallet;
        this.transactionPool = transactionPool;
        this.constants = config.getNetworkConstants();
    }

    @Override
    public void init() {
        // dev node has 10 accouts with balance (in rsk-dev.json
        // with seed cow, cow1..cow9
        if (config.getNetworkConstants().seedCowAccounts()) {
            newAccountWithSeed("cow");

            for (int k = 1; k <= 9; k++) {
                newAccountWithSeed("cow" + k);
            }
        }

        // This creates a new account based on a configured secret passphrase,
        // which is then used to set the current miner coinbase address.
        // Generally used for testing, since you usually don't want to store
        // wallets in production for security reasons.
        Account coinbaseAccount = config.localCoinbaseAccount();
        if (coinbaseAccount != null) {
            this.wallet.addAccount(coinbaseAccount);
        }

        // initializes wallet accounts based on configuration
        for (WalletAccount acc : config.walletAccounts()) {
            this.wallet.addAccountWithPrivateKey(Hex.decode(acc.getPrivateKey()));
        }
    }

    @Override
    public String newAccountWithSeed(String seed) {
        String s = null;

        try {
            byte[] address = this.wallet.addAccountWithSeed(seed);

            return s = HexUtils.toJsonHex(address);
        } finally {
            LOGGER.debug("personal_newAccountWithSeed(*****): {}", s);
        }
    }

    @Override
    public String newAccount(String passphrase) {
        String s = null;

        try {
            RskAddress address = this.wallet.addAccount(passphrase);
            // Unlock immediately with no specified duration
            unlockAccount(address, passphrase, null);
            return s = address.toJsonString();
        } finally {
            LOGGER.debug("personal_newAccount(*****): {}", s);
        }
    }

    @Override
    public String[] listAccounts() {
        String[] ret = null;
        try {
            return ret = wallet.getAccountAddressesAsHex();
        } finally {
            LOGGER.debug("personal_listAccounts(): {}", Arrays.toString(ret));
        }
    }

    @Override
    public String importRawKey(HexKeyParam keyParam, String passphrase) {
        String s = null;
        String key = Optional.ofNullable(keyParam).map(HexKeyParam::getHexKey).orElse(null);
        try {
            if (key != null && key.startsWith("0x")) {
                key = key.substring(2);
            }
            RskAddress address = this.wallet.addAccountWithPrivateKey(Hex.decode(key), passphrase);
            unlockAccount(address, passphrase, null);
            return s = address.toJsonString();
        } finally {
            LOGGER.debug("personal_importRawKey(*****): {}", s);
        }
    }

    @Override
    public String sendTransaction(CallArgumentsParam argsParam, String passphrase) throws Exception {
        String s = null;
        CallArguments args = argsParam.toCallArguments();
        try {
            return s = sendTransaction(args, getAccount(args.getFrom(), passphrase));
        } finally {
            LOGGER.debug("eth_sendTransaction({}): {}", args, s);
        }
    }

    @Override
    public boolean unlockAccount(HexAddressParam address, String passphrase, HexDurationParam duration) {
        return unlockAccount(address.getAddress(), passphrase, duration);
    }

    private boolean unlockAccount(RskAddress addr, String passphrase, HexDurationParam duration) {
        long dur = (long) 1000 * 60 * 30;
        if (duration != null && duration.getDuration() != null) {
            try {
                dur = duration.getDuration();
            } catch (Exception e) {
                throw invalidParamError("Can't parse duration param", e);
            }
        }

        return this.wallet.unlockAccount(addr, passphrase, dur);
    }

    @Override
    public boolean lockAccount(HexAddressParam address) {
        return this.wallet.lockAccount(address.getAddress());
    }

    @Override
    public String dumpRawKey(HexAddressParam address) throws Exception {
        String s = null;
        try {
            Account account = wallet.getAccount(address.getAddress());
            if (account == null) {
                throw new Exception("Address private key is locked or could not be found in this node");
            }

            return s = HexUtils.toJsonHex(ByteUtil.toHexString(account.getEcKey().getPrivKeyBytes()));
        } finally {
            LOGGER.debug("personal_dumpRawKey(*****): {}", s);
        }
    }

    private Account getAccount(String from, String passphrase) {
        return wallet.getAccount(new RskAddress(from), passphrase);
    }

    private String sendTransaction(CallArguments args, Account senderAccount) throws Exception {

        if (senderAccount == null) {
            throw new Exception("From address private key could not be found in this node");
        }

        TransactionArguments txArgs = TransactionArgumentsUtil.processArguments(args, transactionPool, senderAccount, config.getNetworkConstants().getChainId());

        Transaction tx = Transaction.builder().withTransactionArguments(txArgs).build();

        tx.sign(senderAccount.getEcKey().getPrivKeyBytes());

        if (!tx.acceptTransactionSignature(constants.getChainId())) {
            throw RskJsonRpcRequestException.invalidParamError(TransactionArgumentsUtil.ERR_INVALID_CHAIN_ID + tx.getChainId());
        }

        TransactionPoolAddResult result = eth.submitTransaction(tx);
        if (!result.transactionsWereAdded()) {
            throw RskJsonRpcRequestException.transactionError(result.getErrorMessage());
        }
        return tx.getHash().toJsonString();
    }

    private String convertFromJsonHexToHex(String x) throws Exception {
        if (!x.startsWith("0x")) {
            throw new Exception("Incorrect hex syntax");
        }

        return x.substring(2);
    }

    private long convertFromJsonHexToLong(String x) throws Exception {
        if (!x.startsWith("0x")) {
            throw new Exception("Incorrect hex syntax");
        }
        return Long.parseLong(x.substring(2), 16);
    }
}
