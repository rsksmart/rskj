package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Peg-in instructions version 2: same layout as version 1, but the optional refund
 * is carried as a script type plus a 20- or 32-byte program, so native segwit and
 * taproot outputs can be named as well as P2PKH and P2SH.
 */
public class PeginInstructionsVersion2 extends PeginInstructionsBase {
    private static final Logger logger = LoggerFactory.getLogger(PeginInstructionsVersion2.class);

    public static final int PROTOCOL_VERSION = 2;
    private static final int NO_REFUND_LENGTH = 25;
    private static final int REFUND_TYPE_INDEX = 25;
    private static final int REFUND_PROGRAM_INDEX = 26;
    private static final int LENGTH_WITH_20_BYTE_PROGRAM = REFUND_PROGRAM_INDEX + 20;
    private static final int LENGTH_WITH_32_BYTE_PROGRAM = REFUND_PROGRAM_INDEX + 32;

    private final NetworkParameters params;
    private Optional<PeginRefundScriptType> btcRefundScriptType = Optional.empty();
    private Optional<Script> btcRefundScript = Optional.empty();

    public PeginInstructionsVersion2(NetworkParameters params) {
        super(PROTOCOL_VERSION);
        this.params = params;
    }

    @Override
    protected void validateDataLength(byte[] data) throws PeginInstructionsParseException {
        if (data.length != NO_REFUND_LENGTH
            && data.length != LENGTH_WITH_20_BYTE_PROGRAM
            && data.length != LENGTH_WITH_32_BYTE_PROGRAM) {
            String message = String.format(
                "[validateDataLength] Invalid data length. Expected %d, %d or %d bytes, received %d",
                NO_REFUND_LENGTH, LENGTH_WITH_20_BYTE_PROGRAM, LENGTH_WITH_32_BYTE_PROGRAM, data.length);
            logger.debug(message);
            throw new PeginInstructionsParseException(message);
        }
    }

    @Override
    protected void parseAdditionalData(byte[] data) throws PeginInstructionsParseException {
        if (data.length == NO_REFUND_LENGTH) {
            this.btcRefundScriptType = Optional.empty();
            this.btcRefundScript = Optional.empty();
            return;
        }

        int typeByte = data[REFUND_TYPE_INDEX] & 0xff;
        PeginRefundScriptType scriptType = PeginRefundScriptType.fromTypeByte(typeByte)
            .orElseThrow(() -> {
                String message = String.format("[parseAdditionalData] Invalid btc refund script type: %d", typeByte);
                logger.debug(message);
                return new PeginInstructionsParseException(message);
            });

        byte[] program = Arrays.copyOfRange(data, REFUND_PROGRAM_INDEX, data.length);
        if (program.length != scriptType.getProgramLength()) {
            String message = String.format(
                "[parseAdditionalData] Invalid program length for %s. Expected %d bytes, received %d",
                scriptType, scriptType.getProgramLength(), program.length);
            logger.debug(message);
            throw new PeginInstructionsParseException(message);
        }

        Script outputScript = scriptType.buildOutputScript(program);
        logger.debug("[parseAdditionalData] Obtained {} BTC refund script: {}", scriptType, outputScript);

        this.btcRefundScriptType = Optional.of(scriptType);
        this.btcRefundScript = Optional.of(outputScript);
    }

    public Optional<PeginRefundScriptType> getBtcRefundScriptType() {
        return btcRefundScriptType;
    }

    public Optional<Script> getBtcRefundScript() {
        return btcRefundScript;
    }

    /**
     * Only P2PKH and P2SH refund outputs have an {@link Address} representation in bitcoinj-thin.
     * Witness-program refunds are exposed through {@link #getBtcRefundScript()} only.
     */
    public Optional<Address> getBtcRefundAddress() {
        if (btcRefundScriptType.filter(PeginRefundScriptType::isLegacy).isEmpty()) {
            return Optional.empty();
        }
        return btcRefundScript.map(script -> script.getToAddress(params, true));
    }
}
