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
import org.junit.Assert;
import org.junit.Test;

public class PeginInstructionsProviderTest {
    private final NetworkParameters params = BridgeRegTestConstants.getInstance().getBtcParams();

    @Test
    public void buildPeginInstructions_nullOpReturnData() throws Exception {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(params);
        // Add OP_RETURN output with empty data
        btcTransaction.addOutput(Coin.ZERO, new Script(new byte[] { ScriptOpCodes.OP_RETURN }));

        // Act
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        Optional<PeginInstructions> peginInstructionsOptional = peginInstructionsProvider.buildPeginInstructions(btcTransaction);

        // Assert
        Assert.assertFalse(peginInstructionsOptional.isPresent());
    }

    @Test(expected = PeginInstructionsException.class)
    public void buildPeginInstructions_invalidProtocolVersion() throws Exception {
        // Arrange
        int invalidProtocolVersion = 0;
        BtcTransaction btcTransaction = new BtcTransaction(params);
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(invalidProtocolVersion, new RskAddress(new byte[20]), Optional.empty());
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        peginInstructionsProvider.buildPeginInstructions(btcTransaction);
    }

    @Test(expected = PeginInstructionsException.class)
    public void buildPeginInstructions_v1_dataLengthSmallerThanExpected() throws Exception {
        // Arrange
        int protocolVersion = 1;
        BtcTransaction btcTransaction = new BtcTransaction(params);
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRskWithCustomPayload(protocolVersion, new byte[5]);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        peginInstructionsProvider.buildPeginInstructions(btcTransaction);
    }

    @Test(expected = PeginInstructionsParseException.class)
    public void buildPeginInstructions_v1_dataLengthDifferentThanSupported() throws Exception {
        // Arrange
        int protocolVersion = 1;
        BtcTransaction btcTransaction = new BtcTransaction(params);
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRskWithCustomPayload(protocolVersion, new byte[30]);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        peginInstructionsProvider.buildPeginInstructions(btcTransaction);
    }

    @Test
    public void buildPeginInstructions_v1_noBtcRefundAddress() throws Exception {
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
        Assert.assertTrue(peginInstructions.isPresent());
        Assert.assertEquals(protocolVersion, peginInstructions.get().getProtocolVersion());
        Assert.assertEquals(rskDestinationAddress, peginInstructions.get().getRskDestinationAddress());
    }

    @Test
    public void buildPeginInstructions_v1_withBtcrefundAddress() throws Exception {
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
        Assert.assertTrue(peginInstructions.isPresent());
        Assert.assertEquals(protocolVersion, peginInstructions.get().getProtocolVersion());
        Assert.assertEquals(rskDestinationAddress, peginInstructions.get().getRskDestinationAddress());

        PeginInstructionsVersion1 peginInstructionsVersion1 = (PeginInstructionsVersion1) peginInstructions.get();
        Assert.assertTrue(peginInstructionsVersion1.getBtcRefundAddress().isPresent());
        Assert.assertEquals(btcRefundAddress, peginInstructionsVersion1.getBtcRefundAddress().get());
    }

    @Test(expected = NoOpReturnException.class)
    public void extractOpReturnData_noOpReturn() throws PeginInstructionsException {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.COIN, new BtcECKey().toAddress(params));

        // Act
        PeginInstructionsProvider.extractOpReturnData(btcTransaction);
    }

    @Test(expected = NoOpReturnException.class)
    public void extractOpReturnData_noOpReturnForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = ScriptBuilder.createOpReturnScript("some-payload".getBytes());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        PeginInstructionsProvider.extractOpReturnData(btcTransaction);
    }

    @Test(expected = NoOpReturnException.class)
    public void extractOpReturnData_opReturnWithDataLengthShorterThanExpected() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = ScriptBuilder.createOpReturnScript("1".getBytes());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        PeginInstructionsProvider.extractOpReturnData(btcTransaction);
    }

    @Test(expected = PeginInstructionsException.class)
    public void extractOpReturnData_twoOpReturnOutputsForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(1, new RskAddress(new byte[20]), Optional.empty());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        PeginInstructionsProvider.extractOpReturnData(btcTransaction);
    }

    @Test
    public void extractOpReturnData_oneOpReturnForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(1, new RskAddress(new byte[20]), Optional.empty());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        byte[] data = PeginInstructionsProvider.extractOpReturnData(btcTransaction);

        // Assert
        Assert.assertArrayEquals(opReturnScript.getChunks().get(1).data, data);
    }

    @Test
    public void extractOpReturnData_oneOpReturnForRskWithValue() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(1, new RskAddress(new byte[20]), Optional.empty());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.FIFTY_COINS, opReturnScript);

        // Act
        byte[] data = PeginInstructionsProvider.extractOpReturnData(btcTransaction);

        // Assert
        Assert.assertArrayEquals(opReturnScript.getChunks().get(1).data, data);
    }

    @Test
    public void extractOpReturnData_multipleOpReturnOutpust_oneForRsk() throws PeginInstructionsException {
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
        Assert.assertArrayEquals(opReturnForRskScript.getChunks().get(1).data, data);
    }

    @Test(expected = NoOpReturnException.class)
    public void extractOpReturnData_nullOpReturnData() throws PeginInstructionsException {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(params);
        // Add OP_RETURN output with empty data
        btcTransaction.addOutput(Coin.ZERO, new Script(new byte[] { ScriptOpCodes.OP_RETURN }));

        // Act
        PeginInstructionsProvider.extractOpReturnData(btcTransaction);
    }
}
