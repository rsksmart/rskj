package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeRegTestConstants;
import java.util.Optional;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

public class PeginInstructionsVersion1Test {
    private final NetworkParameters params = BridgeRegTestConstants.getInstance().getBtcParams();

    @Test(expected = PeginInstructionsParseException.class)
    public void peginInstructionsV1_validateDataLength_invalid_length_too_large() throws Exception {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        peginInstructionsVersion1.validateDataLength(Hex.decode("00010e537aad84447a2c2a7590d5f2665ef5cf9b667a98"));
    }

    @Test
    public void peginInstructionsV1_getProtocolVersion() {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        Assert.assertEquals(1, peginInstructionsVersion1.getProtocolVersion());
    }

    @Test
    public void peginInstructionsV1_getRskDestinationAddress_no_data() {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        Assert.assertNull(peginInstructionsVersion1.getRskDestinationAddress());
    }

    @Test(expected = PeginInstructionsParseException.class)
    public void peginInstructionsV1_parse_data_invalid_length() throws PeginInstructionsParseException {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        peginInstructionsVersion1.parse(Hex.decode("00010e537aad84447a2c2a7590d5f2665ef5cf9b"));
    }

    @Test
    public void peginInstructionsV1_parseAdditionalData_no_refund_address() throws
        PeginInstructionsException {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        peginInstructionsVersion1.parseAdditionalData(
            Hex.decode("00010e537aad84447a2c2a7590d5f2665ef5cf9b667a"));

        Optional<Address> obtainedBtcAddress = peginInstructionsVersion1.getBtcRefundAddress();
        Assert.assertEquals(Optional.empty(), obtainedBtcAddress);
    }

    @Test
    public void peginInstructionsV1_parseAdditionalData_p2pkh_address() throws
        PeginInstructionsException {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        peginInstructionsVersion1.parseAdditionalData(
            Hex.decode("00010e537aad84447a2c2a7590d5f2665ef5cf9b667a014f4c767a2d308eebb3f0f1247f9163c896e0b7d2"));

        Address btcAddress = new Address(params,
            Hex.decode("4f4c767a2d308eebb3f0f1247f9163c896e0b7d2"));

        Optional<Address> obtainedBtcAddress = peginInstructionsVersion1.getBtcRefundAddress();

        Assert.assertEquals(Optional.of(btcAddress), obtainedBtcAddress);
        Assert.assertFalse(obtainedBtcAddress.get().isP2SHAddress());
    }

    @Test
    public void peginInstructionsV1_parseAdditionalData_p2sh_p2wpkh_address() throws
        PeginInstructionsException {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        peginInstructionsVersion1.parseAdditionalData(
            Hex.decode("00010e537aad84447a2c2a7590d5f2665ef5cf9b667a024f4c767a2d308eebb3f0f1247f9163c896e0b7d2"));

        Address btcAddress = new Address(params,
            params.getP2SHHeader(),
            Hex.decode("4f4c767a2d308eebb3f0f1247f9163c896e0b7d2"));

        Optional<Address> obtainedBtcAddress = peginInstructionsVersion1.getBtcRefundAddress();

        Assert.assertEquals(Optional.of(btcAddress), obtainedBtcAddress);
        Assert.assertTrue(obtainedBtcAddress.get().isP2SHAddress());
    }

    @Test
    public void peginInstructionsV1_parseAdditionalData_p2sh_multisig_address() throws
        PeginInstructionsException {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        peginInstructionsVersion1.parseAdditionalData(
            Hex.decode("00010e537aad84447a2c2a7590d5f2665ef5cf9b667a026c230519d0957afca4a7ffbeda9ab29c3ca233d9"));

        Address btcAddress = new Address(params,
            params.getP2SHHeader(),
            Hex.decode("6c230519d0957afca4a7ffbeda9ab29c3ca233d9"));

        Optional<Address> obtainedBtcAddress = peginInstructionsVersion1.getBtcRefundAddress();

        Assert.assertEquals(Optional.of(btcAddress), obtainedBtcAddress);
        Assert.assertTrue(obtainedBtcAddress.get().isP2SHAddress());
    }

    @Test(expected = PeginInstructionsParseException.class)
    public void peginInstructionsV1_parseAdditionalData_invalid_address_type() throws
            PeginInstructionsParseException {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);

        String rawData = "00010e537aad84447a2c2a7590d5f2665ef5cf9b667a0019d7e0ee9bf6bd70d1d046b066d1c2726e1accc1";
        peginInstructionsVersion1.parseAdditionalData(Hex.decode(rawData));
    }

    @Test(expected = PeginInstructionsParseException.class)
    public void peginInstructionsV1_parseAdditionalData_invalid_refund_address_type() throws
            PeginInstructionsParseException {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);

        String rawData = "00010e537aad84447a2c2a7590d5f2665ef5cf9b667a0019d7e0ee9bf6bd70d1d046b066d1c2726e1accc1";
        peginInstructionsVersion1.parseAdditionalData(Hex.decode(rawData));
    }
}
