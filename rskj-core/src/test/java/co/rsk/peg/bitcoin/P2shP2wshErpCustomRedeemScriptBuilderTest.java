package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class P2shP2wshErpCustomRedeemScriptBuilderTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final long CSV_VALUE = bridgeMainnetConstants.getFederationConstants().getErpFedActivationDelay();
    private static final List<BtcECKey> oneDefaultKey = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fb01"}, true
    );
    private static final List<BtcECKey> emergencyKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fb01", "fb02", "fb03", "fb04"}, true
    );
    private static final int ERP_THRESHOLD = emergencyKeys.size() / 2 + 1;
    private static final int ONE_SIGNATURE_DEFAULT_THRESHOLD = 1;
    private static final P2shP2wshCustomErpRedeemScriptBuilder builder = P2shP2wshCustomErpRedeemScriptBuilder.builder();
    private static final List<BtcECKey> manyDefaultKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
    );
    private static final int MANY_SIGNATURE_DEFAULT_THRESHOLD = manyDefaultKeys.size() / 2 + 1;

    @Test
    void of_withZeroSignaturesThreshold_shouldThrowAnError() {
        // Arrange
        int zeroThreshold = 0;

        assertThrows(
            IllegalArgumentException.class,
            () ->
                builder.of(
                    manyDefaultKeys, zeroThreshold, null, 0, 0
                )
        );
    }

    @Test
    void of_withNegativeSignaturesThreshold_shouldThrowAnError() {
        // Arrange
        int negativeThreshold = -1;

        assertThrows(
            IllegalArgumentException.class,
            () ->
                builder.of(
                    manyDefaultKeys, negativeThreshold, null, 0, 0
                )
        );
    }

    @Test
    void of_withLessSignaturesThanThresholdSpecified_shouldThrowAnError() {
        // Arrange
        int threshold = 2;

        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () ->
                builder.of(
                    oneDefaultKey, threshold, null, 0, 0
                )
        );
    }

    @Test
    void of_withAThresholdGreaterThanTheSignaturesTheProtocolSupports_shouldThrowAnError() {
        // Arrange
        int aboveMaximumDefaultThreshold = 67;

        assertThrows(
            IllegalArgumentException.class,
            () ->
                builder.of(
                    manyDefaultKeys, aboveMaximumDefaultThreshold, null, 0, 0
                )
        );
    }

    @ParameterizedTest
    @MethodSource("providePubKeysAndThresholds")
    void of_shouldHaveTheCorrectRedeemScript(List<BtcECKey> defaultKeys, int threshold) {
        // Act
        Script redeemScript = builder.of(
            defaultKeys, threshold, emergencyKeys, ERP_THRESHOLD, CSV_VALUE
        );

        // Assert
        byte[] p2shp2wshErpCustomRedeemScriptProgram = redeemScript.getProgram();

        // redeemScript - First opcode should be the OP_NOTIF
        int opNotIfIndex = 0;
        byte actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opNotIfIndex];

        assertEquals((byte) ScriptOpCodes.OP_NOTIF, actualOpCode);

        // defaultCustomRedeemScript - Second byte should be the PubKey
        int pubKeyIndex = opNotIfIndex + 1; //Second byte should have the pubKey size
        byte actualPubKeyLength = p2shp2wshErpCustomRedeemScriptProgram[pubKeyIndex++];
        List<BtcECKey> reversedDefaultKeys = Lists.reverse(defaultKeys);
        byte[] expectedFederatorPubKey = reversedDefaultKeys.get(0).getPubKey();

        assertEquals(expectedFederatorPubKey.length, actualPubKeyLength);

        for (byte expectedCharacterPubKey : expectedFederatorPubKey) {
            assertEquals(expectedCharacterPubKey, p2shp2wshErpCustomRedeemScriptProgram[pubKeyIndex++]);
        }

        // defaultCustomRedeemScript - Third opcode should be the OP_CHECKSIG for the PubKey1
        int opCheckSigIndex = pubKeyIndex;
        actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opCheckSigIndex];

        assertEquals((byte) ScriptOpCodes.OP_CHECKSIG, actualOpCode);

        reversedDefaultKeys = reversedDefaultKeys.subList(1, reversedDefaultKeys.size());
        for (BtcECKey pubKey : reversedDefaultKeys) {
            // defaultCustomRedeemScript - Forth opcode should be the OP_SWAP for the PubKey1
            int opSwapIndex = opCheckSigIndex + 1;
            actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opSwapIndex];

            assertEquals((byte) ScriptOpCodes.OP_SWAP, actualOpCode);

            pubKeyIndex = opSwapIndex + 1;
            actualPubKeyLength = p2shp2wshErpCustomRedeemScriptProgram[pubKeyIndex++];
            expectedFederatorPubKey = pubKey.getPubKey();

            assertEquals(expectedFederatorPubKey.length, actualPubKeyLength);

            for (byte expectedCharacterPubKey : expectedFederatorPubKey) {
                assertEquals(expectedCharacterPubKey, p2shp2wshErpCustomRedeemScriptProgram[pubKeyIndex++]);
            }

            // defaultCustomRedeemScript - Third opcode should be the OP_CHECKSIG for the PubKey1
            opCheckSigIndex = pubKeyIndex;
            actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opCheckSigIndex];

            assertEquals((byte) ScriptOpCodes.OP_CHECKSIG, actualOpCode);

            // defaultCustomRedeemScript - After the CHECKSIG & SWAP opcodes should be the OP_ADD to check total of signatures provided
            int opAddIndex = opCheckSigIndex + 1;
            actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opAddIndex];

            assertEquals((byte) ScriptOpCodes.OP_ADD, actualOpCode);
            opCheckSigIndex = opAddIndex;
        }

        // defaultRedeemScript - The second last is the number of signatures expected
        int thresholdIndex = opCheckSigIndex + 1;
        byte actualThreshold = p2shp2wshErpCustomRedeemScriptProgram[thresholdIndex];

        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(threshold)), actualThreshold);

        // defaultCustomRedeemScript - The second last is the number of signatures expected
        int opNumEqualIndex = thresholdIndex + 1;
        actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opNumEqualIndex];

        assertEquals((byte) ScriptOpCodes.OP_NUMEQUAL, actualOpCode);

        // redeemScript - Next byte should equal OP_ELSE
        int opElseIndex = opNumEqualIndex + 1;
        assertEquals((byte) ScriptOpCodes.OP_ELSE, p2shp2wshErpCustomRedeemScriptProgram[opElseIndex]);

        // redeemScript - Next bytes should equal the csv value in bytes
        int opCheckSequenceVerifyIndex = ErpRedeemScriptTestUtils.assertCsvValue(opElseIndex + 1, CSV_VALUE, p2shp2wshErpCustomRedeemScriptProgram);

        // redeemScript - Next bytes should equal OP_DROP
        int opDropIndex = opCheckSequenceVerifyIndex + 1;
        assertEquals((byte) ScriptOpCodes.OP_DROP, p2shp2wshErpCustomRedeemScriptProgram[opDropIndex]);

        // ErpRedeemScript - Next bytes should equal the emergency redeem script
        ErpRedeemScriptTestUtils.assertEmergencyRedeemScript(p2shp2wshErpCustomRedeemScriptProgram, emergencyKeys, opDropIndex, ERP_THRESHOLD);
    }

    private static Stream<Arguments> providePubKeysAndThresholds() {
        return Stream.of(
            Arguments.of(
                oneDefaultKey,
                ONE_SIGNATURE_DEFAULT_THRESHOLD
            ),
            Arguments.of(
                manyDefaultKeys,
                MANY_SIGNATURE_DEFAULT_THRESHOLD
            )
        );
    }
}
