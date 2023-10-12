package co.rsk.bridge.pegininstructions;

import co.rsk.core.RskAddress;
import java.util.Arrays;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PeginInstructionsBase implements PeginInstructions {

    private static final Logger logger = LoggerFactory.getLogger(PeginInstructionsBase.class);
    private final int protocolVersion;
    protected RskAddress rskDestinationAddress;

    protected PeginInstructionsBase(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    private RskAddress getRskDestinationAddressFromData(byte[] data) {
        byte[] rskDestinationAddressBytes = Arrays.copyOfRange(data, 5, 25);
        return new RskAddress(rskDestinationAddressBytes);
    }

    protected abstract void validateDataLength(byte[] data) throws PeginInstructionsParseException;

    protected abstract void parseAdditionalData(byte[] data) throws PeginInstructionsParseException;

    public static int extractProtocolVersion(byte[] data) throws PeginInstructionsParseException {
        if (data == null || data.length < 5) {
            String message;

            if (data == null) {
                message = "Provided data is null";
            } else {
                message = String.format("Invalid data given. Expected at least 5 bytes, " +
                    "received %d", data.length);
            }

            logger.debug("[extractProtocolVersion] {}", message);
            throw new PeginInstructionsParseException(message);
        }

        byte[] protocolVersionBytes = Arrays.copyOfRange(data, 4, 5);
        return ByteUtil.byteArrayToInt(protocolVersionBytes);
    }

    @Override
    public RskAddress getRskDestinationAddress() {
        return this.rskDestinationAddress;
    }

    @Override
    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    public void parse(byte[] data) throws PeginInstructionsParseException {
        validateDataLength(data);
        this.rskDestinationAddress = getRskDestinationAddressFromData(data);
        parseAdditionalData(data);
    }
}
