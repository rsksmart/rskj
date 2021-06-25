package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.peg.pegininstructions.NoOpReturnException;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import java.util.Arrays;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpReturnUtils {

    private static final Logger logger = LoggerFactory.getLogger(OpReturnUtils.class);
    public static final byte[] PEGIN_OUTPUT_IDENTIFIER = Hex.decode("52534b54"); // 'RSKT' in hexa
    public static final byte[] PEGOUT_OUTPUT_IDENTIFIER = Hex.decode("52534b4f"); // 'RSKO' in hexa

    public static byte[] extractPegInOpReturnData(BtcTransaction btcTx) throws PeginInstructionsException {
        return extractOpReturnData(btcTx, PEGIN_OUTPUT_IDENTIFIER);
    }

    public static byte[] extractPegOutOpReturnData(BtcTransaction btcTx) throws PeginInstructionsException {
        return extractOpReturnData(btcTx, PEGOUT_OUTPUT_IDENTIFIER);
    }

    public static Script createPegOutOpReturnScriptForRsk() {
        return ScriptBuilder.createOpReturnScript(PEGOUT_OUTPUT_IDENTIFIER);
    }

    private static byte[] extractOpReturnData(BtcTransaction btcTx, byte[] outputIdentifier) throws PeginInstructionsException {
        logger.trace("[extractOpReturnData] Getting OP_RETURN data for btc tx: {}", btcTx.getHash());

        byte[] data = new byte[]{};
        int opReturnForRskOccurrences = 0;

        for (int i = 0; i < btcTx.getOutputs().size(); i++) {
            TransactionOutput txOutput = btcTx.getOutput(i);
            if(hasOpReturnForRsk(txOutput, outputIdentifier)) {
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

    private static boolean hasOpReturnForRsk(TransactionOutput txOutput, byte[] outputIdentifier) {
        if(txOutput.getScriptPubKey().isOpReturn()) {
            // Check if it has data with the output identifier
            List<ScriptChunk> chunksByOutput = txOutput.getScriptPubKey().getChunks();
            if (chunksByOutput.size() > 1 &&
                chunksByOutput.get(1).data != null &&
                chunksByOutput.get(1).data.length >= outputIdentifier.length) {
                byte[] prefix = Arrays.copyOfRange(chunksByOutput.get(1).data, 0, outputIdentifier.length);
                if (Arrays.equals(prefix, outputIdentifier)) {
                    return true;
                }
            }
        }

        return false;
    }
}
