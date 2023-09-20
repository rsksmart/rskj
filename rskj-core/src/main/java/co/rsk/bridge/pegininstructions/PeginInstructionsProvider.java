package co.rsk.bridge.pegininstructions;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.script.ScriptChunk;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeginInstructionsProvider {

    private static final Logger logger = LoggerFactory.getLogger(PeginInstructionsProvider.class);
    private static final byte[] RSKT_HEX = Hex.decode("52534b54");

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

    protected static byte[] extractOpReturnData(BtcTransaction btcTx) throws PeginInstructionsException {
        logger.trace("[extractOpReturnData] Getting OP_RETURN data for btc tx: {}", btcTx.getHash());

        byte[] data = new byte[]{};
        int opReturnForRskOccurrences = 0;

        for (int i = 0; i < btcTx.getOutputs().size(); i++) {
            TransactionOutput txOutput = btcTx.getOutput(i);
            if(hasOpReturnForRsk(txOutput)) {
                data = txOutput.getScriptPubKey().getChunks().get(1).data;
                opReturnForRskOccurrences++;
            }
        }

        if (opReturnForRskOccurrences == 0) {
            String message = String.format("No OP_RETURN output found for tx %s", btcTx.getHash());
            throw new NoOpReturnException(message);
        }

        if (opReturnForRskOccurrences > 1) {
            String message = String.format("Only one output with OP_RETURN for RSK is allowed. Found %d",
                opReturnForRskOccurrences);
            logger.debug("[extractOpReturnData] {}", message);
            throw new PeginInstructionsException(message);
        }

        return data;
    }

    private static boolean hasOpReturnForRsk(TransactionOutput txOutput) {
        if(txOutput.getScriptPubKey().isOpReturn()) {
            // Check if it has data with `RSKT` prefix
            List<ScriptChunk> chunksByOutput = txOutput.getScriptPubKey().getChunks();
            if (chunksByOutput.size() > 1 &&
                chunksByOutput.get(1).data != null &&
                chunksByOutput.get(1).data.length >= 4) {
                byte[] prefix = Arrays.copyOfRange(chunksByOutput.get(1).data, 0, 4);
                if (Arrays.equals(prefix, RSKT_HEX)) {
                    return true;
                }
            }
        }

        return false;
    }
}
