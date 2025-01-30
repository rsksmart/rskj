package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.peg.bitcoin.*;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static co.rsk.peg.federation.ErpFederationCreationException.Reason.NULL_OR_EMPTY_EMERGENCY_KEYS;
import static co.rsk.peg.federation.ErpFederationCreationException.Reason.REDEEM_SCRIPT_CREATION_FAILED;
import static org.junit.jupiter.api.Assertions.*;

class P2shP2wshErpFederationTest {
    private static final ErpFederation federation = P2shP2wshErpFederationBuilder.builder().build();

    @Test
    void createFederation_withNullErpKeys_throwsErpFederationCreationException() {
        P2shP2wshErpFederationBuilder federationBuilder = P2shP2wshErpFederationBuilder.builder();
        P2shP2wshErpFederationBuilder federationBuilderWithNullErpKeys = federationBuilder.withErpPublicKeys(null);

        ErpFederationCreationException exception =
            assertThrows(ErpFederationCreationException.class, federationBuilderWithNullErpKeys::build);
        assertEquals(NULL_OR_EMPTY_EMERGENCY_KEYS, exception.getReason());
    }

    @Test
    void createFederation_withEmptyErpKeys_throwsErpFederationCreationException() {
        P2shP2wshErpFederationBuilder federationBuilder = P2shP2wshErpFederationBuilder.builder();
        P2shP2wshErpFederationBuilder federationBuilderWithEmptyErpKeys = federationBuilder.withErpPublicKeys(new ArrayList<>());

        ErpFederationCreationException exception =
            assertThrows(ErpFederationCreationException.class, federationBuilderWithEmptyErpKeys::build);
        assertEquals(NULL_OR_EMPTY_EMERGENCY_KEYS, exception.getReason());
    }

    @Test
    void createFederation_withOneErpKey_valid() {
        P2shP2wshErpFederationBuilder federationBuilder = P2shP2wshErpFederationBuilder.builder();
        List<BtcECKey> oneErpKey = Collections.singletonList(federation.getErpPubKeys().get(0));
        P2shP2wshErpFederationBuilder federationBuilderWithOneErpKey = federationBuilder.withErpPublicKeys(oneErpKey);

        assertDoesNotThrow(federationBuilderWithOneErpKey::build);
    }

    @ParameterizedTest
    @ValueSource(longs = {20L, 130L, 500L, 33_000L, ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE})
    void createFederation_withValidCsvValues_valid(long csvValue) {
        P2shP2wshErpFederationBuilder federationBuilder = P2shP2wshErpFederationBuilder.builder();
        P2shP2wshErpFederationBuilder federationBuilderWithCsvValue = federationBuilder.withErpActivationDelay(csvValue);

        assertDoesNotThrow(federationBuilderWithCsvValue::build);
    }

    @ParameterizedTest
    @ValueSource(longs = {-100L, 0L, ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE + 1, 100_000L, 8_400_000L})
    void getRedeemScript_invalidCsvValues_throwsErpFederationCreationException(long csvValue) {
        P2shP2wshErpFederationBuilder federationBuilder = P2shP2wshErpFederationBuilder.builder();
        ErpFederation federationWithCsvValue = federationBuilder.withErpActivationDelay(csvValue).build();

        ErpFederationCreationException fedException =
            assertThrows(ErpFederationCreationException.class, federationWithCsvValue::getRedeemScript);
        assertEquals(REDEEM_SCRIPT_CREATION_FAILED, fedException.getReason());
    }

    @Test
    void getErpRedeemScriptBuilder() {
        assertEquals(P2shErpRedeemScriptBuilder.class, federation.getErpRedeemScriptBuilder().getClass());
    }

    @Test
    void getErpPubKeys() {
        List<BtcECKey> expectedEmergencyKeys = FederationMainNetConstants.getInstance().getErpFedPubKeysList();
        assertEquals(expectedEmergencyKeys, federation.getErpPubKeys());
    }

    @Test
    void getThreshold() {
        int federationSize = federation.getSize();
        int expectedThreshold = federationSize / 2 + 1;

        assertEquals(expectedThreshold, federation.getNumberOfSignaturesRequired());
    }

    @Test
    void getEmergencyThreshold() {
        int emergencyKeysSize = federation.getErpPubKeys().size();
        int expectedEmergencyThreshold = emergencyKeysSize / 2 + 1;

        assertEquals(expectedEmergencyThreshold, federation.getNumberOfEmergencySignaturesRequired());
    }

    @Test
    void getActivationDelay() {
        long expectedActivationDelayValue = FederationMainNetConstants.getInstance().getErpFedActivationDelay();
        assertEquals(expectedActivationDelayValue, federation.getActivationDelay());
    }

    @Test
    void getDefaultRedeemScript() {
        Script expectedDefaultRedeemScript =
            ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getBtcPublicKeys());

        assertEquals(expectedDefaultRedeemScript, federation.getDefaultRedeemScript());
    }

    @Test
    void p2shP2wshErpFederationRedeemScript_hasExpectedRedeemScriptFormat() {
        Script redeemScript = federation.getRedeemScript();
        List<BtcECKey> defaultPublicKeys = federation.getBtcPublicKeys();
        List<BtcECKey> emergencyPublicKeys = federation.getErpPubKeys();
        long activationDelayValue = federation.getActivationDelay();

        /***
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

        // Keys are sorted when added to the redeem script, so we need them sorted in order to validate
        List<BtcECKey> sortedDefaultPublicKeys = new ArrayList<>(defaultPublicKeys);
        sortedDefaultPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        List<BtcECKey> sortedEmergencyPublicKeys = new ArrayList<>(emergencyPublicKeys);
        sortedEmergencyPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(activationDelayValue);

        byte[] script = redeemScript.getProgram();
        Assertions.assertTrue(script.length > 0);

        int index = 0;

        // First byte should equal OP_NOTIF
        assertEquals(ScriptOpCodes.OP_NOTIF, script[index++]);

        // Next byte should equal M, from an M/N multisig
        int m = sortedDefaultPublicKeys.size() / 2 + 1;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        // Assert public keys
        for (BtcECKey key: sortedDefaultPublicKeys) {
            byte[] pubkey = key.getPubKey();
            assertEquals(pubkey.length, script[index++]);
            for (byte b : pubkey) {
                assertEquals(b, script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        int n = sortedDefaultPublicKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(n)), script[index++]);

        // Next byte should equal OP_CHECKMULTISIG
        assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKMULTISIG).byteValue(), script[index++]);

        // Next byte should equal OP_ELSE
        assertEquals(ScriptOpCodes.OP_ELSE, script[index++]);

        // Next byte should equal csv value length
        assertEquals(serializedCsvValue.length, script[index++]);

        // Next bytes should equal the csv value in bytes
        for (int i = 0; i < serializedCsvValue.length; i++) {
            assertEquals(serializedCsvValue[i], script[index++]);
        }

        assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY).byteValue(), script[index++]);
        assertEquals(ScriptOpCodes.OP_DROP, script[index++]);

        // Next byte should equal M, from an M/N multisig
        m = sortedEmergencyPublicKeys.size() / 2 + 1;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        for (BtcECKey key: sortedEmergencyPublicKeys) {
            byte[] pubkey = key.getPubKey();
            assertEquals(Integer.valueOf(pubkey.length).byteValue(), script[index++]);
            for (byte b : pubkey) {
                assertEquals(b, script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        n = sortedEmergencyPublicKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(n)), script[index++]);

        // Next byte should equal OP_CHECKMULTISIG
        assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKMULTISIG).byteValue(), script[index++]);

        assertEquals(ScriptOpCodes.OP_ENDIF, script[index++]);
    }

    @Test
    void getP2SHScript_equalsScriptFromP2shP2wshOutputScript() {
        Script expectedP2SHScript = ScriptBuilder.createP2SHP2WSHOutputScript(federation.getRedeemScript());
        assertEquals(expectedP2SHScript, federation.getP2SHScript());
    }

    @Test
    void getP2SHScript_doesNotEqualScriptFromP2shOutputScript() {
        Script wrongP2SHScript = ScriptBuilder.createP2SHOutputScript(federation.getRedeemScript());
        assertNotEquals(wrongP2SHScript, federation.getP2SHScript());
    }

    @Test
    void getDefaultP2SHScript_equalsScriptFromP2shP2wshOutputScript() {
        Script expectedDefaultP2SHScript = ScriptBuilder.createP2SHP2WSHOutputScript(federation.getDefaultRedeemScript());
        assertEquals(expectedDefaultP2SHScript, federation.getDefaultP2SHScript());
    }

    @Test
    void getDefaultP2SHScript_doesNotEqualScriptFromP2shOutputScript() {
        Script wrongDefaultP2SHScript = ScriptBuilder.createP2SHOutputScript(federation.getDefaultRedeemScript());
        assertNotEquals(wrongDefaultP2SHScript, federation.getDefaultP2SHScript());
    }

    @Test
    void testEquals_same() {
        FederationArgs federationArgs = new FederationArgs(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            federation.getBtcParams()
        );
        ErpFederation otherFederation = FederationFactory.buildP2shP2wshErpFederation(
            federationArgs,
            federation.getErpPubKeys(),
            federation.getActivationDelay()
        );

        assertEquals(federation, otherFederation);
    }

    @Test
    void createFederationArgs_fromFederationValues_createsExpectedFederationArgs() {
        // arrange
        List<FederationMember> federationMembers = federation.getMembers();
        Instant creationTime = federation.getCreationTime();
        long creationBlockNumber = federation.getCreationBlockNumber();
        NetworkParameters btcParams = federation.getBtcParams();

        // act
        FederationArgs federationArgsFromValues =
            new FederationArgs(federationMembers, creationTime, creationBlockNumber, btcParams);

        // assert
        assertEquals(federation.getArgs(), federationArgsFromValues);
    }
}
