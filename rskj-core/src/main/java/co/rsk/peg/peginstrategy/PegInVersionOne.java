package co.rsk.peg.peginstrategy;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.config.BridgeConstants;
import co.rsk.peg.*;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.RejectedPeginReason;
import co.rsk.peg.utils.UnrefundablePeginReason;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Concrete strategy. Implements PegIn version One.
 * <p>
 * Created by Kelvin Isievwore on 11/05/2021.
 */
public class PegInVersionOne extends PegInVersionAbstractClass implements PegInVersionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(PegInVersionOne.class);

    public PegInVersionOne(BridgeEventLogger eventLogger,
                           ActivationConfig.ForBlock activations,
                           Repository rskRepository,
                           FederationSupport federationSupport,
                           BridgeStorageProvider provider,
                           BridgeConstants bridgeConstants,
                           Block rskExecutionBlock) {
        super(eventLogger, activations, rskRepository, federationSupport, provider, bridgeConstants, rskExecutionBlock);
    }

    @Override
    public void processPeginTransaction(BtcTransaction btcTx,
                                        Transaction rskTx,
                                        int height,
                                        PeginInformation peginInformation,
                                        Coin totalAmount) throws RegisterBtcTransactionException, IOException {

        if (!activations.isActive(ConsensusRule.RSKIP170)) {
            throw new RegisterBtcTransactionException("Can't process version 1 peg-ins before RSKIP 170 activation");
        }

        // Confirm we should process this lock
        if (verifyLockDoesNotSurpassLockingCap(btcTx, totalAmount)) {
            executePegIn(btcTx, peginInformation, totalAmount);
        } else {
            logger.debug("[processPegInVersion1] Peg-in attempt surpasses locking cap. Amount attempted to lock: {}", totalAmount);

            if (activations.isActive(ConsensusRule.RSKIP181)) {
                eventLogger.logRejectedPegin(btcTx, RejectedPeginReason.PEGIN_CAP_SURPASSED);
            }

            refundTxSender(btcTx, rskTx, peginInformation, totalAmount);
        }
    }

    private void refundTxSender(
            BtcTransaction btcTx,
            Transaction rskTx,
            PeginInformation peginInformation,
            Coin amount) throws IOException {

        Address btcRefundAddress = peginInformation.getBtcRefundAddress();
        if (btcRefundAddress != null) {
            generateRejectionRelease(btcTx, btcRefundAddress, rskTx, amount);
        } else {
            logger.debug("[refundTxSender] No btc refund address provided, couldn't get sender address either. Can't refund");

            if (activations.isActive(ConsensusRule.RSKIP181)) {
                if (peginInformation.getProtocolVersion() == 1) {
                    eventLogger.logUnrefundablePegin(btcTx, UnrefundablePeginReason.PEGIN_V1_REFUND_ADDRESS_NOT_SET);
                } else {
                    eventLogger.logUnrefundablePegin(btcTx, UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER);
                }
            }
        }
    }
}
