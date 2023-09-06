package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.LegacyAddress;
import co.rsk.bitcoinj.core.NetworkParameters;
import java.util.Arrays;
import java.util.Optional;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeginInstructionsVersion1 extends PeginInstructionsBase {
    private static final Logger logger = LoggerFactory.getLogger(PeginInstructionsVersion1.class);
    private static final int P2PKH_ADDRESS_TYPE = 1;
    private static final int P2SH_ADDRESS_TYPE = 2;

    private final NetworkParameters params;
    private Optional<LegacyAddress> btcRefundAddress;

    public PeginInstructionsVersion1(NetworkParameters params) {
        super(1);
        this.params = params;
    }

    @Override
    protected void validateDataLength(byte[] data) throws PeginInstructionsParseException {
        if (data.length != 25 && data.length != 46) {
            String message = String.format("[validateDataLength] Invalid data length. Expected 22 or 43 bytes, received %d", data.length);
            logger.debug(message);
            throw new PeginInstructionsParseException(message);
        }
    }

    @Override
    protected void parseAdditionalData(byte[] data) throws PeginInstructionsParseException {
        if (data.length == 25) {
            this.btcRefundAddress = Optional.empty();
            return;
        }

        byte[] btcRefundAddressTypeBytes = Arrays.copyOfRange(data, 25, 26);
        int btcRefundAddressType = ByteUtil.byteArrayToInt(btcRefundAddressTypeBytes);
        byte[] hash = Arrays.copyOfRange(data, 26, data.length);

        LegacyAddress parsedBtcRefundAddress;

        switch (btcRefundAddressType) {
            case P2PKH_ADDRESS_TYPE:
                // Uses pubKeyHash
                parsedBtcRefundAddress = new LegacyAddress(this.params, hash);
                logger.debug("[parseAdditionalData] Obtained P2PKH BTC address: {}",parsedBtcRefundAddress);
                break;
            case P2SH_ADDRESS_TYPE:
                // Uses scriptPubKeyHash
                parsedBtcRefundAddress = new LegacyAddress(this.params, true, hash);
                logger.debug("[parseAdditionalData] Obtained P2SH BTC address: {}",parsedBtcRefundAddress);
                break;
            default:
                String message = String.format("[parseAdditionalData] Invalid btc address type: %d", btcRefundAddressType);
                logger.debug(message);
                throw new PeginInstructionsParseException(message);
        }

        this.btcRefundAddress = Optional.of(parsedBtcRefundAddress);
    }

    public Optional<LegacyAddress> getBtcRefundAddress() {
        return this.btcRefundAddress;
    }
}
