package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.peg.utils.BtcTransactionFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeginInstructionsProvider {

    private static final Logger logger = LoggerFactory.getLogger(PeginInstructionsProvider.class);

    public PeginInstructionsBase buildPeginInstructions(BtcTransaction btcTx) throws Exception {
        PeginInstructionsBase peginInstructions;
        byte[] opReturnOutput = BtcTransactionFormatUtils.extractOpReturnData(btcTx);

        if (opReturnOutput == null || opReturnOutput.length == 0) {
            String message = "Empty OP_RETURN data found";
            logger.debug("[getOpReturnOutput] {}", message);
            throw new PeginInstructionsException(message);
        }

        int protocolVersion = PeginInstructionsBase.extractProtocolVersion(opReturnOutput);

        switch (protocolVersion) {
            case 1:
                PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(btcTx.getParams());
                peginInstructionsVersion1.parse(opReturnOutput);
                peginInstructions = peginInstructionsVersion1;
                break;
            default:
                logger.debug("[buildPeginInstructions] Invalid protocol version given");
                throw new PeginInstructionsException("Invalid protocol version");
        }

        return peginInstructions;
    }
}
