package co.rsk.peg.utils;

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
import co.rsk.peg.pegininstructions.NoOpReturnException;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

public class OpReturnUtilsTest {
    private final NetworkParameters params = BridgeRegTestConstants.getInstance().getBtcParams();

    @Test(expected = NoOpReturnException.class)
    public void extractPegInOpReturnData_noOpReturn() throws PeginInstructionsException {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.COIN, new BtcECKey().toAddress(params));

        // Act
        OpReturnUtils.extractPegInOpReturnData(btcTransaction);
    }

    @Test(expected = NoOpReturnException.class)
    public void extractPegOutOpReturnData_noOpReturn() throws PeginInstructionsException {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.COIN, new BtcECKey().toAddress(params));

        // Act
        OpReturnUtils.extractPegOutOpReturnData(btcTransaction);
    }

    @Test(expected = NoOpReturnException.class)
    public void extractPegInOpReturnData_noOpReturnForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = ScriptBuilder.createOpReturnScript("some-payload".getBytes());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        OpReturnUtils.extractPegInOpReturnData(btcTransaction);
    }

    @Test(expected = NoOpReturnException.class)
    public void extractPegOutOpReturnData_noOpReturnForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = ScriptBuilder.createOpReturnScript("some-payload".getBytes());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        OpReturnUtils.extractPegOutOpReturnData(btcTransaction);
    }

    @Test(expected = NoOpReturnException.class)
    public void extractPegInOpReturnData_opReturnWithDataLengthShorterThanExpected() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = ScriptBuilder.createOpReturnScript("1".getBytes());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        OpReturnUtils.extractPegInOpReturnData(btcTransaction);
    }

    @Test(expected = NoOpReturnException.class)
    public void extractPegOutOpReturnData_opReturnWithDataLengthShorterThanExpected() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = ScriptBuilder.createOpReturnScript("1".getBytes());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        OpReturnUtils.extractPegOutOpReturnData(btcTransaction);
    }

    @Test(expected = PeginInstructionsException.class)
    public void extractPegInOpReturnData_twoOpReturnOutputsForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = PegTestUtils.createPegInOpReturnScriptForRsk(
            1,
            new RskAddress(new byte[20]),
            Optional.empty()
        );

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        OpReturnUtils.extractPegInOpReturnData(btcTransaction);
    }

    @Test(expected = PeginInstructionsException.class)
    public void extractPegOutOpReturnData_twoOpReturnOutputsForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = ScriptBuilder.createOpReturnScript(OpReturnUtils.PEGOUT_OUTPUT_IDENTIFIER);

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        OpReturnUtils.extractPegOutOpReturnData(btcTransaction);
    }

    @Test
    public void extractPegInOpReturnData_oneOpReturnForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = PegTestUtils.createPegInOpReturnScriptForRsk(
            1,
            new RskAddress(new byte[20]),
            Optional.empty()
        );

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        byte[] data = OpReturnUtils.extractPegInOpReturnData(btcTransaction);

        // Assert
        Assert.assertArrayEquals(opReturnScript.getChunks().get(1).data, data);
    }

    @Test
    public void extractPegOutOpReturnData_oneOpReturnForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = ScriptBuilder.createOpReturnScript(OpReturnUtils.PEGOUT_OUTPUT_IDENTIFIER);

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        byte[] data = OpReturnUtils.extractPegOutOpReturnData(btcTransaction);

        // Assert
        Assert.assertArrayEquals(opReturnScript.getChunks().get(1).data, data);
    }

    @Test(expected = NoOpReturnException.class)
    public void extractPegInOpReturnData_oneOpReturnForRskWithPegOutIdentifier() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = ScriptBuilder.createOpReturnScript(OpReturnUtils.PEGOUT_OUTPUT_IDENTIFIER);

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        OpReturnUtils.extractPegInOpReturnData(btcTransaction);
    }

    @Test(expected = NoOpReturnException.class)
    public void extractPegOutOpReturnData_oneOpReturnForRskWithPegInIdentifier() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = PegTestUtils.createPegInOpReturnScriptForRsk(
            1,
            new RskAddress(new byte[20]),
            Optional.empty()
        );

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript);

        // Act
        OpReturnUtils.extractPegOutOpReturnData(btcTransaction);
    }

    @Test
    public void extractPegInOpReturnData_oneOpReturnForRskWithValue() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = PegTestUtils.createPegInOpReturnScriptForRsk(
            1,
            new RskAddress(new byte[20]),
            Optional.empty()
        );

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.FIFTY_COINS, opReturnScript);

        // Act
        byte[] data = OpReturnUtils.extractPegInOpReturnData(btcTransaction);

        // Assert
        Assert.assertArrayEquals(opReturnScript.getChunks().get(1).data, data);
    }

    @Test
    public void extractPegOutOpReturnData_oneOpReturnForRskWithValue() throws PeginInstructionsException {
        // Arrange
        Script opReturnScript = ScriptBuilder.createOpReturnScript(OpReturnUtils.PEGOUT_OUTPUT_IDENTIFIER);

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.FIFTY_COINS, opReturnScript);

        // Act
        byte[] data = OpReturnUtils.extractPegOutOpReturnData(btcTransaction);

        // Assert
        Assert.assertArrayEquals(opReturnScript.getChunks().get(1).data, data);
    }

    @Test
    public void extractPegInOpReturnData_multipleOpReturnOutputs_oneForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnForRskScript = PegTestUtils.createPegInOpReturnScriptForRsk(1, new RskAddress(new byte[20]), Optional.empty());
        Script opReturnScript1 = ScriptBuilder.createOpReturnScript("1".getBytes());
        Script opReturnScript2 = ScriptBuilder.createOpReturnScript("some-payload".getBytes());
        Script opReturnScript3 = ScriptBuilder.createOpReturnScript("another-output".getBytes());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript1);
        btcTransaction.addOutput(Coin.ZERO, opReturnForRskScript);
        btcTransaction.addOutput(Coin.COIN, opReturnScript2);
        btcTransaction.addOutput(Coin.FIFTY_COINS, opReturnScript3);

        // Act
        byte[] data = OpReturnUtils.extractPegInOpReturnData(btcTransaction);

        // Assert
        Assert.assertArrayEquals(opReturnForRskScript.getChunks().get(1).data, data);
    }

    @Test
    public void extractPegOutOpReturnData_multipleOpReturnOutputs_oneForRsk() throws PeginInstructionsException {
        // Arrange
        Script opReturnForRskScript = ScriptBuilder.createOpReturnScript(OpReturnUtils.PEGOUT_OUTPUT_IDENTIFIER);
        Script opReturnScript1 = ScriptBuilder.createOpReturnScript("1".getBytes());
        Script opReturnScript2 = ScriptBuilder.createOpReturnScript("some-payload".getBytes());
        Script opReturnScript3 = ScriptBuilder.createOpReturnScript("another-output".getBytes());

        BtcTransaction btcTransaction = new BtcTransaction(params);
        btcTransaction.addOutput(Coin.ZERO, opReturnScript1);
        btcTransaction.addOutput(Coin.ZERO, opReturnForRskScript);
        btcTransaction.addOutput(Coin.COIN, opReturnScript2);
        btcTransaction.addOutput(Coin.FIFTY_COINS, opReturnScript3);

        // Act
        byte[] data = OpReturnUtils.extractPegOutOpReturnData(btcTransaction);

        // Assert
        Assert.assertArrayEquals(opReturnForRskScript.getChunks().get(1).data, data);
    }

    @Test(expected = NoOpReturnException.class)
    public void extractPegInOpReturnData_nullOpReturnData() throws PeginInstructionsException {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(params);
        // Add OP_RETURN output with empty data
        btcTransaction.addOutput(Coin.ZERO, new Script(new byte[] { ScriptOpCodes.OP_RETURN }));

        // Act
        OpReturnUtils.extractPegInOpReturnData(btcTransaction);
    }

    @Test(expected = NoOpReturnException.class)
    public void extractPegOutOpReturnData_nullOpReturnData() throws PeginInstructionsException {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(params);
        // Add OP_RETURN output with empty data
        btcTransaction.addOutput(Coin.ZERO, new Script(new byte[] { ScriptOpCodes.OP_RETURN }));

        // Act
        OpReturnUtils.extractPegOutOpReturnData(btcTransaction);
    }
}
