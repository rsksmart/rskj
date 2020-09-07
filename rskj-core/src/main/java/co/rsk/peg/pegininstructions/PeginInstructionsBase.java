package co.rsk.peg.pegininstructions;

import co.rsk.core.RskAddress;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public abstract class PeginInstructionsBase implements PeginInstructions {
    private static final Logger logger = LoggerFactory.getLogger(PeginInstructionsBase.class);

    protected RskAddress rskDestinationAddress;
    private final int protocolVersion;

    protected PeginInstructionsBase(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public RskAddress getRskDestinationAddress() {
        return this.rskDestinationAddress;
    }

    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    public static int extractProtocolVersion(byte[] data) {
        byte[] protocolVersionBytes = Arrays.copyOfRange(data, 0, 2);
        return ByteUtil.byteArrayToInt(protocolVersionBytes);
    }

    public void parse(byte[] data) throws PeginInstructionsParseException {
        if (data.length < 22) {
            logger.debug("[parse] Invalid data length");
            throw new PeginInstructionsParseException("Invalid data length");
        }

        this.rskDestinationAddress = getRskDestinationAddressFromData(data);
        validateDataLength(data);
        parseAdditionalData(data);
    }

    protected abstract void validateDataLength(byte[] data) throws PeginInstructionsParseException;

    protected abstract void parseAdditionalData(byte[] data) throws PeginInstructionsParseException;

    private RskAddress getRskDestinationAddressFromData(byte[] data) {
        byte[] rskDestinationAddress = Arrays.copyOfRange(data, 2, 22);
        return new RskAddress(rskDestinationAddress);
    }
}
