package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class P2wshP2shErpCustomRedeemScriptBuilderTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    public static final long CSV_VALUE = bridgeMainnetConstants.getFederationConstants().getErpFedActivationDelay();
    private static final List<BtcECKey> oneDefaultKey = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fb01"}, true
    );
    private static final List<BtcECKey> oneEmergencyKey = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fb04"}, true
        );
    private static final int oneSignatureDefaultThreshold = 1;
    private static final int oneSignatureErpThreshold = 1;

    @Test
    void of_withZeroSignaturesThreshold_shouldThrowAnError() {
        // Arrange
        int zeroThreshold = 0;
        List<BtcECKey> defaultKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true);

        try {
            // Act
            P2shP2wshCustomErpRedeemScriptBuilder.builder().of(
                defaultKeys, zeroThreshold, null, 0, 0
            );
        } catch (Exception e) {
            // Assert
            assertInstanceOf(IllegalArgumentException.class, e);
            return;
        }
        fail();
    }

    @Test
    void of_withNegativeSignaturesThreshold_shouldThrowAnError() {
        // Arrange
        int negativeThreshold = -1;
        List<BtcECKey> defaultKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true);

        try {
            // Act
            P2shP2wshCustomErpRedeemScriptBuilder.builder().of(
                defaultKeys, negativeThreshold, null, 0, 0
            );
        } catch (Exception e) {
            // Assert
            assertInstanceOf(IllegalArgumentException.class, e);
            return;
        }
        fail();
    }

    @Test
    void of_withLessSignaturesThanThresholdSpecified_shouldThrowAnError() {
        // Arrange
        int threshold = 2;

        try {
            // Act
            P2shP2wshCustomErpRedeemScriptBuilder.builder().of(
                oneDefaultKey, threshold, null, 0, 0
            );
        } catch (Exception e) {
            // Assert
            assertInstanceOf(IllegalArgumentException.class, e);
            return;
        }
        fail();
    }

    @Test
    void of_withAThresholdGreaterThanTheSignaturesTheProtocolSupports_shouldThrowAnError() {
        // Arrange
        int aboveMaximumDefaultThreshold = 67;
        List<BtcECKey> defaultKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true);

        try {
            // Act
            P2shP2wshCustomErpRedeemScriptBuilder.builder().of(
                defaultKeys, aboveMaximumDefaultThreshold, null, 0, 0
            );
        } catch (Exception e) {
            // Assert
            assertInstanceOf(IllegalArgumentException.class, e);
            return;
        }
        fail();
    }

    @Test
    void of_withOnePublicKey_shouldHaveTheCorrectRedeemScript() {
        // Act
        Script redeemScript = P2shP2wshCustomErpRedeemScriptBuilder.builder().of(
            oneDefaultKey, oneSignatureDefaultThreshold, oneEmergencyKey, oneSignatureErpThreshold, CSV_VALUE
        );

        byte[] p2shp2wshErpCustomRedeemScriptProgram = redeemScript.getProgram();

        // Assert
        // redeemScript - First byte should be the OP_NOTIF
        int opNotIfIndex = 0;
        byte actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opNotIfIndex];

        assertEquals((byte) ScriptOpCodes.OP_NOTIF, actualOpCode);

        // defaultRedeemScript - Second byte should be the PubKey
        int pubKeyIndex = opNotIfIndex+1; //Second byte should have the pubkey size
        byte actualPubKeyLength = p2shp2wshErpCustomRedeemScriptProgram[pubKeyIndex++];
        byte[] expectedFederatorPubKey = oneDefaultKey.get(0).getPubKey();

        assertEquals(expectedFederatorPubKey.length, actualPubKeyLength);

        for (byte expectedCharacterPubKey : expectedFederatorPubKey) {
            assertEquals(expectedCharacterPubKey, p2shp2wshErpCustomRedeemScriptProgram[pubKeyIndex++]);
        }

        // defaultRedeemScript - Third opcode should be the OP_CHECKSIG for the PubKey1
        int opCheckSigIndex = pubKeyIndex;
        actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opCheckSigIndex];

        assertEquals((byte) ScriptOpCodes.OP_CHECKSIG, actualOpCode);

        // defaultRedeemScript - Forth opcode should be the OP_SWAP for the PubKey1
        int opSwapIndex = opCheckSigIndex+1;
        actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opSwapIndex];

        assertEquals((byte) ScriptOpCodes.OP_SWAP, actualOpCode);

        // defaultRedeemScript - After the CHECKSIG & SWAP opcodes should be the OP_ADD to check total of signatures provided
        int opAddIndex = opSwapIndex+1;
        actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opAddIndex];

        assertEquals((byte) ScriptOpCodes.OP_ADD, actualOpCode);

        // defaultRedeemScript - The second last is the number of signatures expected
        int thresholdIndex = opAddIndex+1;
        byte actualThreshold = p2shp2wshErpCustomRedeemScriptProgram[thresholdIndex];

        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(oneSignatureDefaultThreshold)), actualThreshold);

        // defaultRedeemScript - The second last is the number of signatures expected
        int opNumEqualIndex = thresholdIndex+1;
        actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opNumEqualIndex];

        assertEquals((byte) ScriptOpCodes.OP_NUMEQUAL, actualOpCode);

        // Next byte should equal OP_ELSE
        int opElseIndex = opNumEqualIndex + 1;
        assertEquals((byte) ScriptOpCodes.OP_ELSE, p2shp2wshErpCustomRedeemScriptProgram[opElseIndex]);

        // Next bytes should equal the csv value in bytes
        int opCheckSequenceVerifyIndex = ErpRedeemScriptTestUtils.assertCsvValue(opElseIndex + 1, CSV_VALUE, p2shp2wshErpCustomRedeemScriptProgram);

        // Next bytes should equal OP_DROP
        int opDropIndex = opCheckSequenceVerifyIndex + 1;
        assertEquals((byte) ScriptOpCodes.OP_DROP, p2shp2wshErpCustomRedeemScriptProgram[opDropIndex]);

        // ERP
        ErpRedeemScriptTestUtils.assertEmergencyRedeemScript(p2shp2wshErpCustomRedeemScriptProgram, oneEmergencyKey, opDropIndex, oneSignatureErpThreshold);
    }
}
