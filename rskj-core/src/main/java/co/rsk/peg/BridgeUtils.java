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

package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.bitcoin.RskAllowUnconfirmedCoinSelector;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Transaction;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * @author Oscar Guindzberg
 */
public class BridgeUtils {

    private static final Logger logger = LoggerFactory.getLogger("BridgeUtils");

    public static StoredBlock getStoredBlockAtHeight(BtcBlockStore blockStore, int height) throws BlockStoreException {
        StoredBlock storedBlock = blockStore.getChainHead();
        int headHeight = storedBlock.getHeight();
        if (height > headHeight) {
            return null;
        }
        for (int i = 0; i < (headHeight - height); i++) {
            if (storedBlock == null) {
                return null;
            }

            Sha256Hash prevBlockHash = storedBlock.getHeader().getPrevBlockHash();
            storedBlock = blockStore.get(prevBlockHash);
        }
        if (storedBlock != null) {
            if (storedBlock.getHeight() != height) {
                throw new IllegalStateException("Block height is " + storedBlock.getHeight() + " but should be " + headHeight);
            }
            return storedBlock;
        } else {
            return null;
        }
    }

    public static Wallet getFederationNoSpendWallet(Context btcContext, Federation federation) {
        return getFederationsNoSpendWallet(btcContext, Arrays.asList(federation));
    }

    public static Wallet getFederationsNoSpendWallet(Context btcContext, List<Federation> federations) {
        Wallet wallet = new BridgeBtcWallet(btcContext, federations);
        federations.forEach(federation -> wallet.addWatchedAddress(federation.getAddress(), federation.getCreationTime().toEpochMilli()));
        return wallet;
    }

    public static Wallet getFederationSpendWallet(Context btcContext, Federation federation, List<UTXO> utxos) {
        return getFederationsSpendWallet(btcContext, Arrays.asList(federation), utxos);
    }

    public static Wallet getFederationsSpendWallet(Context btcContext, List<Federation> federations, List<UTXO> utxos) {
        Wallet wallet = new BridgeBtcWallet(btcContext, federations);

        RskUTXOProvider utxoProvider = new RskUTXOProvider(btcContext.getParams(), utxos);
        wallet.setUTXOProvider(utxoProvider);
        federations.stream().forEach(federation -> {
            wallet.addWatchedAddress(federation.getAddress(), federation.getCreationTime().toEpochMilli());
        });
        wallet.setCoinSelector(new RskAllowUnconfirmedCoinSelector());
        return wallet;
    }

    private static boolean scriptCorrectlySpendsTx(BtcTransaction tx, int index, Script script) {
        try {
            TransactionInput txInput = tx.getInput(index);
            txInput.getScriptSig().correctlySpends(tx, index, script, Script.ALL_VERIFY_FLAGS);
            return true;
        } catch (ScriptException se) {
            return false;
        }
    }

    public static boolean isLockTx(BtcTransaction tx, List<Federation> federations, Context btcContext, BridgeConstants bridgeConstants) {
        // First, check tx is not a typical release tx (tx spending from the any of the federation addresses and
        // optionally sending some change to any of the federation addresses)
        for (int i = 0; i < tx.getInputs().size(); i++) {
            final int index = i;
            if (federations.stream().anyMatch(federation -> scriptCorrectlySpendsTx(tx, index, federation.getP2SHScript()))) {
                return false;
            }
        }

        Wallet federationsWallet = BridgeUtils.getFederationsNoSpendWallet(btcContext, federations);
        Coin valueSentToMe = tx.getValueSentToMe(federationsWallet);

        int valueSentToMeSignum = valueSentToMe.signum();
        if (valueSentToMe.isLessThan(bridgeConstants.getMinimumLockTxValue())) {
            logger.warn("Someone sent to the federation less than {} satoshis", bridgeConstants.getMinimumLockTxValue());
        }
        return (valueSentToMeSignum > 0 && !valueSentToMe.isLessThan(bridgeConstants.getMinimumLockTxValue()));
    }

    public static boolean isLockTx(BtcTransaction tx, Federation federation, Context btcContext, BridgeConstants bridgeConstants) {
        return isLockTx(tx, Arrays.asList(federation), btcContext, bridgeConstants);
    }

    public static boolean isReleaseTx(BtcTransaction tx, Federation federation, BridgeConstants bridgeConstants) {
        int i = 0;
        for (TransactionInput transactionInput : tx.getInputs()) {
            try {
                transactionInput.getScriptSig().correctlySpends(tx, i, federation.getP2SHScript(), Script.ALL_VERIFY_FLAGS);
                // There is an input spending from the federation address, this is a release tx
                return true;
            } catch (ScriptException se) {
                // do-nothing, input does not spends from the federation address
            }
            i++;
        }
        return false;
    }

    public static boolean isMigrationTx(BtcTransaction btcTx, Federation activeFederation, Federation retiringFederation, Context btcContext, BridgeConstants bridgeConstants) {
        if (retiringFederation == null) {
            return false;
        }
        boolean moveFromRetiring = isReleaseTx(btcTx, retiringFederation, bridgeConstants);
        boolean moveToActive = isLockTx(btcTx, activeFederation, btcContext, bridgeConstants);

        return moveFromRetiring && moveToActive;
    }

    public static Address recoverBtcAddressFromEthTransaction(org.ethereum.core.Transaction tx, NetworkParameters networkParameters) {
        org.ethereum.crypto.ECKey key = tx.getKey();
        byte[] pubKey = key.getPubKey(true);
        return BtcECKey.fromPublicOnly(pubKey).toAddress(networkParameters);
    }

    public static boolean isFreeBridgeTx(SystemProperties config, Transaction rskTx, long blockNumber) {
        BlockchainNetConfig blockchainConfig = config.getBlockchainConfig();
        RskAddress receiveAddress = rskTx.getReceiveAddress();
        if (receiveAddress.equals(RskAddress.nullAddress())) {
            return false;
        }

        BridgeConstants bridgeConstants = blockchainConfig.getCommonConstants().getBridgeConstants();

        // Temporary assumption: if areBridgeTxsFree() is true then the current federation
        // must be the genesis federation.
        // Once the original federation changes, txs are always paid.
        return PrecompiledContracts.BRIDGE_ADDR.equals(receiveAddress) &&
               blockchainConfig.getConfigForBlock(blockNumber).areBridgeTxsFree() &&
               rskTx.acceptTransactionSignature(config.getBlockchainConfig().getCommonConstants().getChainId()) &&
               (
                       isFromFederateMember(rskTx, bridgeConstants.getGenesisFederation()) ||
                       isFromFederationChangeAuthorizedSender(rskTx, bridgeConstants) ||
                       isFromLockWhitelistChangeAuthorizedSender(rskTx, bridgeConstants) ||
                       isFromFeePerKbChangeAuthorizedSender(rskTx, bridgeConstants)
               );
    }

    public static boolean isFromFederateMember(org.ethereum.core.Transaction rskTx, Federation federation) {
        return federation.hasMemberWithRskAddress(rskTx.getSender().getBytes());
    }

    private static boolean isFromFederationChangeAuthorizedSender(org.ethereum.core.Transaction rskTx, BridgeConstants bridgeConfiguration) {
        AddressBasedAuthorizer authorizer = bridgeConfiguration.getFederationChangeAuthorizer();
        return authorizer.isAuthorized(rskTx);
    }

    private static boolean isFromLockWhitelistChangeAuthorizedSender(org.ethereum.core.Transaction rskTx, BridgeConstants bridgeConfiguration) {
        AddressBasedAuthorizer authorizer = bridgeConfiguration.getLockWhitelistChangeAuthorizer();
        return authorizer.isAuthorized(rskTx);
    }

    private static boolean isFromFeePerKbChangeAuthorizedSender(org.ethereum.core.Transaction rskTx, BridgeConstants bridgeConfiguration) {
        AddressBasedAuthorizer authorizer = bridgeConfiguration.getFeePerKbChangeAuthorizer();
        return authorizer.isAuthorized(rskTx);
    }
}
