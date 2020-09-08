package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

public class PeginInstructionsProviderTest {
    private final NetworkParameters params = BridgeRegTestConstants.getInstance().getBtcParams();

    @Test(expected = PeginInstructionsException.class)
    public void peginInstructionsProvider_buildPeginInstructions_null_op_return_data() throws Exception {
        BtcTransaction btcTransaction = new BtcTransaction(params);

        // Add OP_RETURN output with empty data
        btcTransaction.addOutput(Coin.ZERO, new Script(new byte[] { ScriptOpCodes.OP_RETURN }));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        peginInstructionsProvider.buildPeginInstructions(btcTransaction);
    }

    @Test(expected = PeginInstructionsException.class)
    public void peginInstructionsProvider_buildPeginInstructions_invalid_protocol_version() throws Exception {
        BtcTransaction btcTransaction = new BtcTransaction(params,
                Hex.decode(RawTransactions.INVALID_PROTOCOL_VERSION_TX));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        peginInstructionsProvider.buildPeginInstructions(btcTransaction);
    }

    @Test(expected = PeginInstructionsParseException.class)
    public void peginInstructionsProvider_buildPeginInstructions_try_parse_data_length_smaller_than_expected_v1() throws
            Exception {
        BtcTransaction btcTransaction = new BtcTransaction(params,
                Hex.decode(RawTransactions.SMALLER_THAN_EXPECTED_DATA_TX));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        peginInstructionsProvider.buildPeginInstructions(btcTransaction);
    }

    @Test(expected = PeginInstructionsParseException.class)
    public void peginInstructionsProvider_buildPeginInstructions_try_parse_data_length_different_than_supported_v1() throws
            Exception {
        BtcTransaction btcTransaction = new BtcTransaction(params,
                Hex.decode(RawTransactions.UNSUPPORTED_DATA_LENGTH_FOR_V1_TX));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        peginInstructionsProvider.buildPeginInstructions(btcTransaction);
    }

    @Test
    public void peginInstructionsProvider_buildPeginInstructions_and_check_protocol_version_v1() throws Exception {
        BtcTransaction btcTransaction = new BtcTransaction(params,
                Hex.decode(RawTransactions.VALID_DATA_WITHOUT_REFUND_ADDRESS_V1_TX));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        PeginInstructionsVersion1 peginInstructionsVersion1 =
                (PeginInstructionsVersion1) peginInstructionsProvider.buildPeginInstructions(btcTransaction);

        Assert.assertEquals(1, peginInstructionsVersion1.getProtocolVersion());
    }

    @Test
    public void peginInstructionsProvider_buildPeginInstructions_and_return_rsk_destination_address_v1() throws Exception {
        BtcTransaction btcTransaction = new BtcTransaction(params,
                Hex.decode(RawTransactions.VALID_DATA_WITHOUT_REFUND_ADDRESS_V1_TX));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        PeginInstructionsVersion1 peginInstructionsVersion1 =
                (PeginInstructionsVersion1) peginInstructionsProvider.buildPeginInstructions(btcTransaction);

        RskAddress expectedRskDestinationAddress = new RskAddress("0x0e537aad84447a2c2a7590d5f2665ef5cf9b667a");
        Assert.assertEquals(expectedRskDestinationAddress, peginInstructionsVersion1.getRskDestinationAddress());
    }
}
