package co.rsk.peg.peginstrategy;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.config.BridgeConstants;
import co.rsk.peg.*;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.RejectedPeginReason;
import co.rsk.peg.whitelist.LockWhitelist;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Concrete strategy. Implements Legacy PegIn version.
 * <p>
 * Created by Kelvin Isievwore on 11/05/2021.
 */
public class PegInVersionLegacy extends PegInVersionAbstractClass implements PegInVersionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(PegInVersionLegacy.class);

    public PegInVersionLegacy(BridgeEventLogger eventLogger,
                              ActivationConfig.ForBlock activations,
                              Repository rskRepository,
                              FederationSupport federationSupport,
                              BridgeSupport bridgeSupport,
                              BridgeStorageProvider provider,
                              BridgeConstants bridgeConstants,
                              Block rskExecutionBlock) {
        super(eventLogger, activations, rskRepository, federationSupport, bridgeSupport, provider, bridgeConstants, rskExecutionBlock);
    }

    @Override
    public void processPeginTransaction(BtcTransaction btcTx,
                                        Transaction rskTx,
                                        int height,
                                        PeginInformation peginInformation,
                                        Coin totalAmount) throws RegisterBtcTransactionException, IOException {

        Address senderBtcAddress = peginInformation.getSenderBtcAddress();
        BtcLockSender.TxSenderAddressType senderBtcAddressType = peginInformation.getSenderBtcAddressType();

        if (!BridgeUtils.txIsProcessableInLegacyVersion(senderBtcAddressType, activations)) {
            logger.warn("[processPeginVersionLegacy] [btcTx:{}] Could not get BtcLockSender from Btc tx", btcTx.getHash());

            if (activations.isActive(ConsensusRule.RSKIP181)) {
                eventLogger.logRejectedPegin(btcTx, RejectedPeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER);
            }

            throw new RegisterBtcTransactionException("Could not get BtcLockSender from Btc tx");
        }

        // Confirm we should process this lock
        if (shouldProcessPegInVersionLegacy(senderBtcAddressType, btcTx, senderBtcAddress, totalAmount, height)) {
            executePegIn(btcTx, peginInformation, totalAmount);
        } else {
            if (activations.isActive(ConsensusRule.RSKIP181)) {
                if (!isTxLockableForLegacyVersion(senderBtcAddressType, btcTx, senderBtcAddress)) {
                    eventLogger.logRejectedPegin(btcTx, RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER);
                } else if (!verifyLockDoesNotSurpassLockingCap(btcTx, totalAmount)) {
                    eventLogger.logRejectedPegin(btcTx, RejectedPeginReason.PEGIN_CAP_SURPASSED);
                }
            }

            generateRejectionRelease(btcTx, senderBtcAddress, rskTx, totalAmount);
        }

    }

    private boolean shouldProcessPegInVersionLegacy(BtcLockSender.TxSenderAddressType txSenderAddressType, BtcTransaction btcTx,
                                                    Address senderBtcAddress, Coin totalAmount, int height) {
        return isTxLockableForLegacyVersion(txSenderAddressType, btcTx, senderBtcAddress) &&
                verifyLockSenderIsWhitelisted(senderBtcAddress, totalAmount, height) &&
                verifyLockDoesNotSurpassLockingCap(btcTx, totalAmount);
    }

    private boolean verifyLockSenderIsWhitelisted(Address senderBtcAddress, Coin totalAmount, int height) {
        // If the address is not whitelisted, then return the funds
        // using the exact same utxos sent to us.
        // That is, build a release transaction and get it in the release transaction set.
        // Otherwise, transfer SBTC to the sender of the BTC
        // The RSK account to update is the one that matches the pubkey "spent" on the first bitcoin tx input
        LockWhitelist lockWhitelist = provider.getLockWhitelist();
        if (!lockWhitelist.isWhitelistedFor(senderBtcAddress, totalAmount, height)) {
            logger.info("Rejecting lock. Address {} is not whitelisted.", senderBtcAddress);
            return false;
        }

        // Consume this whitelisted address
        lockWhitelist.consume(senderBtcAddress);

        return true;
    }

    private boolean isTxLockableForLegacyVersion(BtcLockSender.TxSenderAddressType txSenderAddressType, BtcTransaction btcTx, Address senderBtcAddress) {

        if (txSenderAddressType == BtcLockSender.TxSenderAddressType.P2PKH ||
                (txSenderAddressType == BtcLockSender.TxSenderAddressType.P2SHP2WPKH && activations.isActive(ConsensusRule.RSKIP143))) {
            return true;
        } else {
            logger.warn(
                    "[isTxLockableForLegacyVersion]: [btcTx:{}] Btc tx type not supported: {}, returning funds to sender: {}",
                    btcTx.getHash(),
                    txSenderAddressType,
                    senderBtcAddress
            );
            return false;
        }
    }
}
