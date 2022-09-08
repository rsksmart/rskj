package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.PegTestUtils;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PeginInstructionsVersion1Test {
    private final NetworkParameters params = BridgeRegTestConstants.getInstance().getBtcParams();

    @Test
    public void validateDataLength_invalidLengthTooLarge() {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        Assertions.assertThrows(PeginInstructionsParseException.class, () -> peginInstructionsVersion1.validateDataLength(new byte[50]));
    }

    @Test
    public void getProtocolVersion() {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        Assertions.assertEquals(1, peginInstructionsVersion1.getProtocolVersion());
    }

    @Test
    public void getRskDestinationAddress_noData() {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        Assertions.assertNull(peginInstructionsVersion1.getRskDestinationAddress());
    }

    @Test
    public void parse_invalidDataLength() {
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        Assertions.assertThrows(PeginInstructionsParseException.class, () -> peginInstructionsVersion1.parse(new byte[30]));
    }

    @Test
    public void parseAdditionalData_noBtcRefundAddress() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(1, new RskAddress(new byte[20]), Optional.empty());

        // Act
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        peginInstructionsVersion1.parseAdditionalData(opReturnScript.getChunks().get(1).data);

        // Assert
        Optional<Address> obtainedBtcAddress = peginInstructionsVersion1.getBtcRefundAddress();
        Assertions.assertFalse(obtainedBtcAddress.isPresent());
    }

    @Test
    public void parseAdditionalData_p2pkhTypeAddress() throws PeginInstructionsException {
        // Arrange
        BtcECKey key = new BtcECKey();
        Address btcRefundAddress = key.toAddress(params);
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(
            1,
            new RskAddress(new byte[20]),
            Optional.of(btcRefundAddress)
        );

        // Act
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        peginInstructionsVersion1.parseAdditionalData(opReturnScript.getChunks().get(1).data);

        // Assert
        Optional<Address> obtainedBtcAddress = peginInstructionsVersion1.getBtcRefundAddress();
        Assertions.assertTrue(obtainedBtcAddress.isPresent());
        Assertions.assertEquals(btcRefundAddress, obtainedBtcAddress.get());
        Assertions.assertFalse(obtainedBtcAddress.get().isP2SHAddress());
    }

    @Test
    public void parseAdditionalData_p2shMultisigAddress() throws PeginInstructionsException {
        // Arrange
        Script p2shScript = ScriptBuilder.createP2SHOutputScript(
            2,
            Arrays.asList(new BtcECKey(), new BtcECKey(), new BtcECKey())
        );
        Address btcRefundAddress = Address.fromP2SHScript(params, p2shScript);
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(
            1,
            new RskAddress(new byte[20]),
            Optional.of(btcRefundAddress)
        );

        // Act
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        peginInstructionsVersion1.parseAdditionalData(opReturnScript.getChunks().get(1).data);

        // Assert
        Optional<Address> obtainedBtcAddress = peginInstructionsVersion1.getBtcRefundAddress();
        Assertions.assertTrue(obtainedBtcAddress.isPresent());
        Assertions.assertEquals(btcRefundAddress, obtainedBtcAddress.get());
        Assertions.assertTrue(obtainedBtcAddress.get().isP2SHAddress());
    }

    @Test
    public void parseAdditionalData_invalidAddressType() {
        // Arrange
        BtcECKey key = new BtcECKey();
        Address btcRefundAddress = key.toAddress(params);
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(
            1,
            new RskAddress(new byte[20]),
            Optional.of(btcRefundAddress)
        );
        byte[] opReturnData = opReturnScript.getChunks().get(1).data;
        opReturnData[25] = 9; // Change the byte containing the address type

        // Act
        PeginInstructionsVersion1 peginInstructionsVersion1 = new PeginInstructionsVersion1(params);
        Assertions.assertThrows(PeginInstructionsParseException.class, () -> peginInstructionsVersion1.parseAdditionalData(opReturnData));
    }
}
