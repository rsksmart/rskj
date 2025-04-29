package co.rsk.peg.bitcoin;

import static co.rsk.peg.bitcoin.ErpRedeemScriptTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class P2shErpRedeemScriptBuilderTest {

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
    void of_whenValidValues_returnRedeemScript() {
        // act
        Script redeemScript = P2shErpRedeemScriptBuilder.builder().of(
            defaultKeys, defaultThreshold, erpKeys, erpThreshold, CSV_VALUE
        );

        // assert
        validateP2shErpRedeemScript(redeemScript, CSV_VALUE);
    }

    @ParameterizedTest()
    @MethodSource("invalidInputsArgsProvider")
    void of_invalidInputs_throwsException(
        Class<Exception> expectedException,
        List<BtcECKey> defaultKeys,
        Integer defaultThreshold,
        List<BtcECKey> erpKeys,
        Integer erpThreshold,
        Long CSV_VALUE
    ) {
        assertThrows(
            expectedException,
            () ->
                P2shErpRedeemScriptBuilder.builder().of(
                    defaultKeys, defaultThreshold, erpKeys, erpThreshold, CSV_VALUE
                )
        );
    }

    private static Stream<Arguments> invalidInputsArgsProvider() {
        long surpassingMaxCsvValue = ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE + 1;
        return Stream.of(
            Arguments.of(NullPointerException.class, null, 0, erpKeys, erpThreshold, CSV_VALUE),
            // empty default keys
            Arguments.of(IllegalArgumentException.class, Collections.emptyList(), 0, erpKeys, erpThreshold, CSV_VALUE),
            Arguments.of(IllegalArgumentException.class, defaultKeys, -1, erpKeys, erpThreshold, CSV_VALUE),
            // threshold greater than default keys size
            Arguments.of(IllegalArgumentException.class, defaultKeys, defaultKeys.size()+1, erpKeys, erpThreshold, CSV_VALUE),
            Arguments.of(NullPointerException.class, defaultKeys, defaultThreshold, null, erpThreshold, CSV_VALUE),
            // empty erp keys
            Arguments.of(IllegalArgumentException.class, defaultKeys, defaultThreshold, Collections.emptyList(), erpThreshold, CSV_VALUE),
            Arguments.of(IllegalArgumentException.class, defaultKeys, defaultThreshold, erpKeys, -1, CSV_VALUE),
            // erp threshold greater than erp keys size
            Arguments.of(IllegalArgumentException.class, defaultKeys, defaultThreshold, erpKeys, erpKeys.size() + 1, CSV_VALUE),
            Arguments.of(RedeemScriptCreationException.class, defaultKeys, defaultThreshold, erpKeys, erpThreshold, -1L),
            Arguments.of(RedeemScriptCreationException.class, defaultKeys, defaultThreshold, erpKeys, erpThreshold, 0L),
            Arguments.of(RedeemScriptCreationException.class, defaultKeys, defaultThreshold, erpKeys, erpThreshold, surpassingMaxCsvValue)
        );
    }

    private void validateP2shErpRedeemScript(
        Script p2shErpRedeemScript,
        Long csvValue
    ) {
        /*
         * Expected structure:
         * OP_NOTIF
         *  OP_M
         *  PUBKEYS...N
         *  OP_N
         *  OP_CHECKMULTISIG
         * OP_ELSE
         *  CSV_VALUE
         *  OP_CHECKSEQUENCEVERIFY
         *  OP_DROP
         *  OP_M
         *  PUBKEYS...N
         *  OP_N
         *  OP_CHECKMULTISIG
         * OP_ENDIF
         */

        byte[] p2shErpRedeemScriptProgram = p2shErpRedeemScript.getProgram();
        assertTrue(p2shErpRedeemScriptProgram.length > 0);

        final int OP_NOT_IF_INDEX = 0;
        byte actualOpCodeInOpNotIfPosition = p2shErpRedeemScriptProgram[OP_NOT_IF_INDEX];
        assertEquals(ScriptOpCodes.OP_NOTIF, actualOpCodeInOpNotIfPosition);

        // [1..csvValueStart)
        int csvValueStart = calculateMultiSigLength(defaultKeys) + 1;
        byte[] nMultiSigProgram = Arrays.copyOfRange(p2shErpRedeemScriptProgram, 1, csvValueStart);
        assertNMultiSig(nMultiSigProgram, defaultKeys);

        // [csvValueStart..multiSigStart)
        int nEmergencyMultiSigStart = csvValueStart + calculateCSVValueLength(csvValue);
        byte[] csvValueProgram = Arrays.copyOfRange(p2shErpRedeemScriptProgram, csvValueStart, nEmergencyMultiSigStart);
        assertCsvValueSection(csvValueProgram, csvValue);

        // [multiSigStart .. end]
        int nEmergencyMultiSigEnd = p2shErpRedeemScriptProgram.length;
        nMultiSigProgram = Arrays.copyOfRange(p2shErpRedeemScriptProgram, nEmergencyMultiSigStart, nEmergencyMultiSigEnd);
        assertNMultiSig(nMultiSigProgram, erpKeys);
    }
}
