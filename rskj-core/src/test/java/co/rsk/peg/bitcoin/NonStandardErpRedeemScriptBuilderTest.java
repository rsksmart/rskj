package co.rsk.peg.bitcoin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NonStandardErpRedeemScriptBuilderTest {

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
        Script redeemScript = NonStandardErpRedeemScriptBuilder.builder().of(
            defaultKeys, defaultThreshold, erpKeys, erpThreshold, CSV_VALUE
        );

        // assert
        validateNonStandardErpRedeemScript(redeemScript, CSV_VALUE);
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
                NonStandardErpRedeemScriptBuilder.builder().of(
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
            Arguments.of(NullPointerException.class, defaultKeys, null, erpKeys, erpThreshold, CSV_VALUE),
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
            Arguments.of(RedeemScriptCreationException.class, defaultKeys, defaultThreshold, erpKeys, erpThreshold, 0L),
            Arguments.of(NullPointerException.class, defaultKeys, defaultThreshold, erpKeys, erpThreshold, null),
            Arguments.of(RedeemScriptCreationException.class, defaultKeys, defaultThreshold, erpKeys, erpThreshold, surpassingMaxCsvValue)
        );
    }

    private void validateNonStandardErpRedeemScript(
        Script nonStandardErpRedeemScript,
        Long csvValue
    ) {
        /***
         * Expected structure:
         * OP_NOTIF
         *  OP_M
         *  PUBKEYS...N
         *  OP_N
         * OP_ELSE
         *  OP_PUSHBYTES
         *  CSV_VALUE
         *  OP_CHECKSEQUENCEVERIFY
         *  OP_DROP
         *  OP_M
         *  PUBKEYS...N
         *  OP_N
         * OP_ENDIF
         * OP_CHECKMULTISIG
         */
        int expectedCsvValueLength = BigInteger.valueOf(csvValue).toByteArray().length;
        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(csvValue);

        byte[] nonStandardErpRedeemScriptProgram = nonStandardErpRedeemScript.getProgram();
        assertTrue(nonStandardErpRedeemScriptProgram.length > 0);

        // First byte should equal OP_NOTIF
        final int OP_NOT_IF_INDEX = 0;
        assertEquals(ScriptOpCodes.OP_NOTIF, nonStandardErpRedeemScriptProgram[OP_NOT_IF_INDEX]);

        // Next byte should equal M, from an M/N multisig
        int M_STANDARD_VALUE_INDEX = OP_NOT_IF_INDEX + 1;

        int expectedMStandardFederation = defaultThreshold;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(expectedMStandardFederation)), nonStandardErpRedeemScriptProgram[M_STANDARD_VALUE_INDEX]);

        // Assert public keys
        int pubKeysIndex = M_STANDARD_VALUE_INDEX + 1;
        for (int i = 0; i < defaultKeys.size(); i++) {
            BtcECKey btcFederatorKey = defaultKeys.get(i);
            byte actualPubKeyLength = nonStandardErpRedeemScriptProgram[pubKeysIndex++];

            byte[] expectedFederatorPubKey = btcFederatorKey.getPubKey();
            assertEquals(expectedFederatorPubKey.length, actualPubKeyLength);

            for (byte characterPubKey : expectedFederatorPubKey) {
                assertEquals(characterPubKey, nonStandardErpRedeemScriptProgram[pubKeysIndex++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        final int N_STANDARD_VALUE_INDEX = pubKeysIndex;
        int nStandardFederation = defaultKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(nStandardFederation)), nonStandardErpRedeemScriptProgram[N_STANDARD_VALUE_INDEX]);

        // Next byte should equal OP_ELSE
        final int OP_ELSE_INDEX = N_STANDARD_VALUE_INDEX + 1;
        assertEquals(ScriptOpCodes.OP_ELSE, nonStandardErpRedeemScriptProgram[OP_ELSE_INDEX]);

        // Next byte should equal csv value length
        final int CSV_VALUE_LENGTH_INDEX = OP_ELSE_INDEX + 1;
        assertEquals(expectedCsvValueLength, nonStandardErpRedeemScriptProgram[CSV_VALUE_LENGTH_INDEX]);

        // Next bytes should equal the csv value in bytes
        final int CSV_VALUE_START_INDEX = CSV_VALUE_LENGTH_INDEX + 1;
        for (int i = 0; i < expectedCsvValueLength; i++) {
            int currentCsvValueIndex = CSV_VALUE_START_INDEX + i;
            assertEquals(serializedCsvValue[i], nonStandardErpRedeemScriptProgram[currentCsvValueIndex]);
        }

        final int CSV_VALUE_OP_CODE_INDEX = CSV_VALUE_START_INDEX + expectedCsvValueLength;
        assertEquals((byte) ScriptOpCodes.OP_CHECKSEQUENCEVERIFY,
            nonStandardErpRedeemScriptProgram[CSV_VALUE_OP_CODE_INDEX]);

        final int OP_DROP_INDEX = CSV_VALUE_OP_CODE_INDEX + 1;
        assertEquals(ScriptOpCodes.OP_DROP, nonStandardErpRedeemScriptProgram[OP_DROP_INDEX]);

        // Next byte should equal M, from an M/N multisig
        final int OP_M_ERP_INDEX = OP_DROP_INDEX + 1;

        int expectedMErpFederation = erpThreshold;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(expectedMErpFederation)), nonStandardErpRedeemScriptProgram[OP_M_ERP_INDEX]);

        int erpPubKeysIndex = OP_M_ERP_INDEX + 1;
        for (BtcECKey btcErpEcKey : erpKeys) {
            byte actualErpKeyLength = nonStandardErpRedeemScriptProgram[erpPubKeysIndex++];

            byte[] erpPubKey = btcErpEcKey.getPubKey();
            byte expectedLength = Integer.valueOf(erpPubKey.length).byteValue();
            assertEquals(expectedLength, actualErpKeyLength);
            for (byte characterErpPubKey : erpPubKey) {
                assertEquals(characterErpPubKey, nonStandardErpRedeemScriptProgram[erpPubKeysIndex++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        final int N_ERP_INDEX = erpPubKeysIndex;
        int actualNErpFederation = nonStandardErpRedeemScriptProgram[N_ERP_INDEX];
        int expectedNErpFederation = erpKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(expectedNErpFederation)), actualNErpFederation);

        // Next byte should equal OP_ENDIF
        final int OP_ENDIF_INDEX = N_ERP_INDEX + 1;
        byte actualOpEndIfValue = nonStandardErpRedeemScriptProgram[OP_ENDIF_INDEX];
        assertEquals(ScriptOpCodes.OP_ENDIF, actualOpEndIfValue);

        // Next byte should equal OP_CHECKMULTISIG
        final int OP_CHECK_MULTISIG_INDEX = OP_ENDIF_INDEX + 1;
        assertEquals((byte)ScriptOpCodes.OP_CHECKMULTISIG, nonStandardErpRedeemScriptProgram[OP_CHECK_MULTISIG_INDEX]);
    }
}