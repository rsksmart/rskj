package co.rsk.peg;

import static co.rsk.bitcoinj.script.Script.MAX_SCRIPT_ELEMENT_SIZE;
import static co.rsk.peg.ErpFederationCreationException.Reason.NULL_OR_EMPTY_EMERGENCY_KEYS;
import static co.rsk.peg.ErpFederationCreationException.Reason.REDEEM_SCRIPT_CREATION_FAILED;
import static co.rsk.peg.bitcoin.RedeemScriptCreationException.Reason.INVALID_CSV_VALUE;
import static co.rsk.peg.bitcoin.ScriptCreationException.Reason.ABOVE_MAX_SCRIPT_ELEMENT_SIZE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.*;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.bitcoin.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class P2shErpFederationTest {
    private ErpFederation federation;
    private NetworkParameters networkParameters;
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
        BtcECKey federator5PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0372cd46831f3b6afd4c044d160b7667e8ebf659d6cb51a825a3104df6ee0638c6"));
        BtcECKey federator6PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0340df69f28d69eef60845da7d81ff60a9060d4da35c767f017b0dd4e20448fb44"));
        BtcECKey federator7PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02ac1901b6fba2c1dbd47d894d2bd76c8ba1d296d65f6ab47f1c6b22afb53e73eb"));
        BtcECKey federator8PublicKey = BtcECKey.fromPublicOnly(Hex.decode("031aabbeb9b27258f98c2bf21f36677ae7bae09eb2d8c958ef41a20a6e88626d26"));
        BtcECKey federator9PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0245ef34f5ee218005c9c21227133e8568a4f3f11aeab919c66ff7b816ae1ffeea"));
        defaultKeys = Arrays.asList(
            federator0PublicKey, federator1PublicKey, federator2PublicKey,
            federator3PublicKey, federator4PublicKey, federator5PublicKey,
            federator6PublicKey, federator7PublicKey, federator8PublicKey, federator9PublicKey
        );
        defaultThreshold = defaultKeys.size() / 2 + 1;
        emergencyKeys = bridgeConstants.getErpFedPubKeysList();
        emergencyThreshold = emergencyKeys.size() / 2 + 1;
        activationDelayValue = bridgeConstants.getErpFedActivationDelay();

        networkParameters = bridgeConstants.getBtcParams();

        federation = createDefaultP2shErpFederation();
    }

    private ErpFederation createDefaultP2shErpFederation() {
        List<FederationMember> standardMembers = FederationTestUtils.getFederationMembersWithBtcKeys(defaultKeys);
        Instant creationTime = ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant();
        long creationBlockNumber = 0L;

        return new ErpFederation(
            standardMembers,
            creationTime,
            creationBlockNumber,
            networkParameters,
            emergencyKeys,
            activationDelayValue,
            new P2shErpRedeemScriptBuilder()
        );
    }

    private void createAndValidateFederation() {
        federation = createDefaultP2shErpFederation();

        validateP2shErpRedeemScript(
            federation.getRedeemScript(),
            defaultKeys,
            emergencyKeys,
            activationDelayValue
        );
    }

    @Test
    void createFederation_withNullErpKeys_throwsErpFederationCreationException() {
        emergencyKeys = null;

        ErpFederationCreationException exception = assertThrows(
            ErpFederationCreationException.class, this::createDefaultP2shErpFederation
        );
        assertEquals(NULL_OR_EMPTY_EMERGENCY_KEYS, exception.getReason());
    }

    @Test
    void createFederation_withEmptyErpKeys_throwsErpFederationCreationException() {
        emergencyKeys = new ArrayList<>();

        ErpFederationCreationException exception = assertThrows(
            ErpFederationCreationException.class, this::createDefaultP2shErpFederation
        );
        assertEquals(NULL_OR_EMPTY_EMERGENCY_KEYS, exception.getReason());
    }

    @Test
    void createFederation_withOneErpKey_valid() {
        emergencyKeys = Collections.singletonList(emergencyKeys.get(0));
        createAndValidateFederation();
    }

    @ParameterizedTest
    @ValueSource(longs = {20L, 130L, 500L, 33_000L, ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE})
    void createFederation_withValidCsvValues_valid(long csvValue) {
        activationDelayValue = csvValue;

        createAndValidateFederation();
    }

    @ParameterizedTest
    @ValueSource(longs = {-100L, 0L, ErpRedeemScriptBuilderUtils.MAX_CSV_VALUE + 1, 100_000L, 8_400_000L})
    void createFederation_invalidCsvValues_throwsErpFederationCreationException(long csvValue) {
        activationDelayValue = csvValue;

        federation = createDefaultP2shErpFederation();
        ErpFederationCreationException fedException = assertThrows(
            ErpFederationCreationException.class,
            () -> federation.getRedeemScript());
        assertEquals(REDEEM_SCRIPT_CREATION_FAILED, fedException.getReason());

        // Check the builder throws the particular expected exception
        ErpRedeemScriptBuilder builder = federation.getErpRedeemScriptBuilder();
        RedeemScriptCreationException builderException = assertThrows(
            RedeemScriptCreationException.class,
            () -> builder.createRedeemScriptFromKeys(
                defaultKeys, defaultThreshold,
                emergencyKeys, emergencyThreshold,
                activationDelayValue
            ));
        assertEquals(INVALID_CSV_VALUE, builderException.getReason());
    }

    @Test
    void createFederation_withRedeemScriptSizeAboveMaximum_throwsScriptCreationException() {
        // add one member to exceed redeem script size limit
        List<BtcECKey> newDefaultKeys = federation.getBtcPublicKeys();
        BtcECKey federator10PublicKey = BtcECKey.fromPublicOnly(
            Hex.decode("02550cc87fa9061162b1dd395a16662529c9d8094c0feca17905a3244713d65fe8")
        );
        newDefaultKeys.add(federator10PublicKey);
        defaultKeys = newDefaultKeys;

        ErpRedeemScriptBuilder builder = federation.getErpRedeemScriptBuilder();
        ScriptCreationException exception = assertThrows(
            ScriptCreationException.class,
            () -> builder.createRedeemScriptFromKeys(
                defaultKeys, defaultThreshold,
                emergencyKeys, emergencyThreshold,
                activationDelayValue
            ));
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

        Assertions.assertNotEquals(null, federation);
        Assertions.assertNotEquals(federation, new Object());
        Assertions.assertNotEquals("something else", federation);
    }

    @Test
    void testEquals_same() {
        P2shErpRedeemScriptBuilder p2shErpRedeemScriptBuilder = new P2shErpRedeemScriptBuilder();
        ErpFederation otherFederation = new ErpFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            federation.getBtcParams(),
            federation.getErpPubKeys(),
            federation.getActivationDelay(),
            p2shErpRedeemScriptBuilder
        );

        assertEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentNumberOfMembers() {
        // remove federator9
        List<BtcECKey> newDefaultKeys = federation.getBtcPublicKeys();
        newDefaultKeys.remove(9);
        defaultKeys = newDefaultKeys;

        ErpFederation otherFederation = createDefaultP2shErpFederation();
        Assertions.assertNotEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentNetworkParameters() {
        networkParameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        ErpFederation otherFederation = createDefaultP2shErpFederation();
        Assertions.assertNotEquals(federation, otherFederation);
    }

    @Test
    void testEquals_differentMembers() {
        BtcECKey federator9PublicKey = BtcECKey.fromPublicOnly(
            Hex.decode("0245ef34f5ee218005c9c21227133e8568a4f3f11aeab919c66ff7b816ae1ffeea")
        );
        // replace federator8 with federator9
        List<BtcECKey> newDefaultKeys = federation.getBtcPublicKeys();
        newDefaultKeys.remove(8);
        newDefaultKeys.add(federator9PublicKey);
        defaultKeys = newDefaultKeys;

        ErpFederation otherFederation = createDefaultP2shErpFederation();
        Assertions.assertNotEquals(federation, otherFederation);
    }

    @Test
    void createdRedeemScriptProgramFromP2shErpBuilder_withRealValues_equalsRealRedeemScriptProgram() {
        // this is a known redeem script program
        byte[] redeemScriptProgram = Hex.decode("64542102099fd69cf6a350679a05593c3ff814bfaa281eb6dde505c953cf2875979b120921022a159227df514c7b7808ee182ae07d71770b67eda1e5ee668272761eefb2c24c210233bc8c1a994a921d7818f93e57a559373133ba531928843bf84c59c15e47eab02102937df9948c6f18359e473beeee0a19c27dd4f6d4114e5809aa862671bb765b5b2102afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da2103db2ebad883823cefe8b2336c03b8d9c6afee4cbac77c7e935bc8c51ec20b26632103fb8e1d5d0392d35ca8c3656acb6193dbf392b3e89b9b7b86693f5c80f7ce858157ae670350cd00b27552210216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3210275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f1421034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f53ae68");

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02099fd69cf6a350679a05593c3ff814bfaa281eb6dde505c953cf2875979b1209"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("022a159227df514c7b7808ee182ae07d71770b67eda1e5ee668272761eefb2c24c"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0233bc8c1a994a921d7818f93e57a559373133ba531928843bf84c59c15e47eab0"));
        BtcECKey federator3PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02937df9948c6f18359e473beeee0a19c27dd4f6d4114e5809aa862671bb765b5b"));
        BtcECKey federator4PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da"));
        BtcECKey federator5PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03db2ebad883823cefe8b2336c03b8d9c6afee4cbac77c7e935bc8c51ec20b2663"));
        BtcECKey federator6PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03fb8e1d5d0392d35ca8c3656acb6193dbf392b3e89b9b7b86693f5c80f7ce8581"));
        defaultKeys = Arrays.asList(
            federator0PublicKey, federator1PublicKey, federator2PublicKey,
            federator3PublicKey, federator4PublicKey, federator5PublicKey,
            federator6PublicKey
        );
        defaultThreshold = defaultKeys.size() / 2 + 1;

        BtcECKey emergency0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3"));
        BtcECKey emergency1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f14"));
        BtcECKey emergency2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f"));
        emergencyKeys = Arrays.asList(
            emergency0PublicKey, emergency1PublicKey, emergency2PublicKey
        );
        emergencyThreshold = emergencyKeys.size() / 2 + 1;

        activationDelayValue = 52_560L;

        createAndValidateFederation();

        ErpRedeemScriptBuilder builder = federation.getErpRedeemScriptBuilder();
        Script obtainedRedeemScript =
            builder.createRedeemScriptFromKeys(
                defaultKeys, defaultThreshold,
                emergencyKeys, emergencyThreshold,
                activationDelayValue
            );
        assertArrayEquals(redeemScriptProgram, obtainedRedeemScript.getProgram());
    }

    @ParameterizedTest
    @MethodSource("getRedeemScriptArgsProvider")
    void getRedeemScript(BridgeConstants bridgeConstants) {
        if (!(bridgeConstants instanceof BridgeMainNetConstants)) {
            // should add this case because adding erp to mainnet genesis federation
            // throws a validation error, so in that case we use the one set up before each test.
            // if using testnet constants, we can add them with no errors
            defaultKeys = bridgeConstants.getGenesisFederation().getBtcPublicKeys();
        }

        emergencyKeys = bridgeConstants.getErpFedPubKeysList();
        activationDelayValue = bridgeConstants.getErpFedActivationDelay();

        ErpFederation p2shErpFederation = createDefaultP2shErpFederation();
        validateP2shErpRedeemScript(
            p2shErpFederation.getRedeemScript(),
            defaultKeys,
            emergencyKeys,
            activationDelayValue
        );
    }

    @Test
    void getStandardRedeemScript() {
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            Arrays.asList(new BtcECKey(), new BtcECKey(), new BtcECKey())
        );
        Instant creationTime = Instant.now();
        int creationBlock = 0;
        NetworkParameters btcParams = BridgeRegTestConstants.getInstance().getBtcParams();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);

        // Create a legacy powpeg and then a p2sh valid one. Both of them should produce the same standard redeem script
        StandardMultisigFederation legacyFed = new StandardMultisigFederation(
            members,
            creationTime,
            creationBlock,
            btcParams
        );
        ErpFederation p2shFed = new ErpFederation(
            members,
            creationTime,
            creationBlock,
            btcParams,
            Arrays.asList(new BtcECKey(), new BtcECKey()),
            10_000,
            new P2shErpRedeemScriptBuilder()
        );

        assertEquals(legacyFed.getRedeemScript(), p2shFed.getDefaultRedeemScript());
        Assertions.assertNotEquals(p2shFed.getRedeemScript(), p2shFed.getDefaultRedeemScript());
    }

    @Test
    void createdFederationInfo_withRealValues_equalsExistingFederationInfo_testnet() {
        // these values belong to a real federation
        BridgeConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();
        networkParameters = bridgeTestNetConstants.getBtcParams();
        emergencyKeys = bridgeTestNetConstants.getErpFedPubKeysList();
        activationDelayValue = bridgeTestNetConstants.getErpFedActivationDelay();

        defaultKeys = Arrays.stream(new String[]{
            "02099fd69cf6a350679a05593c3ff814bfaa281eb6dde505c953cf2875979b1209",
            "022a159227df514c7b7808ee182ae07d71770b67eda1e5ee668272761eefb2c24c",
            "0233bc8c1a994a921d7818f93e57a559373133ba531928843bf84c59c15e47eab0",
            "02937df9948c6f18359e473beeee0a19c27dd4f6d4114e5809aa862671bb765b5b",
            "02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da",
            "03db2ebad883823cefe8b2336c03b8d9c6afee4cbac77c7e935bc8c51ec20b2663",
            "03fb8e1d5d0392d35ca8c3656acb6193dbf392b3e89b9b7b86693f5c80f7ce8581",
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());
        String expectedProgram = "a914007c29a1d854639220aefca2587cdde07f381f4787";
        Address expectedAddress = Address.fromBase58(networkParameters, "2MsHnjFiAt5srgHJtwnwZTtZQPrKN8yiDqh");

        // this should create the real fed
        ErpFederation realP2shErpFederation = createDefaultP2shErpFederation();
        Script realP2shScript = realP2shErpFederation.getP2SHScript();
        Address realAddress = realP2shErpFederation.getAddress();

        assertEquals(expectedProgram, Hex.toHexString(realP2shScript.getProgram()));
        assertEquals(3, realP2shScript.getChunks().size());
        assertEquals(realAddress, realP2shScript.getToAddress(networkParameters));
        assertEquals(expectedAddress, realAddress);
    }

    @Test
    void createdFederationInfo_withRealValues_equalsExistingFederationInfo_mainnet() {
        // these values belong to a real federation
        BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
        networkParameters = bridgeMainNetConstants.getBtcParams();
        emergencyKeys = bridgeMainNetConstants.getErpFedPubKeysList();
        activationDelayValue = bridgeMainNetConstants.getErpFedActivationDelay();

        defaultKeys = Arrays.stream(new String[]{
            "020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c",
            "0275d473555de2733c47125f9702b0f870df1d817379f5587f09b6c40ed2c6c949",
            "025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db",
            "026b472f7d59d201ff1f540f111b6eb329e071c30a9d23e3d2bcd128fe73dc254c",
            "03250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf93",
            "0357f7ed4c118e581f49cd3b4d9dd1edb4295f4def49d6dcf2faaaaac87a1a0a42",
            "03ae72827d25030818c4947a800187b1fbcc33ae751e248ae60094cc989fb880f6",
            "03e05bf6002b62651378b1954820539c36ca405cbb778c225395dd9ebff6780299",
            "03b58a5da144f5abab2e03e414ad044b732300de52fa25c672a7f7b35888771906"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());
        String expectedProgram = "a9142c1bab6ea51fdaf85c8366bd2b1502eaa69b6ae687";
        Address expectedAddress = Address.fromBase58(
            networkParameters,
            "35iEoWHfDfEXRQ5ZWM5F6eMsY2Uxrc64YK"
        );

        // this should create the real fed
        ErpFederation realP2shErpFederation = createDefaultP2shErpFederation();
        Script p2shScript = realP2shErpFederation.getP2SHScript();
        Address address = realP2shErpFederation.getAddress();

        assertEquals(expectedProgram, Hex.toHexString(p2shScript.getProgram()));
        assertEquals(3, p2shScript.getChunks().size());
        assertEquals(
            address,
            p2shScript.getToAddress(networkParameters)
        );
        assertEquals(expectedAddress, address);
    }

    @Test
    void getErpPubKeys_fromUncompressedPublicKeys_equals() {
        // Public keys used for creating federation, but uncompressed format now
        emergencyKeys = emergencyKeys
            .stream()
            .map(BtcECKey::decompress)
            .collect(Collectors.toList());

        // Recreate federation
        ErpFederation federationWithUncompressedKeys = createDefaultP2shErpFederation();
        assertEquals(emergencyKeys, federationWithUncompressedKeys.getErpPubKeys());
    }

    @Test
    void getErpRedeemScript_compareOtherImplementation_P2SHERPFederation() throws IOException {

        byte[] rawRedeemScripts;
        try {
            rawRedeemScripts = Files.readAllBytes(Paths.get("src/test/resources/redeemScripts_p2shERP.json"));
        } catch (IOException e) {
            System.out.println("redeemScripts_p2shERP.json file not found");
            throw(e);
        }

        RawGeneratedRedeemScript[] generatedScripts = new ObjectMapper().readValue(rawRedeemScripts, RawGeneratedRedeemScript[].class);
        for (RawGeneratedRedeemScript generatedScript : generatedScripts) {
            // Skip test cases with invalid redeem script that exceed the maximum size
            if (generatedScript.script.getProgram().length <= MAX_SCRIPT_ELEMENT_SIZE) {
                Federation erpFederation = new ErpFederation(
                    FederationTestUtils.getFederationMembersWithBtcKeys(generatedScript.mainFed),
                    ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                    1,
                    NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
                    generatedScript.emergencyFed,
                    generatedScript.timelock,
                    new P2shErpRedeemScriptBuilder()
                );

                Script rskjScript = erpFederation.getRedeemScript();
                Script alternativeScript = generatedScript.script;
                assertEquals(alternativeScript, rskjScript);
            }
        }
    }

    @ParameterizedTest()
    @MethodSource("spendFromP2shErpFedArgsProvider")
    void spendFromP2shErpFed(
        NetworkParameters networkParameters,
        long activationDelay,
        boolean signWithEmergencyMultisig) {

        List<BtcECKey> defaultKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7", "fed8", "fed9", "fed10"},
            true
        );

        List<BtcECKey> emergencyKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"erp1", "erp2", "erp3", "erp4"},
            true
        );

        ErpFederation p2shErpFed = new ErpFederation(
            FederationMember.getFederationMembersFromKeys(defaultKeys),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            networkParameters,
            emergencyKeys,
            activationDelay,
            new P2shErpRedeemScriptBuilder()
        );

        Coin value = Coin.valueOf(1_000_000);
        Coin fee = Coin.valueOf(10_000);
        BtcTransaction fundTx = new BtcTransaction(networkParameters);
        fundTx.addOutput(value, p2shErpFed.getAddress());

        Address destinationAddress = BitcoinTestUtils.createP2PKHAddress(
            networkParameters,
            "destination"
        );

        assertDoesNotThrow(() -> FederationTestUtils.spendFromErpFed(
            networkParameters,
            p2shErpFed,
            signWithEmergencyMultisig ? emergencyKeys : defaultKeys,
            fundTx.getHash(),
            0,
            destinationAddress,
            value.minus(fee),
            signWithEmergencyMultisig
        ));
    }

    private void validateP2shErpRedeemScript(
        Script redeemScript,
        List<BtcECKey> defaultKeys,
        List<BtcECKey> emergencyKeys,
        Long csvValue) {

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
        defaultKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        emergencyKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(csvValue);

        byte[] script = redeemScript.getProgram();
        Assertions.assertTrue(script.length > 0);

        int index = 0;

        // First byte should equal OP_NOTIF
        assertEquals(ScriptOpCodes.OP_NOTIF, script[index++]);

        // Next byte should equal M, from an M/N multisig
        int m = defaultKeys.size() / 2 + 1;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        // Assert public keys
        for (BtcECKey key: defaultKeys) {
            byte[] pubkey = key.getPubKey();
            assertEquals(pubkey.length, script[index++]);
            for (byte b : pubkey) {
                assertEquals(b, script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        int n = defaultKeys.size();
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
        m = emergencyKeys.size() / 2 + 1;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(m)), script[index++]);

        for (BtcECKey key: emergencyKeys) {
            byte[] pubkey = key.getPubKey();
            assertEquals(Integer.valueOf(pubkey.length).byteValue(), script[index++]);
            for (byte b : pubkey) {
                assertEquals(b, script[index++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        n = emergencyKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(n)), script[index++]);

        // Next byte should equal OP_CHECKMULTISIG
        assertEquals(Integer.valueOf(ScriptOpCodes.OP_CHECKMULTISIG).byteValue(), script[index++]);

        assertEquals(ScriptOpCodes.OP_ENDIF, script[index++]);
    }

    private static Stream<Arguments> spendFromP2shErpFedArgsProvider() {
        BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
        BridgeConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();

        return Stream.of(
            Arguments.of(
                bridgeMainNetConstants.getBtcParams(),
                bridgeMainNetConstants.getErpFedActivationDelay(),
                false
            ),
            Arguments.of(
                bridgeTestNetConstants.getBtcParams(),
                bridgeTestNetConstants.getErpFedActivationDelay(),
                false
            ),
            Arguments.of(
                bridgeMainNetConstants.getBtcParams(),
                bridgeMainNetConstants.getErpFedActivationDelay(),
                true
            ),
            Arguments.of(
                bridgeTestNetConstants.getBtcParams(),
                bridgeTestNetConstants.getErpFedActivationDelay(),
                true
            )
        );
    }

    private static Stream<Arguments> getRedeemScriptArgsProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance()),
            Arguments.of(BridgeTestNetConstants.getInstance())
        );
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