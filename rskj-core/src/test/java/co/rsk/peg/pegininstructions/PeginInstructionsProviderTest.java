package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import java.util.Optional;
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

    @Test(expected = PeginInstructionsException.class)
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
    public void peginInstructionsProvider_buildPeginInstructions_and_check_protocol_version_v1()
        throws Exception {
        BtcTransaction btcTransaction = new BtcTransaction(params,
                Hex.decode(RawTransactions.VALID_DATA_WITHOUT_REFUND_ADDRESS_V1_TX));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        Optional<PeginInstructions> peginInstructions =
            peginInstructionsProvider.buildPeginInstructions(btcTransaction);

        RskAddress expectedRskDestinationAddress = new RskAddress("0x0e537aad84447a2c2a7590d5f2665ef5cf9b667a");

        Assert.assertEquals(1, peginInstructions.get().getProtocolVersion());
        Assert.assertEquals(expectedRskDestinationAddress,
            peginInstructions.get().getRskDestinationAddress());
    }

    @Test
    public void peginInstructionsProvider_buildPeginInstructions_and_check_btc_refund_address()
        throws Exception {
        BtcTransaction btcTransaction = new BtcTransaction(params,
            Hex.decode(RawTransactions.VALID_DATA_WITH_REFUND_ADDRESS_V1_P2SH_MULTISIG_TX));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        Optional<PeginInstructions> peginInstructions = peginInstructionsProvider.buildPeginInstructions(btcTransaction);

        Address btcAddress = new Address(params,
            params.getP2SHHeader(),
            Hex.decode("6c230519d0957afca4a7ffbeda9ab29c3ca233d9"));

        PeginInstructionsVersion1 peginInstructionsVersion1 =
            (PeginInstructionsVersion1) peginInstructions.get();

        Assert.assertEquals(Optional.of(btcAddress), peginInstructionsVersion1.getBtcRefundAddress());
    }

    @Test(expected = NoOpReturnException.class)
    public void getOpReturnOutput_no_op_return() throws PeginInstructionsException {
        BtcTransaction btcTransaction = new BtcTransaction(params);
        PeginInstructionsProvider.extractOpReturnData(btcTransaction);
    }

    @Test(expected = PeginInstructionsException.class)
    public void getOpReturnOutput_two_op_return() throws PeginInstructionsException {
        BtcTransaction btcTransaction = new BtcTransaction(params,
            Hex.decode(RawTransactions.INVALID_TX_2_OP_RETURNS));
        PeginInstructionsProvider.extractOpReturnData(btcTransaction);
    }

    @Test
    public void getOpReturnOutput_one_op_return() throws PeginInstructionsException {
        BtcTransaction btcTransaction = new BtcTransaction(params,
            Hex.decode(RawTransactions.VALID_DATA_WITHOUT_REFUND_ADDRESS_V1_TX));

        byte[] data = PeginInstructionsProvider.extractOpReturnData(btcTransaction);
        String expectedData = "00010e537aad84447a2c2a7590d5f2665ef5cf9b667a";

        Assert.assertEquals(expectedData, Hex.toHexString(data));
    }

    @Test(expected = PeginInstructionsException.class)
    public void getOpReturnOutput_empty_data_op_return() throws PeginInstructionsException {
        BtcTransaction btcTransaction = new BtcTransaction(params);

        // Add OP_RETURN output with empty data
        btcTransaction.addOutput(Coin.ZERO, new Script(new byte[] { ScriptOpCodes.OP_RETURN }));
        Assert.assertNull(PeginInstructionsProvider.extractOpReturnData(btcTransaction));
    }
}
