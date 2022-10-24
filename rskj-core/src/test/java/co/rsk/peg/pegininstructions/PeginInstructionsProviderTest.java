package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.PegTestUtils;
import java.util.Optional;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PeginInstructionsProviderTest {
    private final NetworkParameters params = BridgeRegTestConstants.getInstance().getBtcParams();

    @Test
    void buildPeginInstructions_nullOpReturnData() throws Exception {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(params);
        // Add OP_RETURN output with empty data
        btcTransaction.addOutput(Coin.ZERO, new Script(new byte[] { ScriptOpCodes.OP_RETURN }));

        // Act
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        Optional<PeginInstructions> peginInstructionsOptional = peginInstructionsProvider.buildPeginInstructions(btcTransaction);

        // Assert
        Assertions.assertFalse(peginInstructionsOptional.isPresent());
    }

    @Test
    void buildPeginInstructions_invalidProtocolVersion() {
        // Arrange
        int invalidProtocolVersion = 0;
        BtcTransaction btcTransaction = new BtcTransaction(params);
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(invalidProtocolVersion, new RskAddress(new byte[20]), Optional.empty());
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        Assertions.assertThrows(PeginInstructionsException.class, () -> peginInstructionsProvider.buildPeginInstructions(btcTransaction));
    }

    @Test
    void buildPeginInstructions_v1_dataLengthSmallerThanExpected() {
        // Arrange
        int protocolVersion = 1;
        BtcTransaction btcTransaction = new BtcTransaction(params);
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRskWithCustomPayload(protocolVersion, new byte[5]);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        Assertions.assertThrows(PeginInstructionsException.class, () -> peginInstructionsProvider.buildPeginInstructions(btcTransaction));
    }

    @Test
    void buildPeginInstructions_v1_dataLengthDifferentThanSupported() {
        // Arrange
        int protocolVersion = 1;
        BtcTransaction btcTransaction = new BtcTransaction(params);
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRskWithCustomPayload(protocolVersion, new byte[30]);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        Assertions.assertThrows(PeginInstructionsParseException.class, () -> peginInstructionsProvider.buildPeginInstructions(btcTransaction));
    }

    @Test
    void buildPeginInstructions_v1_noBtcRefundAddress() throws Exception {
        // Arrange
        int protocolVersion = 1;
        BtcECKey key = new BtcECKey();
        RskAddress rskDestinationAddress = new RskAddress(ECKey.fromPublicOnly(key.getPubKey()).getAddress());

        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(protocolVersion, rskDestinationAddress, Optional.empty());
        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        Optional<PeginInstructions> peginInstructions = peginInstructionsProvider.buildPeginInstructions(btcTransaction);

        // Assert
        Assertions.assertTrue(peginInstructions.isPresent());
        Assertions.assertEquals(protocolVersion, peginInstructions.get().getProtocolVersion());
        Assertions.assertEquals(rskDestinationAddress, peginInstructions.get().getRskDestinationAddress());
    }

    @Test
    void buildPeginInstructions_v1_withBtcrefundAddress() throws Exception {
        // Arrange
        int protocolVersion = 1;
        BtcECKey key = new BtcECKey();
        RskAddress rskDestinationAddress = new RskAddress(ECKey.fromPublicOnly(key.getPubKey()).getAddress());
        Address btcRefundAddress = key.toAddress(params);

        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(protocolVersion, rskDestinationAddress, Optional.of(btcRefundAddress));
        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        Optional<PeginInstructions> peginInstructions = peginInstructionsProvider.buildPeginInstructions(btcTransaction);

        // Assert
        Assertions.assertTrue(peginInstructions.isPresent());
        Assertions.assertEquals(protocolVersion, peginInstructions.get().getProtocolVersion());
        Assertions.assertEquals(rskDestinationAddress, peginInstructions.get().getRskDestinationAddress());

        PeginInstructionsVersion1 peginInstructionsVersion1 = (PeginInstructionsVersion1) peginInstructions.get();
        Assertions.assertTrue(peginInstructionsVersion1.getBtcRefundAddress().isPresent());
        Assertions.assertEquals(btcRefundAddress, peginInstructionsVersion1.getBtcRefundAddress().get());
    }

    @Test
    void extractOpReturnData_noOpReturn() {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.COIN, new BtcECKey().toAddress(params));

        // Act
        Assertions.assertThrows(NoOpReturnException.class, () -> PeginInstructionsProvider.extractOpReturnData(btcTransaction));
    }

    @Test
    void extractOpReturnData_noOpReturnForRsk() {
        // Arrange
        Script opReturnScript = ScriptBuilder.createOpReturnScript("some-payload".getBytes());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        Assertions.assertThrows(NoOpReturnException.class, () -> PeginInstructionsProvider.extractOpReturnData(btcTransaction));
    }

    @Test
    void extractOpReturnData_opReturnWithDataLengthShorterThanExpected() {
        // Arrange
        Script opReturnScript = ScriptBuilder.createOpReturnScript("1".getBytes());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        Assertions.assertThrows(NoOpReturnException.class, () -> PeginInstructionsProvider.extractOpReturnData(btcTransaction));
    }

    @Test
    void extractOpReturnData_twoOpReturnOutputsForRsk() {
        // Arrange
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(1, new RskAddress(new byte[20]), Optional.empty());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        Assertions.assertThrows(PeginInstructionsException.class, () -> PeginInstructionsProvider.extractOpReturnData(btcTransaction));
    }

    @Test
    void extractOpReturnData_oneOpReturnForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(1, new RskAddress(new byte[20]), Optional.empty());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        byte[] data = PeginInstructionsProvider.extractOpReturnData(btcTransaction);

        // Assert
        Assertions.assertArrayEquals(opReturnScript.getChunks().get(1).data, data);
    }

    @Test
    void extractOpReturnData_oneOpReturnForRskWithValue() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(1, new RskAddress(new byte[20]), Optional.empty());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.FIFTY_COINS, opReturnScript);

        // Act
        byte[] data = PeginInstructionsProvider.extractOpReturnData(btcTransaction);

        // Assert
        Assertions.assertArrayEquals(opReturnScript.getChunks().get(1).data, data);
    }

    @Test
    void extractOpReturnData_multipleOpReturnOutpust_oneForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnForRskScript = PegTestUtils.createOpReturnScriptForRsk(1, new RskAddress(new byte[20]), Optional.empty());
        Script opReturnScript1 = ScriptBuilder.createOpReturnScript("1".getBytes());
        Script opReturnScript2 = ScriptBuilder.createOpReturnScript("some-payload".getBytes());
        Script opReturnScript3 = ScriptBuilder.createOpReturnScript("another-output".getBytes());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript1);
        btcTransaction.addOutput(Coin.ZERO, opReturnForRskScript);
        btcTransaction.addOutput(Coin.COIN, opReturnScript2);
        btcTransaction.addOutput(Coin.FIFTY_COINS, opReturnScript3);

        // Act
        byte[] data = PeginInstructionsProvider.extractOpReturnData(btcTransaction);

        // Assert
        Assertions.assertArrayEquals(opReturnForRskScript.getChunks().get(1).data, data);
    }

    @Test
    void extractOpReturnData_nullOpReturnData() {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(params);
        // Add OP_RETURN output with empty data
        btcTransaction.addOutput(Coin.ZERO, new Script(new byte[] { ScriptOpCodes.OP_RETURN }));

        // Act
        Assertions.assertThrows(NoOpReturnException.class, () -> PeginInstructionsProvider.extractOpReturnData(btcTransaction));
    }
}
