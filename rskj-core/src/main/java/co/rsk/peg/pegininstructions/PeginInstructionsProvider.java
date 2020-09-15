package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import java.util.List;
import java.util.Optional;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeginInstructionsProvider {

    private static final Logger logger = LoggerFactory.getLogger(PeginInstructionsProvider.class);

    public Optional<PeginInstructions> buildPeginInstructions(BtcTransaction btcTx) throws
        PeginInstructionsException {

        logger.trace("[buildPeginInstructions] Using btc tx {}", btcTx.getHash());

        PeginInstructionsBase peginInstructions;
        byte[] opReturnOutputData;

        try {
            opReturnOutputData = extractOpReturnData(btcTx);
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

    protected static byte[] extractOpReturnData(BtcTransaction btcTx)
        throws PeginInstructionsException {
        byte[] data = new byte[]{};
        int opReturnOccurrences = 0;

        logger.trace("[extractOpReturnData] Getting OP_RETURN data for btc tx: {}", btcTx.getHash());

        for (int i=0; i<btcTx.getOutputs().size(); i++) {
            List<ScriptChunk> chunksByOutput = btcTx.getOutput(i).getScriptPubKey().getChunks();
            if (chunksByOutput.get(0).opcode == ScriptOpCodes.OP_RETURN) {
                if (chunksByOutput.size() > 1) {
                    data = btcTx.getOutput(i).getScriptPubKey().getChunks().get(1).data;
                    opReturnOccurrences++;
                } else {
                    // OP_RETURN exist but data is empty
                    opReturnOccurrences++;
                    data = null;
                }
            }
        }

        if (opReturnOccurrences == 0) {
            String message = String.format("No OP_RETURN output found for tx %s", btcTx.getHash());
            throw new NoOpReturnException(message);
        }

        if (opReturnOccurrences > 1) {
            String message = String.format("Only one output with OP_RETURN is allowed. Found %d",
                opReturnOccurrences);
            logger.debug("[extractOpReturnData] {}", message);
            throw new PeginInstructionsException(message);
        }

        if (data == null) {
            String message = "Empty OP_RETURN data found";
            logger.debug("[extractOpReturnData] {}", message);
            throw new PeginInstructionsException(message);
        }

        return data;
    }
}
