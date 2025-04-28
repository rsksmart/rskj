package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static co.rsk.peg.bitcoin.ErpRedeemScriptTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class P2shP2wshErpCustomRedeemScriptBuilderTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final long csvValue = bridgeMainnetConstants.getFederationConstants().getErpFedActivationDelay();
    private static final List<BtcECKey> emergencyKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fb01", "fb02", "fb03", "fb04"}, true
    );
    private static final int erpThreshold = emergencyKeys.size() / 2 + 1;
    private static final P2shP2wshCustomErpRedeemScriptBuilder builder = P2shP2wshCustomErpRedeemScriptBuilder.builder();
    private static final List<BtcECKey> defaultKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{"fa00","fa01", "fa02", "fa03", "fa04", "fa05", "fa06", "fa07", "fa08", "fa09",
            "fa10","fa11", "fa12", "fa13", "fa14", "fa15", "fa16", "fa17", "fa18", "fa19",
        }, true
    );
    private static final int defaultThreshold = defaultKeys.size() / 2 + 1;

    @Test
    void of_withTwoScripts_withTheSameDefaultPubKeys_withDifferentOrder_shouldSortTheKeysAndTurnThemEquals() {
        // Arrange
        Script scriptA = builder.of(
            defaultKeys, defaultThreshold, emergencyKeys, erpThreshold, csvValue
        );

        List<BtcECKey> reversedKeys = Lists.reverse(defaultKeys);
        Script scriptB = builder.of(
            reversedKeys, defaultThreshold, emergencyKeys, erpThreshold, csvValue
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
            Arguments.of(NullPointerException.class, null, 0, emergencyKeys, erpThreshold, csvValue),
            // defaultThreshold is zero
            Arguments.of(IllegalArgumentException.class, defaultKeys, 0, emergencyKeys, erpThreshold, csvValue),
            // defaultThreshold is negative
            Arguments.of(IllegalArgumentException.class, defaultKeys, -1, emergencyKeys, erpThreshold, csvValue),
            // empty default keys
            Arguments.of(IllegalArgumentException.class, Collections.emptyList(), 0, emergencyKeys, erpThreshold, csvValue),
            // threshold greater than default keys size
            Arguments.of(IllegalArgumentException.class, defaultKeys, defaultKeys.size() + 1, emergencyKeys, erpThreshold, csvValue),
            // erpKeys is null
            Arguments.of(NullPointerException.class, defaultKeys, defaultThreshold, null, erpThreshold, csvValue),
            // empty erp keys
            Arguments.of(IllegalArgumentException.class, defaultKeys, defaultThreshold, Collections.emptyList(), erpThreshold, csvValue),
            // erp threshold is negative
            Arguments.of(IllegalArgumentException.class, defaultKeys, defaultThreshold, emergencyKeys, -1, csvValue),
            // erp threshold greater than erp keys size
            Arguments.of(IllegalArgumentException.class, defaultKeys, defaultThreshold, emergencyKeys, emergencyKeys.size() + 1, csvValue),
            // csv is negative
            Arguments.of(RedeemScriptCreationException.class, defaultKeys, defaultThreshold, emergencyKeys, erpThreshold, -1L),
            // csv is zero
            Arguments.of(RedeemScriptCreationException.class, defaultKeys, defaultThreshold, emergencyKeys, erpThreshold, 0L),
            // csv is above the maximum allowed
            Arguments.of(RedeemScriptCreationException.class, defaultKeys, defaultThreshold, emergencyKeys, erpThreshold, ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE + 1)
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
         */

        // Act
        Script redeemScript = builder.of(
            defaultKeys, defaultThreshold, emergencyKeys, erpThreshold, csvValue
        );

        // Assert
        byte[] p2shp2wshErpCustomRedeemScriptProgram = redeemScript.getProgram();

        // [1..csvValueStart)
        int csvValueStart = calculateCustomRedeemScriptLength(defaultKeys);
        byte[] erpCustomRedeemScriptProgram = Arrays.copyOfRange(p2shp2wshErpCustomRedeemScriptProgram, 0, csvValueStart); // [1..csvValue)
        assertCustomERPRedeemScript(erpCustomRedeemScriptProgram, defaultKeys);

        // [csvValueStart..nMultiSigStart)
        int nMultiSigStart = csvValueStart + calculateCSVValueLength(csvValue);
        byte[] csvValueProgram = Arrays.copyOfRange(p2shp2wshErpCustomRedeemScriptProgram, csvValueStart, nMultiSigStart);
        assertCsvValueSection(csvValueProgram, csvValue);

        // [multiSigStart .. end]
        int nMultiSigEnd = p2shp2wshErpCustomRedeemScriptProgram.length - 1;
        byte[] nMultiSigProgram = Arrays.copyOfRange(p2shp2wshErpCustomRedeemScriptProgram, nMultiSigStart, nMultiSigEnd);
        assertNMultiSig(nMultiSigProgram, emergencyKeys);
    }
}
