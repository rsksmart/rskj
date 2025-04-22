package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class P2wshP2shErpCustomRedeemScriptBuilderTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final List<BtcECKey> defaultKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09"}, true
    );
    private static final int defaultThreshold = defaultKeys.size() / 2 + 1;

    private static final List<BtcECKey> erpKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fb01", "fb02", "fb03", "fb04"}, true
    );
    private static final int erpThreshold = erpKeys.size() / 2 + 1;

    private static long CSV_VALUE = bridgeMainnetConstants.getFederationConstants().getErpFedActivationDelay();

    @Test
    void of_withZeroSignaturesThreshold_shouldThrowAnError() {
        // Arrange
        int zeroThreshold = 0;

        try {
            // Act
            P2shP2wshCustomErpRedeemScriptBuilder.builder().of(
                defaultKeys, zeroThreshold, erpKeys, erpThreshold, CSV_VALUE
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

        try {
            // Act
            P2shP2wshCustomErpRedeemScriptBuilder.builder().of(
                defaultKeys, negativeThreshold, erpKeys, erpThreshold, CSV_VALUE
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
        List<BtcECKey> defaultKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fb01"}, true
        );

        try {
            // Act
            P2shP2wshCustomErpRedeemScriptBuilder.builder().of(
                defaultKeys, threshold, erpKeys, erpThreshold, CSV_VALUE
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
            new String[]{"fb01"}, true
        );

        try {
            // Act
            P2shP2wshCustomErpRedeemScriptBuilder.builder().of(
                defaultKeys, aboveMaximumDefaultThreshold, erpKeys, erpThreshold, CSV_VALUE
            );
        } catch (Exception e) {
            // Assert
            assertInstanceOf(IllegalArgumentException.class, e);
            return;
        }
        fail();
    }

    @Test
    void of_shouldHaveNOTIFAsFirstOpCode() {
        // Act
        Script redeemScript = P2shP2wshCustomErpRedeemScriptBuilder.builder().of(
            defaultKeys, defaultThreshold, erpKeys, erpThreshold, CSV_VALUE
        );

        byte[] p2shp2wshErpCustomRedeemScriptProgram = redeemScript.getProgram();

        int opNOTIFIndex = 0; //First byte should be OP_NOTIF
        byte actualOpCodeInOpNotIfPosition = p2shp2wshErpCustomRedeemScriptProgram[opNOTIFIndex];

        // Assert
        assertEquals(ScriptOpCodes.OP_NOTIF, actualOpCodeInOpNotIfPosition);
    }
}
