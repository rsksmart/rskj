package co.rsk.peg.bitcoin;

import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
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
         *  OP_PUSHBYTES
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

        // First byte should equal OP_NOTIF
        final int OP_NOT_IF_INDEX = 0;
        byte actualOpCodeInOpNotIfPosition = p2shErpRedeemScriptProgram[OP_NOT_IF_INDEX];
        assertEquals(ScriptOpCodes.OP_NOTIF, actualOpCodeInOpNotIfPosition);

        // Next byte should equal M, from an M/N multisig
        int M_STANDARD_VALUE_INDEX = OP_NOT_IF_INDEX + 1;
        int actualOpCodeM = p2shErpRedeemScriptProgram[M_STANDARD_VALUE_INDEX];

        int expectedMStandardFederation = defaultThreshold;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(expectedMStandardFederation)), actualOpCodeM);

        // Assert public keys
        int pubKeysIndex = M_STANDARD_VALUE_INDEX + 1;
        for (int i = 0; i < defaultKeys.size(); i++) {
            BtcECKey btcFederatorKey = defaultKeys.get(i);
            byte actualPubKeyLength = p2shErpRedeemScriptProgram[pubKeysIndex++];

            byte[] expectedFederatorPubKey = btcFederatorKey.getPubKey();
            assertEquals(expectedFederatorPubKey.length, actualPubKeyLength);

            for (byte characterPubKey : expectedFederatorPubKey) {
                assertEquals(characterPubKey, p2shErpRedeemScriptProgram[pubKeysIndex++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        final int N_STANDARD_VALUE_INDEX = pubKeysIndex;
        int numberOfStandardKeys = defaultKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(numberOfStandardKeys)), p2shErpRedeemScriptProgram[N_STANDARD_VALUE_INDEX]);

        // Next byte should equal OP_CHECKMULTISIG
        final int OP_CHECK_MULTISIG_IN_IF_INDEX = N_STANDARD_VALUE_INDEX + 1;

        assertEquals((byte)ScriptOpCodes.OP_CHECKMULTISIG, p2shErpRedeemScriptProgram[OP_CHECK_MULTISIG_IN_IF_INDEX]);

        // Next byte should equal OP_ELSE
        final int OP_ELSE_INDEX = OP_CHECK_MULTISIG_IN_IF_INDEX + 1;
        assertEquals((byte)ScriptOpCodes.OP_ELSE, p2shErpRedeemScriptProgram[OP_ELSE_INDEX]);

        // Next byte should equal csv value length and then the csv value
        final int CSV_VALUE_LENGTH_INDEX = OP_ELSE_INDEX + 1;
        int CSV_OP_CODE_INDEX = ErpRedeemScriptTestUtils.assertCsvValue(CSV_VALUE_LENGTH_INDEX, csvValue, p2shErpRedeemScriptProgram);

        // Next byte should equal OP_DROP
        final int OP_DROP_INDEX = CSV_OP_CODE_INDEX + 1;
        assertEquals((byte)ScriptOpCodes.OP_DROP, p2shErpRedeemScriptProgram[OP_DROP_INDEX]);

        ErpRedeemScriptTestUtils.assertEmergencyRedeemScript(p2shErpRedeemScriptProgram, erpKeys, OP_DROP_INDEX, erpThreshold);
    }
}
