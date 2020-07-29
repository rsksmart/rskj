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
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.bitcoin.RskAllowUnconfirmedCoinSelector;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.utils.BtcTransactionFormatUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Transaction;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Oscar Guindzberg
 */
public class BridgeUtils {

    private static final Logger logger = LoggerFactory.getLogger("BridgeUtils");

    public static Wallet getFederationNoSpendWallet(Context btcContext, Federation federation) {
        return getFederationsNoSpendWallet(btcContext, Collections.singletonList(federation));
    }

    public static Wallet getFederationsNoSpendWallet(Context btcContext, List<Federation> federations) {
        Wallet wallet = new BridgeBtcWallet(btcContext, federations);
        federations.forEach(federation -> wallet.addWatchedAddress(federation.getAddress(), federation.getCreationTime().toEpochMilli()));
        return wallet;
    }

    public static Wallet getFederationSpendWallet(Context btcContext, Federation federation, List<UTXO> utxos) {
        return getFederationsSpendWallet(btcContext, Collections.singletonList(federation), utxos);
    }

    public static Wallet getFederationsSpendWallet(Context btcContext, List<Federation> federations, List<UTXO> utxos) {
        Wallet wallet = new BridgeBtcWallet(btcContext, federations);

        RskUTXOProvider utxoProvider = new RskUTXOProvider(btcContext.getParams(), utxos);
        wallet.setUTXOProvider(utxoProvider);
        federations.forEach(federation -> {
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
        return isLockTx(tx, Collections.singletonList(federation), btcContext, bridgeConstants);
    }

    /**
     * It checks if the tx can be processed, if it is sent from a P2PKH address or RSKIP 143 is active
     * @param txType type of the transaction
     * @param activations to identify if certain hardfork is active or not.
     * @return true if this tx can be locked
     */
    public static boolean txIsProcessable(BtcLockSender.TxType txType, ActivationConfig.ForBlock activations) {
        //After RSKIP 143 activation, if a BtcLockSender object could be parsed then it's processable
        return txType == BtcLockSender.TxType.P2PKH || activations.isActive(ConsensusRule.RSKIP143);
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

    /**
     * Return the amount of missing signatures for a tx.
     * @param btcContext Btc context
     * @param btcTx The btc tx to check
     * @return 0 if was signed by the required number of federators, amount of missing signatures otherwise
     */
    public static int countMissingSignatures(Context btcContext, BtcTransaction btcTx) {
        // Check missing signatures for only one input as it is not
        // possible for a federator to leave unsigned inputs in a tx
        Context.propagate(btcContext);
        int unsigned = 0;

        TransactionInput input = btcTx.getInput(0);
        Script scriptSig = input.getScriptSig();
        List<ScriptChunk> chunks = scriptSig.getChunks();

        for (int i = 1; i < chunks.size() - 1; i++) {
            ScriptChunk chunk = chunks.get(i);
            if (!chunk.isOpCode() && chunk.data.length == 0) {
                unsigned++;
            }
        }
        return unsigned;
    }

    /**
     * Checks whether a btc tx has been signed by the required number of federators.
     * @param btcContext Btc context
     * @param btcTx The btc tx to check
     * @return True if was signed by the required number of federators, false otherwise
     */
    public static boolean hasEnoughSignatures(Context btcContext, BtcTransaction btcTx) {
        // When the tx is constructed OP_0 are placed where signature should go.
        // Check all OP_0 have been replaced with actual signatures in all inputs
        Context.propagate(btcContext);
        for (TransactionInput input : btcTx.getInputs()) {
            Script scriptSig = input.getScriptSig();
            List<ScriptChunk> chunks = scriptSig.getChunks();
            for (int i = 1; i < chunks.size(); i++) {
                ScriptChunk chunk = chunks.get(i);
                if (!chunk.isOpCode() && chunk.data.length == 0) {
                    return false;
                }
            }
        }
        return true;
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

    public static Coin getCoinFromBigInteger(BigInteger value) {
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

    public static boolean validateHeightAndConfirmations(int height, int btcBestChainHeight, int acceptableConfirmationsAmount, Sha256Hash btcTxHash) throws Exception {
        // Check there are at least N blocks on top of the supplied height
        if (height < 0) {
            throw new Exception("Height can't be lower than 0");
        }
        int confirmations = btcBestChainHeight - height + 1;
        if (confirmations < acceptableConfirmationsAmount) {
            logger.warn(
                    "Btc Tx {} at least {} confirmations are required, but there are only {} confirmations",
                    btcTxHash,
                    acceptableConfirmationsAmount,
                    confirmations
            );
            return false;
        }
        return true;
    }

    public static Sha256Hash calculateMerkleRoot(NetworkParameters networkParameters, byte[] pmtSerialized, Sha256Hash btcTxHash) throws VerificationException{
        PartialMerkleTree pmt = new PartialMerkleTree(networkParameters, pmtSerialized, 0);
        List<Sha256Hash> hashesInPmt = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashesInPmt);
        if (!hashesInPmt.contains(btcTxHash)) {
            logger.warn("Supplied Btc Tx {} is not in the supplied partial merkle tree", btcTxHash);
            return null;
        }
        return merkleRoot;
    }

    public static void validateInputsCount(byte[] btcTxSerialized, boolean isActiveRskip) throws VerificationException.EmptyInputsOrOutputs {
        if (BtcTransactionFormatUtils.getInputsCount(btcTxSerialized) == 0) {
            if (isActiveRskip) {
                if (BtcTransactionFormatUtils.getInputsCountForSegwit(btcTxSerialized) == 0) {
                    logger.warn("Provided btc segwit tx has no inputs");
                    // this is the exception thrown by co.rsk.bitcoinj.core.BtcTransaction#verify when there are no inputs.
                    throw new VerificationException.EmptyInputsOrOutputs();
                }
            } else {
                logger.warn("Provided btc tx has no inputs ");
                // this is the exception thrown by co.rsk.bitcoinj.core.BtcTransaction#verify when there are no inputs.
                throw new VerificationException.EmptyInputsOrOutputs();
            }
        }
    }

    /**
     * Check if the p2sh multisig scriptsig of the given input was already signed by federatorPublicKey.
     * @param federatorPublicKey The key that may have been used to sign
     * @param sighash the sighash that corresponds to the input
     * @param input The input
     * @return true if the input was already signed by the specified key, false otherwise.
     */
    public static boolean isInputSignedByThisFederator(BtcECKey federatorPublicKey, Sha256Hash sighash, TransactionInput input) {
        List<ScriptChunk> chunks = input.getScriptSig().getChunks();
        for (int j = 1; j < chunks.size() - 1; j++) {
            ScriptChunk chunk = chunks.get(j);

            if (chunk.data.length == 0) {
                continue;
            }

            TransactionSignature sig2 = TransactionSignature.decodeFromBitcoin(chunk.data, false, false);

            if (federatorPublicKey.verify(sighash, sig2)) {
                return true;
            }
        }
        return false;
    }
}
