package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.NetworkParameters;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;

public class PeginInstructionsVersion1 extends PeginInstructionsBase {
    private static final Logger logger = LoggerFactory.getLogger(PeginInstructionsVersion1.class);
    private static final int P2PKH_ADDRESS_TYPE = 1;
    private static final int P2SH_ADDRESS_TYPE = 2;

    private final NetworkParameters params;
    private Optional<Address> btcRefundAddress;

    public PeginInstructionsVersion1(NetworkParameters params) {
        super(1);
        this.params = params;
    }

    @Override
    protected void validateDataLength(byte[] data) throws PeginInstructionsParseException {
        if (data.length != 22 && data.length != 43) {
            logger.debug("[validateDataLength] Invalid data length");
            throw new PeginInstructionsParseException("[validateDataLength] Invalid data length");
        }
    }

    @Override
    protected void parseAdditionalData(byte[] data) throws PeginInstructionsParseException {
        this.btcRefundAddress = getBtcRefundAddressFromData(data);
    }

    public Optional<Address> getBtcRefundAddressFromData(byte[] data) throws PeginInstructionsParseException {
        if (data.length == 22) {
            return Optional.empty();
        }

        byte[] btcRefundAddressTypeBytes = Arrays.copyOfRange(data, 22, 23);
        int btcRefundAddressType = ByteUtil.byteArrayToInt(btcRefundAddressTypeBytes);
        byte[] hash = Arrays.copyOfRange(data, 23, data.length);

        Address btcRefundAddress;

        switch (btcRefundAddressType) {
            case P2PKH_ADDRESS_TYPE:
                // Uses pubKeyHash
                btcRefundAddress = new Address(this.params, hash);
                break;
            case P2SH_ADDRESS_TYPE:
                // Uses scriptPubKeyHash
                btcRefundAddress = new Address(this.params, this.params.getP2SHHeader(), hash);
                break;
            default:
                logger.debug("[getBtcRefundAddressFromData] Invalid btc address type");
                throw new PeginInstructionsParseException("Invalid btc address type");
        }

        return Optional.of(btcRefundAddress);
    }
}
