package co.rsk.peg;

import static co.rsk.peg.ErpFederation.MAX_CSV_VALUE;
import static co.rsk.peg.FederationCreationException.Reason.*;
import static co.rsk.peg.bitcoin.Standardness.MAX_SCRIPT_ELEMENT_SIZE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.ScriptException;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.resources.TestConstants;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class LegacyErpFederationTest {
    private LegacyErpFederation federation;
    private NetworkParameters networkParameters;
    private List<BtcECKey> standardKeys;
    private List<BtcECKey> emergencyKeys;
    private long activationDelayValue;
    private ActivationConfig.ForBlock activations;

    @BeforeEach
    void setup() {
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03b53899c390573471ba30e5054f78376c5f797fda26dde7a760789f02908cbad2"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("027319afb15481dbeb3c426bcc37f9a30e7f51ceff586936d85548d9395bcc2344"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0355a2e9bf100c00fc0a214afd1bf272647c7824eb9cb055480962f0c382596a70"));
        BtcECKey federator3PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7"));
        BtcECKey federator4PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0294c817150f78607566e961b3c71df53a22022a80acbb982f83c0c8baac040adc"));
        BtcECKey federator5PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0372cd46831f3b6afd4c044d160b7667e8ebf659d6cb51a825a3104df6ee0638c6"));
        BtcECKey federator6PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0340df69f28d69eef60845da7d81ff60a9060d4da35c767f017b0dd4e20448fb44"));
        BtcECKey federator7PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02ac1901b6fba2c1dbd47d894d2bd76c8ba1d296d65f6ab47f1c6b22afb53e73eb"));
        BtcECKey federator8PublicKey = BtcECKey.fromPublicOnly(Hex.decode("031aabbeb9b27258f98c2bf21f36677ae7bae09eb2d8c958ef41a20a6e88626d26"));
        BtcECKey federator9PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0245ef34f5ee218005c9c21227133e8568a4f3f11aeab919c66ff7b816ae1ffeea"));
        standardKeys = Arrays.asList(
            federator0PublicKey, federator1PublicKey, federator2PublicKey,
            federator3PublicKey, federator4PublicKey, federator5PublicKey,
            federator6PublicKey, federator7PublicKey, federator8PublicKey, federator9PublicKey
        );

        networkParameters = bridgeConstants.getBtcParams();
        emergencyKeys = bridgeConstants.getErpFedPubKeysList();
        activationDelayValue = bridgeConstants.getErpFedActivationDelay();
        activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        federation = createDefaultLegacyErpFederation();
    }

    private LegacyErpFederation createDefaultLegacyErpFederation() {
        List<FederationMember> standardMembers = FederationTestUtils.getFederationMembersWithBtcKeys(standardKeys);
        Instant creationTime = ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant();
        long creationBlockNumber = 0L;

        return new LegacyErpFederation(
            standardMembers,
            creationTime,
            creationBlockNumber,
            networkParameters,
            emergencyKeys,
            activationDelayValue,
            activations
        );
    }

    @Test
    void createInvalidLegacyErpFederation_nullErpKeys() {
        emergencyKeys = null;
        FederationCreationException exception = assertThrows(
            FederationCreationException.class, this::createDefaultLegacyErpFederation
        );
        assertEquals(NULL_OR_EMPTY_EMERGENCY_KEYS, exception.getReason());
    }

    @Test
    void createInvalidLegacyErpFederation_emptyErpKeys() {
        emergencyKeys = new ArrayList<>();
        FederationCreationException exception = assertThrows(
            FederationCreationException.class, this::createDefaultLegacyErpFederation
        );
        assertEquals(NULL_OR_EMPTY_EMERGENCY_KEYS, exception.getReason());
    }

    @Test
    void createValidLegacyErpFederation_oneErpKey() {
        emergencyKeys = Collections.singletonList(emergencyKeys.get(0));
        assertDoesNotThrow(this::createDefaultLegacyErpFederation);
    }
    @Test
    void createInvalidLegacyErpFederation_negativeCsvValue() {
        activationDelayValue = -100L;
        FederationCreationException exception = assertThrows(
            FederationCreationException.class, this::createDefaultLegacyErpFederation
        );
        assertEquals(INVALID_CSV_VALUE, exception.getReason());
    }

    @Test
    void createInvalidLegacyErpFederation_zeroCsvValue() {
        activationDelayValue = 0L;
        FederationCreationException exception = assertThrows(
            FederationCreationException.class, this::createDefaultLegacyErpFederation
        );
        assertEquals(INVALID_CSV_VALUE, exception.getReason());
    }

    @Test
    void createInvalidLegacyErpFederation_aboveMaxCsvValue() {
        activationDelayValue = MAX_CSV_VALUE + 1;
        FederationCreationException exception = assertThrows(
            FederationCreationException.class, this::createDefaultLegacyErpFederation
        );
        assertEquals(INVALID_CSV_VALUE, exception.getReason());
    }

    @Test
    void createValidLegacyErpFederation_exactMaxCsvValue() {
        activationDelayValue = MAX_CSV_VALUE;
        assertDoesNotThrow(this::createDefaultLegacyErpFederation);
    }

    @Test
    void createInvalidLegacyErpFederation_aboveMaxScriptSigSize() {
        // add one member to exceed redeem script size limit
        List<BtcECKey> newStandardKeys = federation.getBtcPublicKeys();
        BtcECKey federator10PublicKey = BtcECKey.fromPublicOnly(
            Hex.decode("02550cc87fa9061162b1dd395a16662529c9d8094c0feca17905a3244713d65fe8")
        );
        newStandardKeys.add(federator10PublicKey);
        standardKeys = newStandardKeys;
        FederationCreationException exception = assertThrows(
            FederationCreationException.class, this::createDefaultLegacyErpFederation
        );
        assertEquals(ABOVE_MAX_SCRIPT_ELEMENT_SIZE, exception.getReason());
    }

    @Test
    void getErpPubKeys() {
        assertEquals(emergencyKeys, federation.getErpPubKeys());
    }

    @Test
    void getActivationDelay() {
        assertEquals(activationDelayValue, federation.getActivationDelay());
    }

    @Test
    void testEquals_basic() {
        assertEquals(federation, federation);

        assertNotEquals(null, federation);
        assertNotEquals(federation, new Object());
        assertNotEquals("something else", federation);
    }

    @Test
    void testEquals_same() {
        ErpFederation otherFederation = new LegacyErpFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );

        assertEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentCreationTime() {
        ErpFederation otherFederation = new LegacyErpFederation(
            federation.getMembers(),
            federation.getCreationTime().plus(1, ChronoUnit.MILLIS),
            federation.getCreationBlockNumber(),
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );
        assertEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentCreationBlockNumber() {
        ErpFederation otherFederation = new LegacyErpFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber() + 1,
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            activations
        );
        assertEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentNetworkParameters() {
        networkParameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        ErpFederation otherFederation = createDefaultLegacyErpFederation();
        Assertions.assertNotEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentNumberOfMembers() {
        // remove federator9
        List<BtcECKey> newStandardKeys = federation.getBtcPublicKeys();
        newStandardKeys.remove(9);
        standardKeys = newStandardKeys;

        ErpFederation otherFederation = createDefaultLegacyErpFederation();
        Assertions.assertNotEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentMembers() {
        // replace federator8 with federator9
        BtcECKey federator9PublicKey = BtcECKey.fromPublicOnly(
            Hex.decode("0245ef34f5ee218005c9c21227133e8568a4f3f11aeab919c66ff7b816ae1ffeea")
        );
        List<BtcECKey> newStandardKeys = federation.getBtcPublicKeys();
        newStandardKeys.remove(8);
        newStandardKeys.add(federator9PublicKey);
        standardKeys = newStandardKeys;

        ErpFederation otherFederation = createDefaultLegacyErpFederation();
        Assertions.assertNotEquals(federation, otherFederation);
    }

    @Test
    void getP2SHScriptAndAddress() {
        // standard and emergency keys from real legacy erp fed in testnet
        BridgeConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();
        networkParameters = bridgeTestNetConstants.getBtcParams();
        emergencyKeys = bridgeTestNetConstants.getErpFedPubKeysList();
        activationDelayValue = bridgeTestNetConstants.getErpFedActivationDelay();

        standardKeys = Arrays.asList(
            BtcECKey.fromPublicOnly(
                Hex.decode("0208f40073a9e43b3e9103acec79767a6de9b0409749884e989960fee578012fce")
            ),
            BtcECKey.fromPublicOnly(
                Hex.decode("0225e892391625854128c5c4ea4340de0c2a70570f33db53426fc9c746597a03f4")
            ),
            BtcECKey.fromPublicOnly(
                Hex.decode("025a2f522aea776fab5241ad72f7f05918e8606676461cb6ce38265a52d4ca9ed6")
            ),
            BtcECKey.fromPublicOnly(
                Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da")
            ),
            BtcECKey.fromPublicOnly(
                Hex.decode("0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09")
            )
        );

        ErpFederation realLegacyErpFederation = createDefaultLegacyErpFederation();
        Script p2shScript = realLegacyErpFederation.getP2SHScript();
        Address address = realLegacyErpFederation.getAddress();

        String expectedProgram = "a9148f38b3d8ec8816f7f58a390f306bb90bb178d6ac87";
        Address expectedAddress = Address.fromBase58(
            networkParameters,
            "2N6JWYUb6Li4Kux6UB2eihT7n3rm3YX97uv"
        );

        assertEquals(expectedProgram, Hex.toHexString(p2shScript.getProgram()));
        assertEquals(3, p2shScript.getChunks().size());
        assertEquals(
            address,
            p2shScript.getToAddress(networkParameters)
        );
        assertEquals(expectedAddress, address);
    }

    @Test
    void getErpPubKeys_uncompressed_public_keys() {
        // Public keys used for creating federation, but uncompressed format now
        emergencyKeys = emergencyKeys
            .stream()
            .map(BtcECKey::decompress)
            .collect(Collectors.toList());

        // Recreate federation
        ErpFederation federationWithUncompressedKeys = createDefaultLegacyErpFederation();
        assertEquals(emergencyKeys, federationWithUncompressedKeys.getErpPubKeys());
    }

    @Test
    void getLegacyErpRedeemScript_compareOtherImplementation() throws IOException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        byte[] rawRedeemScripts;
        try {
            rawRedeemScripts = Files.readAllBytes(Paths.get("src/test/resources/redeemScripts.json"));
        } catch (IOException e) {
            System.out.println("redeemScripts.json file not found");
            throw(e);
        }

        RawGeneratedRedeemScript[] generatedScripts = new ObjectMapper().readValue(rawRedeemScripts, RawGeneratedRedeemScript[].class);
        for (RawGeneratedRedeemScript generatedScript : generatedScripts) {
            // Skip test cases with invalid redeem script that exceed the maximum size
            if (generatedScript.script.getProgram().length <= MAX_SCRIPT_ELEMENT_SIZE) {
                Federation erpFederation = new LegacyErpFederation(
                    FederationTestUtils.getFederationMembersWithBtcKeys(generatedScript.mainFed),
                    ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                    1,
                    NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
                    generatedScript.emergencyFed,
                    generatedScript.timelock,
                    activations
                );

                Script rskjScript = erpFederation.getRedeemScript();
                Script alternativeScript = generatedScript.script;

                assertEquals(alternativeScript, rskjScript);
            }
        }
    }

    @Test
    void getRedeemScript_before_RSKIP293() {
        Script redeemScript = federation.getRedeemScript();
        validateErpRedeemScript(
            redeemScript,
            activationDelayValue,
            false
        );
    }

    @Test
    void getRedeemScript_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        federation = createDefaultLegacyErpFederation();
        Script redeemScript = federation.getRedeemScript();

        validateErpRedeemScript(
            redeemScript,
            activationDelayValue,
            true
        );
    }

    @Test
    void getRedeemScript_changes_after_RSKIP293() {
        Script preRskip293RedeemScript = federation.getRedeemScript();

        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        federation = createDefaultLegacyErpFederation();
        Script postRskip293RedeemScript = federation.getRedeemScript();

        Assertions.assertNotEquals(preRskip293RedeemScript, postRskip293RedeemScript);
    }

    @Test
    void createErpFederation_testnet_constants_before_RSKIP293() {
        createErpFederation(BridgeTestNetConstants.getInstance(), false);
    }

    @Test
    void createErpFederation_testnet_constants_after_RSKIP293() {
        createErpFederation(BridgeTestNetConstants.getInstance(), true);
    }

    @Test
    void createErpFederation_mainnet_constants_before_RSKIP293() {
        createErpFederation(BridgeMainNetConstants.getInstance(), false);
    }

    @Test
    void createErpFederation_mainnet_constants_after_RSKIP293() {
        createErpFederation(BridgeMainNetConstants.getInstance(), true);
    }

    @Test
    void getRedeemScript_before_RSKIP_284_testnet() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        Federation erpFederation = new LegacyErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            1,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            emergencyKeys,
            activationDelayValue,
            activations
        );

        assertEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, erpFederation.getRedeemScript());
    }

    @Test
    void getRedeemScript_before_RSKIP_284_mainnet() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        Federation erpFederation = new LegacyErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(standardKeys),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            1,
            NetworkParameters.fromID(NetworkParameters.ID_MAINNET),
            emergencyKeys,
            activationDelayValue,
            activations
        );

        Assertions.assertNotEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, erpFederation.getRedeemScript());
        validateErpRedeemScript(
            erpFederation.getRedeemScript(),
            activationDelayValue,
            false
        );
    }

    @Test
    void getRedeemScript_after_RSKIP_284_testnet() {
        Federation erpFederation = new LegacyErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(standardKeys),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            1,
            NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
            emergencyKeys,
            activationDelayValue,
            activations
        );

        Assertions.assertNotEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, erpFederation.getRedeemScript());
        validateErpRedeemScript(
            erpFederation.getRedeemScript(),
            activationDelayValue,
            false
        );
    }

    @Test
    void getRedeemScript_after_RSKIP_284_mainnet() {
        Federation erpFederation = new LegacyErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(standardKeys),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            1,
            NetworkParameters.fromID(NetworkParameters.ID_MAINNET),
            emergencyKeys,
            activationDelayValue,
            activations
        );

        Assertions.assertNotEquals(TestConstants.ERP_TESTNET_REDEEM_SCRIPT, erpFederation.getRedeemScript());
        validateErpRedeemScript(
            erpFederation.getRedeemScript(),
            activationDelayValue,
            false
        );
    }

    @Test
    void testEquals_differentRedeemScript() {
        ActivationConfig.ForBlock activationsPre = mock(ActivationConfig.ForBlock.class);
        when(activationsPre.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        ActivationConfig.ForBlock activationsPost = mock(ActivationConfig.ForBlock.class);
        when(activationsPost.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        networkParameters = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        // Both federations created before RSKIP284 with the same data, should have the same redeem script
        activations = activationsPre;

        Federation erpFederation = createDefaultLegacyErpFederation();
        Federation otherErpFederation = createDefaultLegacyErpFederation();
        assertEquals(erpFederation, otherErpFederation);

        activations = activationsPost;
        // One federation created after RSKIP284 with the same data, should have different redeem script
        otherErpFederation = createDefaultLegacyErpFederation();
        assertNotEquals(erpFederation, otherErpFederation);

        // The other federation created after RSKIP284 with the same data, should have same redeem script
        erpFederation = createDefaultLegacyErpFederation();
        assertEquals(erpFederation, otherErpFederation);
    }

    @Disabled("Can't recreate the hardcoded redeem script since the needed CSV value is above the max. Keeping the test ignored as testimonial")
    @Test
    void createErpFedWithSameRedeemScriptAsHardcodedOne_after_RSKIP293_fails() {
        // We can't test the same condition before RSKIP293 since the serialization used by bj-thin
        // prior to RSKIP293 enforces the CSV value to be encoded using 2 bytes.
        // The hardcoded script has a 3 byte long CSV value
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        List<BtcECKey> standardMultisigKeys = Arrays.stream(new String[]{
            "0208f40073a9e43b3e9103acec79767a6de9b0409749884e989960fee578012fce",
            "0225e892391625854128c5c4ea4340de0c2a70570f33db53426fc9c746597a03f4",
            "02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da",
            "0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09",
            "039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        List<BtcECKey> emergencyMultisigKeys = Arrays.stream(new String[]{
            "0216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3",
            "0275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f14",
            "034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        long activationDelay = 5_295_360L;

        List<FederationMember> federationMembersWithBtcKeys = FederationTestUtils.getFederationMembersWithBtcKeys(standardMultisigKeys);
        Instant creationTime = ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant();
        NetworkParameters btcParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);
        assertThrows(FederationCreationException.class, () -> new LegacyErpFederation(
            federationMembersWithBtcKeys,
            creationTime,
            1,
            btcParams,
            emergencyMultisigKeys,
            activationDelay,
            activations
        ));
    }

    @Test
    void spendFromErpFed_before_RSKIP293_testnet_using_erp_multisig_can_spend() {
        BridgeConstants constants = BridgeTestNetConstants.getInstance();

        // The CSV value defined in BridgeTestnetConstants,
        // actually allows the emergency multisig to spend before the expected amount of blocks
        // Since it's encoded as BE and decoded as LE, the result is a number lower than the one defined in the constant
        assertDoesNotThrow(() ->
            spendFromErpFed(
                constants.getBtcParams(),
                constants.getErpFedActivationDelay(),
                false,
                true
            ));
    }

    @Test
    void spendFromErpFed_before_RSKIP293_testnet_using_erp_multisig_cant_spend() {
        BridgeConstants constants = BridgeTestNetConstants.getInstance();

        // Should fail due to the wrong encoding of the CSV value
        // In this case, the value 300 when encoded as BE and decoded as LE results in a larger number
        // This causes the validation to fail
        NetworkParameters btcParams = constants.getBtcParams();
        assertThrows(ScriptException.class, () -> spendFromErpFed(
            btcParams,
            300,
            false,
            true
        ));
    }

    @Test
    void spendFromErpFed_before_RSKIP293_testnet_using_standard_multisig() {
        BridgeConstants constants = BridgeTestNetConstants.getInstance();

        // Should validate since it's not executing the path of the script with the CSV value
        assertDoesNotThrow(() -> spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            false,
            false
        ));
    }

    @Test
    void spendFromErpFed_before_RSKIP293_mainnet_using_erp_multisig_can_spend() {
        BridgeConstants constants = BridgeMainNetConstants.getInstance();

        // The CSV value defined in BridgeMainnetConstants,
        // actually allows the emergency multisig to spend before the expected amount of blocks
        // Since it's encoded as BE and decoded as LE, the result is a number lower than the one defined in the constant
        assertDoesNotThrow(() -> spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            false,
            true
        ));
    }

    @Test
    void spendFromErpFed_before_RSKIP293_mainnet_using_erp_multisig_cant_spend() {
        BridgeConstants constants = BridgeMainNetConstants.getInstance();

        // Should fail due to the wrong encoding of the CSV value
        // In this case, the value 300 when encoded as BE and decoded as LE results in a larger number
        // This causes the validation to fail
        NetworkParameters btcParams = constants.getBtcParams();
        assertThrows(ScriptException.class, () -> spendFromErpFed(
            btcParams,
            300,
            false,
            true
        ));
    }

    @Test
    void spendFromErpFed_before_RSKIP293_mainnet_using_standard_multisig() {
        BridgeConstants constants = BridgeMainNetConstants.getInstance();

        // Should validate since it's not executing the path of the script with the CSV value
        assertDoesNotThrow(() -> spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            false,
            false
        ));
    }

    @Test
    void spendFromErpFed_after_RSKIP293_testnet_using_erp_multisig() {
        BridgeConstants constants = BridgeTestNetConstants.getInstance();

        // Post RSKIP293 activation it should encode the CSV value correctly
        assertDoesNotThrow(() -> spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            true,
            true
        ));
    }

    @Test
    void spendFromErpFed_after_RSKIP293_testnet_using_standard_multisig() {
        BridgeConstants constants = BridgeTestNetConstants.getInstance();

        assertDoesNotThrow(() -> spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            true,
            false
        ));
    }

    @Test
    void spendFromErpFed_after_RSKIP293_mainnet_using_erp_multisig() {
        BridgeConstants constants = BridgeMainNetConstants.getInstance();

        // Post RSKIP293 activation it should encode the CSV value correctly
        assertDoesNotThrow(() -> spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            true,
            true
        ));
    }

    @Test
    void spendFromErpFed_after_RSKIP293_mainnet_using_standard_multisig() {
        BridgeConstants constants = BridgeMainNetConstants.getInstance();

        assertDoesNotThrow(() -> spendFromErpFed(
            constants.getBtcParams(),
            constants.getErpFedActivationDelay(),
            true,
            false
        ));
    }

    @Test
    void test_getMemberByBtcPublicKey_passing_existing_btcPublicKey(){
        BtcECKey existingMemberBtcPublicKey = standardKeys.get(0);
        Optional<FederationMember> foundMember = federation.getMemberByBtcPublicKey(existingMemberBtcPublicKey);
        Assertions.assertTrue(foundMember.isPresent());
        Assertions.assertEquals(existingMemberBtcPublicKey, foundMember.get().getBtcPublicKey());
    }

    @Test
    void test_getMemberByBtcPublicKey_passing_non_existing_btcPublicKey(){
        BtcECKey noExistingBtcPublicKey = new BtcECKey();
        Optional<FederationMember> foundMember = federation.getMemberByBtcPublicKey(noExistingBtcPublicKey);
        Assertions.assertFalse(foundMember.isPresent());
    }

    @Test
    void test_getMemberByBtcPublicKey_passing_null_btcPublicKey(){
        Optional<FederationMember> foundMember = federation.getMemberByBtcPublicKey(null);
        Assertions.assertFalse(foundMember.isPresent());
    }

    private void createErpFederation(BridgeConstants constants, boolean isRskip293Active) {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRskip293Active);

        Federation erpFederation = new LegacyErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(standardKeys),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            1,
            constants.getBtcParams(),
            constants.getErpFedPubKeysList(),
            constants.getErpFedActivationDelay(),
            activations
        );

        validateErpRedeemScript(
            erpFederation.getRedeemScript(),
            standardKeys,
            constants.getErpFedPubKeysList(),
            constants.getErpFedActivationDelay(),
            isRskip293Active
        );
    }

    private void spendFromErpFed(
        NetworkParameters networkParameters,
        long activationDelay,
        boolean isRskip293Active,
        boolean signWithEmergencyMultisig) {

        List<BtcECKey> standardKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7", "fed8", "fed9", "fed10"},
            true
        );

        List<BtcECKey> emergencyKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"erp1", "erp2", "erp3", "erp4"},
            true
        );

        List<ConsensusRule> except = isRskip293Active ?
            Collections.emptyList() :
            Collections.singletonList(ConsensusRule.RSKIP293);
        ActivationConfig activations = ActivationConfigsForTest.hop400(except);

        ErpFederation erpFed = new LegacyErpFederation(
            FederationMember.getFederationMembersFromKeys(standardKeys),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            1,
            networkParameters,
            emergencyKeys,
            activationDelay,
            activations.forBlock(0)
        );

        Coin value = Coin.valueOf(1_000_000);
        Coin fee = Coin.valueOf(10_000);
        BtcTransaction fundTx = new BtcTransaction(networkParameters);
        fundTx.addOutput(value, erpFed.getAddress());

        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(
            networkParameters,
            "destination"
        );

        FederationTestUtils.spendFromErpFed(
            networkParameters,
            erpFed,
            signWithEmergencyMultisig ? emergencyKeys : standardKeys,
            fundTx.getHash(),
            0,
            destinationAddress,
            value.minus(fee),
            signWithEmergencyMultisig
        );
    }

    private void validateErpRedeemScript(
        Script erpRedeemScript,
        Long csvValue,
        boolean isRskip293Active) {

        validateErpRedeemScript(
            erpRedeemScript,
            standardKeys,
            emergencyKeys,
            csvValue,
            isRskip293Active
        );
    }

    private void validateErpRedeemScript(
        Script erpRedeemScript,
        List<BtcECKey> defaultMultisigKeys,
        List<BtcECKey> emergencyMultisigKeys,
        Long csvValue,
        boolean isRskip293Active) {

        // Keys are sorted when added to the redeem script, so we need them sorted in order to validate
        defaultMultisigKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        emergencyMultisigKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        int expectedCsvValueLength = isRskip293Active ? BigInteger.valueOf(csvValue).toByteArray().length : 2;
        byte[] serializedCsvValue = isRskip293Active ?
            Utils.signedLongToByteArrayLE(csvValue) :
            Utils.unsignedLongToByteArrayBE(csvValue, expectedCsvValueLength);

        byte[] script = erpRedeemScript.getProgram();
        Assertions.assertTrue(script.length > 0);

        int index = 0;

        // First byte should equal OP_NOTIF
        assertEquals(ScriptOpCodes.OP_NOTIF, script[index++]);

        // Next byte should equal M, from an M/N multisig
        int m = defaultMultisigKeys.size() / 2 + 1;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        // Assert public keys
        for (BtcECKey key: defaultMultisigKeys) {
            byte[] pubkey = key.getPubKey();
            assertEquals(pubkey.length, script[index++]);
            for (byte b : pubkey) {
                assertEquals(b, script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        int n = defaultMultisigKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(n)), script[index++]);

        // Next byte should equal OP_ELSE
        assertEquals(ScriptOpCodes.OP_ELSE, script[index++]);

        // Next byte should equal csv value length
        assertEquals(expectedCsvValueLength, script[index++]);

        // Next bytes should equal the csv value in bytes
        for (int i = 0; i < expectedCsvValueLength; i++) {
            assertEquals(serializedCsvValue[i], script[index++]);
        }

        assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY).byteValue(), script[index++]);
        assertEquals(ScriptOpCodes.OP_DROP, script[index++]);

        // Next byte should equal M, from an M/N multisig
        m = emergencyMultisigKeys.size() / 2 + 1;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        for (BtcECKey key: emergencyMultisigKeys) {
            byte[] pubkey = key.getPubKey();
            assertEquals(Integer.valueOf(pubkey.length).byteValue(), script[index++]);
            for (byte b : pubkey) {
                assertEquals(b, script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        n = emergencyMultisigKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(n)), script[index++]);

        assertEquals(ScriptOpCodes.OP_ENDIF, script[index++]);
        assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKMULTISIG).byteValue(), script[index++]);
    }

    private static class RawGeneratedRedeemScript {
        List<BtcECKey> mainFed;
        List<BtcECKey> emergencyFed;
        Long timelock;
        Script script;

        @JsonCreator
        public RawGeneratedRedeemScript(@JsonProperty("mainFed") List<String> mainFed,
            @JsonProperty("emergencyFed") List<String> emergencyFed,
            @JsonProperty("timelock") Long timelock,
            @JsonProperty("script") String script) {
            this.mainFed = parseFed(mainFed);
            this.emergencyFed = parseFed(emergencyFed);
            this.timelock = timelock;
            this.script = new Script(Hex.decode(script));
        }

        private List<BtcECKey> parseFed(List<String> fed) {
            return fed.stream().map(Hex::decode).map(BtcECKey::fromPublicOnly).collect(Collectors.toList());
        }
    }
}
