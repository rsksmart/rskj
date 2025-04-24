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

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class P2shP2wshErpCustomRedeemScriptBuilderTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final long CSV_VALUE = bridgeMainnetConstants.getFederationConstants().getErpFedActivationDelay();
    private static final List<BtcECKey> emergencyKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fb01", "fb02", "fb03", "fb04"}, true
    );
    private static final int ERP_THRESHOLD = emergencyKeys.size() / 2 + 1;
    private static final P2shP2wshCustomErpRedeemScriptBuilder builder = P2shP2wshCustomErpRedeemScriptBuilder.builder();
    private static final List<BtcECKey> defaultKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fa00","fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09",
            "fa10","fa11", "fa12", "fa13", "fa14", "fa15", "fa16", "fa17", "fa18", "fa19",
        }, true
    );
    private static final int DEFAULT_THRESHOLD = defaultKeys.size() / 2 + 1;

    @Test
    void of_withTwoScripts_withTheSameDefaultPubKeys_withDifferentOrder_shouldSortTheKeysAndTurnThemEquals() {
        // Arrange
        Script scriptA = builder.of(
            defaultKeys, DEFAULT_THRESHOLD, emergencyKeys, ERP_THRESHOLD, CSV_VALUE
        );

        List<BtcECKey> reversedKeys = Lists.reverse(defaultKeys);
        Script scriptB = builder.of(
            reversedKeys, DEFAULT_THRESHOLD, emergencyKeys, ERP_THRESHOLD, CSV_VALUE
        );

        assertArrayEquals(scriptA.getProgram(), scriptB.getProgram());
    }

    @ParameterizedTest()
    @MethodSource("invalidInputsArgsProvider")
    void of_invalidInputs_throwsException(
        Class<Exception> expectedException,
        List<BtcECKey> defaultKeys,
        Integer defaultThreshold,
        List<BtcECKey> erpKeys,
        Integer erpThreshold,
        Long csvValue
    ) {
        assertThrows(
            expectedException,
            () ->
                builder.of(
                    defaultKeys, defaultThreshold, erpKeys, erpThreshold, csvValue
                )
        );
    }

    private static Stream<Arguments> invalidInputsArgsProvider() {
        return Stream.of(
            // defaultKeys is null
            Arguments.of(NullPointerException.class, null, 0, emergencyKeys, ERP_THRESHOLD, CSV_VALUE),
            // defaultThreshold is zero
            Arguments.of(IllegalArgumentException.class, defaultKeys, 0, emergencyKeys, ERP_THRESHOLD, CSV_VALUE),
            // defaultThreshold is negative
            Arguments.of(IllegalArgumentException.class, defaultKeys, -1, emergencyKeys, ERP_THRESHOLD, CSV_VALUE),
            // empty default keys
            Arguments.of(IllegalArgumentException.class, Collections.emptyList(), 0, emergencyKeys, ERP_THRESHOLD, CSV_VALUE),
            // threshold greater than default keys size
            Arguments.of(IllegalArgumentException.class, defaultKeys, defaultKeys.size() + 1, emergencyKeys, ERP_THRESHOLD, CSV_VALUE),
            // erpKeys is null
            Arguments.of(NullPointerException.class, defaultKeys, DEFAULT_THRESHOLD, null, ERP_THRESHOLD, CSV_VALUE),
            // empty erp keys
            Arguments.of(IllegalArgumentException.class, defaultKeys, DEFAULT_THRESHOLD, Collections.emptyList(), ERP_THRESHOLD, CSV_VALUE),
            // erp threshold is negative
            Arguments.of(IllegalArgumentException.class, defaultKeys, DEFAULT_THRESHOLD, emergencyKeys, -1, CSV_VALUE),
            // erp threshold greater than erp keys size
            Arguments.of(IllegalArgumentException.class, defaultKeys, DEFAULT_THRESHOLD, emergencyKeys, emergencyKeys.size() + 1, CSV_VALUE),
            // csv is negative
            Arguments.of(RedeemScriptCreationException.class, defaultKeys, DEFAULT_THRESHOLD, emergencyKeys, ERP_THRESHOLD, -1L),
            // csv is zero
            Arguments.of(RedeemScriptCreationException.class, defaultKeys, DEFAULT_THRESHOLD, emergencyKeys, ERP_THRESHOLD, 0L),
            // csv is above the maximum allowed
            Arguments.of(RedeemScriptCreationException.class, defaultKeys, DEFAULT_THRESHOLD, emergencyKeys, ERP_THRESHOLD, ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE + 1)
        );
    }

    @Test
    void of_shouldHaveTheCorrectRedeemScript() {
        /*
         * Expected structure:
         * OP_NOTIF
         *  <pubkey1>
         *  OP_CHECKSIG
         *  OP_SWAP
         *  ...
         *  <pubkeyn>
         *  OP_CHECKSIG
         *  OP_SWAP
         *  OP_ADD
         *  <M>
         *  OP_NUMEQUAL
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

        // Arrange
        /*
        * Retrieving the federation public keys returns them in lexicographical order. The same happens with the
        * corresponding signatures. However, the signatures provided are in reverse order than the public keys
        * in the redeem script. Therefore, we are pushing the keys in reverse order to keep the signatures unmodified.
        * This is because when evaluating the script, the push operations are added at the bottom of the stack and
        * compared against the last element in it (i.e., the one that is right above the operation). So the first
        * pushed operation will be compared against the bottom element of the stack.
        */
        List<BtcECKey> reversedDefaultKeys = Lists.reverse(defaultKeys);

        // Act
        Script redeemScript = builder.of(
            defaultKeys, DEFAULT_THRESHOLD, emergencyKeys, ERP_THRESHOLD, CSV_VALUE
        );

        // Assert
        byte[] p2shp2wshErpCustomRedeemScriptProgram = redeemScript.getProgram();

        // redeemScript - First opcode should be the OP_NOTIF
        int opNotIfIndex = 0;
        byte actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opNotIfIndex];

        assertEquals((byte) ScriptOpCodes.OP_NOTIF, actualOpCode);

        // defaultCustomRedeemScript - First bytes should be the PubKey and OP_CHECKSIG
        int startingPubKeyIndex = opNotIfIndex + 1;
        byte[] expectedFederatorPubKey = reversedDefaultKeys.get(0).getPubKey();
        int opCheckSigIndex = assertPubKeyAndCheckSig(p2shp2wshErpCustomRedeemScriptProgram, expectedFederatorPubKey, startingPubKeyIndex);
        reversedDefaultKeys = reversedDefaultKeys.subList(1, reversedDefaultKeys.size());

        for (BtcECKey pubKey : reversedDefaultKeys) {
            // defaultCustomRedeemScript - After the OP_CHECKSIG opcode should be the OP_SWAP for the PubKey
            int opSwapIndex = opCheckSigIndex + 1;
            actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opSwapIndex];

            assertEquals((byte) ScriptOpCodes.OP_SWAP, actualOpCode);
            // defaultCustomRedeemScript - After the OP_SWAP there should be the pubKey and the OP_CHECKSIG
            int pubKeyLengthIndex = opSwapIndex + 1;
            byte[] expectedPubKey = pubKey.getPubKey();
            opCheckSigIndex = assertPubKeyAndCheckSig(p2shp2wshErpCustomRedeemScriptProgram, expectedPubKey, pubKeyLengthIndex);

            // defaultCustomRedeemScript - After the OP_CHECKSIG opcode there should be the OP_ADD
            // to check total of signatures provided
            int opAddIndex = opCheckSigIndex + 1;
            actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opAddIndex];

            assertEquals((byte) ScriptOpCodes.OP_ADD, actualOpCode);
            opCheckSigIndex = opAddIndex;
        }

        // defaultRedeemScript - After the signatures there should be the number of signatures expected
        int thresholdIndex = opCheckSigIndex + 1;
        byte actualThreshold = p2shp2wshErpCustomRedeemScriptProgram[thresholdIndex];

        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(DEFAULT_THRESHOLD)), actualThreshold);

        // defaultCustomRedeemScript - Finally, there should be the OP_NUMEQUAL
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
        ErpRedeemScriptTestUtils.assertMultiSigRedeemScript(p2shp2wshErpCustomRedeemScriptProgram, emergencyKeys, opDropIndex + 1);
    }

    private static int assertPubKeyAndCheckSig(byte[] p2shp2wshErpCustomRedeemScriptProgram, byte[] expectedPubKey, int startingIndex) {
        int opCheckSigIndex = ErpRedeemScriptTestUtils.assertPublicKeyAndReturnTheNextIndex(
            p2shp2wshErpCustomRedeemScriptProgram, expectedPubKey, startingIndex
        );

        // defaultCustomRedeemScript - After the pubKey there should be an OP_CHECKSIG for the pubKey
        byte  actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[opCheckSigIndex];

        assertEquals((byte) ScriptOpCodes.OP_CHECKSIG, actualOpCode);
        return opCheckSigIndex;
    }
}
