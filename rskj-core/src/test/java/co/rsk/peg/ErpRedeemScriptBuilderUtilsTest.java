package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_CHECKMULTISIG;
import static co.rsk.peg.ErpFederationCreationException.Reason.INVALID_CSV_VALUE;
import static co.rsk.peg.ErpFederationCreationException.Reason.INVALID_INTERNAL_REDEEM_SCRIPTS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ErpRedeemScriptBuilderUtilsTest {
    private List<BtcECKey> defaultKeys;
    private int defaultThreshold;
    private List<BtcECKey> emergencyKeys;
    private int emergencyThreshold;
    private long activationDelayValue;
    @BeforeEach
    void setup() {
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03b53899c390573471ba30e5054f78376c5f797fda26dde7a760789f02908cbad2"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("027319afb15481dbeb3c426bcc37f9a30e7f51ceff586936d85548d9395bcc2344"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0355a2e9bf100c00fc0a214afd1bf272647c7824eb9cb055480962f0c382596a70"));
        BtcECKey federator3PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"));
        BtcECKey federator4PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0294c817150f78607566e961b3c71df53a22022a80acbb982f83c0c8baac040adc"));
        defaultKeys = Arrays.asList(
            federator0PublicKey, federator1PublicKey, federator2PublicKey,
            federator3PublicKey, federator4PublicKey
        );
        defaultThreshold = defaultKeys.size() / 2 + 1;
        emergencyKeys = bridgeConstants.getErpFedPubKeysList();
        emergencyThreshold = emergencyKeys.size() / 2 + 1;
        activationDelayValue = bridgeConstants.getErpFedActivationDelay();
    }

    @Test
    void removeOpCheckMultiSig() {
        Script defaultScript = createMultiSigScript(defaultKeys, defaultThreshold);
        List<ScriptChunk> defaultScriptWithOpCheckMultiSigRemovedChunks = ErpRedeemScriptBuilderUtils.removeOpCheckMultisig(defaultScript);

        Script defaultScriptWithoutOpCheckMultiSig = createMultiSigScriptWithoutOpCheckMultisig(defaultKeys, defaultThreshold);
        List<ScriptChunk> defaultScriptWithoutOpCheckMultiSigChunks = defaultScriptWithoutOpCheckMultiSig.getChunks();

        assertEquals(defaultScriptWithOpCheckMultiSigRemovedChunks, defaultScriptWithoutOpCheckMultiSigChunks);
    }

    @ParameterizedTest
    @ValueSource(longs = {-100L, 0L, ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE + 1})
    void createInvalidErpFederation_invalidCsvValues(long csvValue) {
        activationDelayValue = csvValue;

        Script defaultScript = createMultiSigScript(defaultKeys, defaultThreshold);
        Script emergencyScript = createMultiSigScript(emergencyKeys, emergencyThreshold);

        ErpFederationCreationException exception = assertThrows(
            ErpFederationCreationException.class,
            () -> ErpRedeemScriptBuilderUtils.validateRedeemScriptValues(
                defaultScript,
                emergencyScript,
                activationDelayValue)
        );
        assertEquals(INVALID_CSV_VALUE, exception.getReason());
    }

    @Test
    void createValidErpFederation_exactMaxCsvValue() {
        Script defaultScript = createMultiSigScript(defaultKeys, defaultThreshold);
        Script emergencyScript = createMultiSigScript(emergencyKeys, emergencyThreshold);

        activationDelayValue = ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE;
        assertDoesNotThrow(
            () -> ErpRedeemScriptBuilderUtils.validateRedeemScriptValues(
            defaultScript,
            emergencyScript,
            activationDelayValue)
        );
    }

    @Test
    void createInvalidErpFederation_invalidDefaultRedeemScript_withoutThreshold() {
        Script defaultScript = createMultiSigScriptWithoutThreshold(defaultKeys);
        Script emergencyScript = createMultiSigScript(emergencyKeys, emergencyThreshold);

        ErpFederationCreationException exception = assertThrows(
            ErpFederationCreationException.class,
            () -> ErpRedeemScriptBuilderUtils.validateRedeemScriptValues(
                    defaultScript,
                    emergencyScript,
                    activationDelayValue)
        );
        assertEquals(INVALID_INTERNAL_REDEEM_SCRIPTS, exception.getReason());
    }

    @Test
    void createInvalidErpFederation_invalidEmergencyRedeemScript_withoutThreshold() {
        Script defaultScript = createMultiSigScript(defaultKeys, defaultThreshold);
        Script emergencyScript = createMultiSigScriptWithoutThreshold(emergencyKeys);

        ErpFederationCreationException exception = assertThrows(
            ErpFederationCreationException.class,
            () -> ErpRedeemScriptBuilderUtils.validateRedeemScriptValues(
                    defaultScript,
                    emergencyScript,
                    activationDelayValue)
        );
        assertEquals(INVALID_INTERNAL_REDEEM_SCRIPTS, exception.getReason());
    }

    @Test
    void createInvalidErpFederation_invalidDefaultRedeemScript_withZeroThreshold() {
        Script defaultScript = createMultiSigScript(defaultKeys, 0);
        Script emergencyScript = createMultiSigScript(emergencyKeys, emergencyThreshold);

        ErpFederationCreationException exception = assertThrows(
            ErpFederationCreationException.class,
            () -> ErpRedeemScriptBuilderUtils.validateRedeemScriptValues(
                defaultScript,
                emergencyScript,
                activationDelayValue)
        );
        assertEquals(INVALID_INTERNAL_REDEEM_SCRIPTS, exception.getReason());
    }

    @Test
    void createInvalidErpFederation_invalidEmergencyRedeemScript_withZeroThreshold() {
        Script defaultScript = createMultiSigScript(defaultKeys, defaultThreshold);
        Script emergencyScript = createMultiSigScript(emergencyKeys, 0);

        ErpFederationCreationException exception = assertThrows(
            ErpFederationCreationException.class,
            () -> ErpRedeemScriptBuilderUtils.validateRedeemScriptValues(
                defaultScript,
                emergencyScript,
                activationDelayValue)
        );
        assertEquals(INVALID_INTERNAL_REDEEM_SCRIPTS, exception.getReason());
    }

    @Test
    void createInvalidErpFederation_invalidDefaultRedeemScript_withoutKeysSize() {
        Script defaultScript = createMultiSigScriptWithoutKeysSize(defaultKeys, defaultThreshold);
        Script emergencyScript = createMultiSigScript(emergencyKeys, emergencyThreshold);

        ErpFederationCreationException exception = assertThrows(
            ErpFederationCreationException.class,
            () -> ErpRedeemScriptBuilderUtils.validateRedeemScriptValues(
                defaultScript,
                emergencyScript,
                activationDelayValue)
        );
        assertEquals(INVALID_INTERNAL_REDEEM_SCRIPTS, exception.getReason());
    }

    @Test
    void createInvalidErpFederation_invalidEmergencyRedeemScript_withoutKeysSize() {
        Script defaultScript = createMultiSigScript(defaultKeys, defaultThreshold);
        Script emergencyScript = createMultiSigScriptWithoutKeysSize(emergencyKeys, emergencyThreshold);

        ErpFederationCreationException exception = assertThrows(
            ErpFederationCreationException.class,
            () -> ErpRedeemScriptBuilderUtils.validateRedeemScriptValues(
                defaultScript,
                emergencyScript,
                activationDelayValue)
        );
        assertEquals(INVALID_INTERNAL_REDEEM_SCRIPTS, exception.getReason());
    }

    @Test
    void createInvalidErpFederation_invalidDefaultRedeemScript_withoutOpCheckMultisig() {
        Script defaultScript = createMultiSigScriptWithoutOpCheckMultisig(defaultKeys, defaultThreshold);
        Script emergencyScript = createMultiSigScript(emergencyKeys, emergencyThreshold);

        ErpFederationCreationException exception = assertThrows(
            ErpFederationCreationException.class,
            () -> ErpRedeemScriptBuilderUtils.validateRedeemScriptValues(
                    defaultScript,
                    emergencyScript,
                    activationDelayValue)
        );
        assertEquals(INVALID_INTERNAL_REDEEM_SCRIPTS, exception.getReason());
    }

    @Test
    void createInvalidErpFederation_invalidEmergencyRedeemScript_withoutOpCheckMultisig() {
        Script defaultScript = createMultiSigScript(defaultKeys, defaultThreshold);
        Script emergencyScript = createMultiSigScriptWithoutOpCheckMultisig(emergencyKeys, emergencyThreshold);

        ErpFederationCreationException exception = assertThrows(
            ErpFederationCreationException.class,
            () -> ErpRedeemScriptBuilderUtils.validateRedeemScriptValues(
                    defaultScript,
                    emergencyScript,
                    activationDelayValue)
        );
        assertEquals(INVALID_INTERNAL_REDEEM_SCRIPTS, exception.getReason());
    }

    private Script createMultiSigScript(List<BtcECKey> keys,
                                   int threshold) {
       ScriptBuilder scriptBuilder = new ScriptBuilder();
       scriptBuilder.smallNum(threshold);
       for (BtcECKey key : keys) {
           scriptBuilder.data(key.getPubKey());
       }
       scriptBuilder.smallNum(keys.size());
       scriptBuilder.op(OP_CHECKMULTISIG);

       return scriptBuilder.build();
    }

    private Script createMultiSigScriptWithoutThreshold(List<BtcECKey> keys) {
        ScriptBuilder scriptBuilder = new ScriptBuilder();
        for (BtcECKey key : keys) {
            scriptBuilder.data(key.getPubKey());
        }
        scriptBuilder.smallNum(keys.size());
        scriptBuilder.op(OP_CHECKMULTISIG);

        return scriptBuilder.build();
    }

    private Script createMultiSigScriptWithoutKeysSize(List<BtcECKey> keys,
                                        int threshold) {
        ScriptBuilder scriptBuilder = new ScriptBuilder();
        scriptBuilder.smallNum(threshold);
        for (BtcECKey key : keys) {
            scriptBuilder.data(key.getPubKey());
        }
        scriptBuilder.op(OP_CHECKMULTISIG);

        return scriptBuilder.build();
    }

    private Script createMultiSigScriptWithoutOpCheckMultisig(List<BtcECKey> keys,
                                                              int threshold) {
        ScriptBuilder scriptBuilder = new ScriptBuilder();
        scriptBuilder.smallNum(threshold);
        for (BtcECKey key : keys) {
            scriptBuilder.data(key.getPubKey());
        }
        scriptBuilder.smallNum(keys.size());

        return scriptBuilder.build();
    }
}
