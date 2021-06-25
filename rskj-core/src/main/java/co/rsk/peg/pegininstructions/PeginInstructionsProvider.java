package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.peg.utils.OpReturnUtils;
import java.util.Optional;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeginInstructionsProvider {

    private static final Logger logger = LoggerFactory.getLogger(PeginInstructionsProvider.class);

    public Optional<PeginInstructions> buildPeginInstructions(BtcTransaction btcTx) throws PeginInstructionsException {
        logger.trace("[buildPeginInstructions] Using btc tx {}", btcTx.getHash());

        PeginInstructionsBase peginInstructions;
        byte[] opReturnOutputData;

        try {
            opReturnOutputData = OpReturnUtils.extractPegInOpReturnData(btcTx);
        } catch (NoOpReturnException e) {
            logger.trace("[buildPeginInstructions] {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            String message = String.format("Btc tx: %s has an invalid OP_RETURN structure", btcTx.getHash());
            logger.debug(message);
            throw new PeginInstructionsException(message, e);
        }

        logger.trace("[buildPeginInstructions] OP_RETURN data: {}", Hex.toHexString(opReturnOutputData));

        int protocolVersion = PeginInstructionsBase.extractProtocolVersion(opReturnOutputData);

        switch (protocolVersion) {
            case 1:
                logger.trace("[buildPeginInstructions] Going to build peginInstructions version 1..");
                peginInstructions = new PeginInstructionsVersion1(btcTx.getParams());
                break;
            default:
                logger.debug("[buildPeginInstructions] Invalid protocol version given: {}", protocolVersion);
                throw new PeginInstructionsException("Invalid protocol version");
        }

        peginInstructions.parse(opReturnOutputData);
        logger.trace("[buildPeginInstructions] Successfully created peginInstructions: {}",
            peginInstructions.getClass());

        return Optional.of(peginInstructions);
    }
}
