package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.LegacyAddress;
import co.rsk.core.RskAddress;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructions;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsVersion1;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeginInformation {

    private static final Logger logger = LoggerFactory.getLogger(PeginInformation.class);

    private final BtcLockSenderProvider btcLockSenderProvider;
    private final PeginInstructionsProvider peginInstructionsProvider;
    private final ActivationConfig.ForBlock activations;

    private int protocolVersion;
    private RskAddress rskDestinationAddress;
    private LegacyAddress btcRefundAddress;
    private LegacyAddress senderBtcAddress;
    private TxSenderAddressType senderBtcAddressType;

    public PeginInformation(
        BtcLockSenderProvider btcLockSenderProvider,
        PeginInstructionsProvider peginInstructionsProvider,
        ActivationConfig.ForBlock activations) {
        this.btcLockSenderProvider = btcLockSenderProvider;
        this.peginInstructionsProvider = peginInstructionsProvider;
        this.activations = activations;
        this.protocolVersion = -1; // Set an invalid value by default
        this.senderBtcAddressType = TxSenderAddressType.UNKNOWN;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public RskAddress getRskDestinationAddress() {
        return this.rskDestinationAddress;
    }

    public LegacyAddress getBtcRefundAddress() {
        return this.btcRefundAddress;
    }

    public LegacyAddress getSenderBtcAddress() {
        return this.senderBtcAddress;
    }

    public TxSenderAddressType getSenderBtcAddressType() {
        return this.senderBtcAddressType;
    }

    public void parse(BtcTransaction btcTx) throws PeginInstructionsException {
        logger.trace("[parse] Trying to parse peg-in information from btc tx {}", btcTx.getHash());

        // Get information from tx sender first
        Optional<BtcLockSender> btcLockSenderOptional = btcLockSenderProvider.tryGetBtcLockSender(btcTx);
        if (btcLockSenderOptional.isPresent()) {
            BtcLockSender btcLockSender = btcLockSenderOptional.get();
            parseFromBtcLockSender(btcLockSender);
        }

        // If HF is active and peg-in instructions were provided then override the info obtained from BtcLockSender
        Optional<PeginInstructions> peginInstructionsOptional = Optional.empty();
        if (activations.isActive(ConsensusRule.RSKIP170)) {
            peginInstructionsOptional = peginInstructionsProvider.buildPeginInstructions(btcTx);
            if (peginInstructionsOptional.isPresent()) {
                PeginInstructions peginInstructions = peginInstructionsOptional.get();
                parseFromPeginInstructions(peginInstructions);
            }
        }

        // If BtcLockSender could not be parsed and peg-in instructions were not provided, then this tx can't be processed
        if(!btcLockSenderOptional.isPresent() && !peginInstructionsOptional.isPresent()) {
            String message = String.format("Could not get peg-in information for tx %s", btcTx.getHash());
            logger.warn("[parse] {}", message);
            throw new PeginInstructionsException(message);
        }
    }

    private void parseFromBtcLockSender(BtcLockSender btcLockSender) {
        this.protocolVersion = 0;
        this.rskDestinationAddress = btcLockSender.getRskAddress();
        this.btcRefundAddress = btcLockSender.getBTCAddress();
        this.senderBtcAddress = btcLockSender.getBTCAddress();
        this.senderBtcAddressType = btcLockSender.getTxSenderAddressType();

        logger.trace("[parseFromBtcLockSender] Protocol version: {}", this.protocolVersion);
        logger.trace("[parseFromBtcLockSender] RSK destination address: {}", btcLockSender.getRskAddress());
        logger.trace("[parseFromBtcLockSender] BTC refund address: {}", btcLockSender.getBTCAddress());
        logger.trace("[parseFromBtcLockSender] Sender BTC address: {}", btcLockSender.getBTCAddress());
        logger.trace("[parseFromBtcLockSender] Sender BTC address type: {}", btcLockSender.getTxSenderAddressType());
    }

    private void parseFromPeginInstructions(PeginInstructions peginInstructions)  throws PeginInstructionsException {
        this.protocolVersion = peginInstructions.getProtocolVersion();
        this.rskDestinationAddress = peginInstructions.getRskDestinationAddress();
        logger.trace("[parseFromPeginInstructions] Protocol version: {}", peginInstructions.getProtocolVersion());
        logger.trace("[parseFromPeginInstructions] RSK destination address: {}", peginInstructions.getRskDestinationAddress());

        switch (protocolVersion) {
            case 1:
                PeginInstructionsVersion1 peginInstructionsV1 = (PeginInstructionsVersion1) peginInstructions;
                parseFromPeginInstructionsVersion1(peginInstructionsV1);
                break;
            default:
                String message = String.format("Invalid protocol version: %d", protocolVersion);
                logger.warn("[parseFromPeginInstructions] {}", message);
                throw new PeginInstructionsException(message);
        }
    }

    private void parseFromPeginInstructionsVersion1(PeginInstructionsVersion1 peginInstructions) {
        Optional<LegacyAddress> btcRefundAddressOptional = peginInstructions.getBtcRefundAddress();
        if (btcRefundAddressOptional.isPresent()) {
            this.btcRefundAddress = btcRefundAddressOptional.get();
            logger.trace("[parseFromPeginInstructionsVersion1] BTC refund address: {}", btcRefundAddressOptional.get());
        }
    }
}
