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
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.bitcoin.RskAllowUnconfirmedCoinSelector;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Transaction;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Oscar Guindzberg
 */
public class BridgeUtils {

    private static final Logger logger = LoggerFactory.getLogger("BridgeUtils");

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

    /**
     * Indicates whether a tx is a valid lock tx or not, checking the first input's script sig
     * @param tx
     * @return
     */
    public static boolean isValidLockTx(BtcTransaction tx) {
        if (tx.getInputs().size() == 0) {
            return false;
        }
        // This indicates that the tx is a P2PKH transaction which is the only one we support for now
        return tx.getInput(0).getScriptSig().getChunks().size() == 2;
    }

    /**
     * Will return a valid scriptsig for the first input
     * @param tx
     * @return
     */
    public static Optional<Script> getFirstInputScriptSig(BtcTransaction tx) {
        if (!isValidLockTx(tx)) {
            return Optional.empty();
        }
        return Optional.of(tx.getInput(0).getScriptSig());
    }

    /**
     * It checks if the tx doesn't spend any of the federations' funds and if it sends more than
     * the minimum ({@see BridgeConstants::getMinimumLockTxValue}) to any of the federations
     * @param tx the BTC transaction to check
     * @param federations the active federations
     * @param btcContext the BTC Context
     * @param bridgeConstants the Bridge constants
     * @return true if this is a valid lock transaction
     */
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
            logger.warn("[btctx:{}]Someone sent to the federation less than {} satoshis", tx.getHash(), bridgeConstants.getMinimumLockTxValue());
        }
        return (valueSentToMeSignum > 0 && !valueSentToMe.isLessThan(bridgeConstants.getMinimumLockTxValue()));
    }

    public static boolean isLockTx(BtcTransaction tx, Federation federation, Context btcContext, BridgeConstants bridgeConstants) {
        return isLockTx(tx, Arrays.asList(federation), btcContext, bridgeConstants);
    }

    private static boolean isReleaseTx(BtcTransaction tx, Federation federation) {
        return isReleaseTx(tx, Collections.singletonList(federation));
    }

    public static boolean isReleaseTx(BtcTransaction tx, List<Federation> federations) {
        int inputsSize = tx.getInputs().size();
        for (int i = 0; i < inputsSize; i++) {
            final int inputIndex = i;
            if (federations.stream().map(Federation::getP2SHScript).anyMatch(federationPayScript -> scriptCorrectlySpendsTx(tx, inputIndex, federationPayScript))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isMigrationTx(BtcTransaction btcTx, Federation activeFederation, Federation retiringFederation, Context btcContext, BridgeConstants bridgeConstants) {
        if (retiringFederation == null) {
            return false;
        }
        boolean moveFromRetiring = isReleaseTx(btcTx, retiringFederation);
        boolean moveToActive = isLockTx(btcTx, activeFederation, btcContext, bridgeConstants);

        return moveFromRetiring && moveToActive;
    }

    public static Address recoverBtcAddressFromEthTransaction(org.ethereum.core.Transaction tx, NetworkParameters networkParameters) {
        org.ethereum.crypto.ECKey key = tx.getKey();
        byte[] pubKey = key.getPubKey(true);
        return BtcECKey.fromPublicOnly(pubKey).toAddress(networkParameters);
    }

    public static boolean isFreeBridgeTx(Transaction rskTx, Constants constants, ActivationConfig.ForBlock activations) {
        RskAddress receiveAddress = rskTx.getReceiveAddress();
        if (receiveAddress.equals(RskAddress.nullAddress())) {
            return false;
        }

        BridgeConstants bridgeConstants = constants.getBridgeConstants();

        // Temporary assumption: if areBridgeTxsFree() is true then the current federation
        // must be the genesis federation.
        // Once the original federation changes, txs are always paid.
        return PrecompiledContracts.BRIDGE_ADDR.equals(receiveAddress) &&
               !activations.isActive(ConsensusRule.ARE_BRIDGE_TXS_PAID) &&
               rskTx.acceptTransactionSignature(constants.getChainId()) &&
               (
                       isFromFederateMember(rskTx, bridgeConstants.getGenesisFederation()) ||
                       isFromFederationChangeAuthorizedSender(rskTx, bridgeConstants) ||
                       isFromLockWhitelistChangeAuthorizedSender(rskTx, bridgeConstants) ||
                       isFromFeePerKbChangeAuthorizedSender(rskTx, bridgeConstants)
               );
    }

    /**
     * Indicates if the provided tx was generated from a contract
     * @param rskTx
     * @return
     */
    public static boolean isContractTx(Transaction rskTx) {
        // TODO: this should be refactored to provide a more robust way of checking the transaction origin
        return rskTx.getClass() == org.ethereum.vm.program.InternalTransaction.class;
    }

    public static boolean isFromFederateMember(org.ethereum.core.Transaction rskTx, Federation federation) {
        return federation.hasMemberWithRskAddress(rskTx.getSender().getBytes());
    }

    public static Coin getCoinFromBigInteger(BigInteger value) throws BridgeIllegalArgumentException {
        if (value == null) {
            throw new BridgeIllegalArgumentException("value cannot be null");
        }
        try {
            return Coin.valueOf(value.longValueExact());
        } catch(ArithmeticException e) {
            throw new BridgeIllegalArgumentException(e.getMessage(), e);
        }
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
