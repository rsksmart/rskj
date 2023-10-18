package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.*;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;

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

import co.rsk.rules.Standardness;
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

class P2shErpFederationTest {
    private P2shErpFederation federation;
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

        federation = createDefaultP2shErpFederation();
    }

    private P2shErpFederation createDefaultP2shErpFederation() {
        List<FederationMember> standardMembers = FederationTestUtils.getFederationMembersWithBtcKeys(standardKeys);
        Instant creationTime = ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant();
        long creationBlockNumber = 0L;

        return new P2shErpFederation(
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
    void createInvalidP2shErpFederation_nullErpKeys() {
        emergencyKeys = null;
        FederationCreationException exception = assertThrows(
            FederationCreationException.class, this::createDefaultP2shErpFederation
        );

        String expectedMessage = "Emergency keys are not provided";
        assertTrue(exception.getMessage().contentEquals(expectedMessage));
    }

    @Test
    void createInvalidP2shErpFederation_emptyErpKeys() {
        emergencyKeys = new ArrayList<>();
        FederationCreationException exception = assertThrows(
            FederationCreationException.class, this::createDefaultP2shErpFederation
        );

        String expectedMessage = "Emergency keys are not provided";
        assertTrue(exception.getMessage().contentEquals(expectedMessage));
    }

    @Test
    void createValidP2shErpFederation_oneErpKey() {
        emergencyKeys = Collections.singletonList(emergencyKeys.get(0));
        assertDoesNotThrow(this::createDefaultP2shErpFederation);
    }

    @Test
    void createInvalidP2shErpFederation_negativeCsvValue() {
        activationDelayValue = -100L;
        FederationCreationException exception = assertThrows(
            FederationCreationException.class, this::createDefaultP2shErpFederation
        );

        String expectedMessage = String.format(
            "Provided csv value %d must be larger than 0 and lower than %d",
            activationDelayValue,
            ErpFederationRedeemScriptParser.MAX_CSV_VALUE
        );
        assertTrue(exception.getMessage().contentEquals(expectedMessage));
    }

    @Test
    void createInvalidP2shErpFederation_zeroCsvValue()  {
        activationDelayValue = 0L;
        FederationCreationException exception = assertThrows(
            FederationCreationException.class, this::createDefaultP2shErpFederation
        );

        String expectedMessage = String.format(
            "Provided csv value %d must be larger than 0 and lower than %d",
            activationDelayValue,
            ErpFederationRedeemScriptParser.MAX_CSV_VALUE
        );
        assertTrue(exception.getMessage().contentEquals(expectedMessage));
    }

    @Test
    void createInvalidP2shErpFederation_aboveMaxCsvValue()  {
        activationDelayValue = ErpFederationRedeemScriptParser.MAX_CSV_VALUE + 1;
        FederationCreationException exception = assertThrows(
            FederationCreationException.class, this::createDefaultP2shErpFederation
        );

        String expectedMessage = String.format(
            "Provided csv value %d must be larger than 0 and lower than %d",
            activationDelayValue,
            ErpFederationRedeemScriptParser.MAX_CSV_VALUE
        );
        assertTrue(exception.getMessage().contentEquals(expectedMessage));
    }

    @Test
    void createValidP2shErpFederation_exactMaxCsvValue()  {
        activationDelayValue = ErpFederationRedeemScriptParser.MAX_CSV_VALUE;
        assertDoesNotThrow(this::createDefaultP2shErpFederation);
    }

    @Test
    void createInvalidFederation_aboveMaxScriptSigSize() {
        // add one member to exceed redeem script size limit
        List<BtcECKey> newStandardKeys = federation.getBtcPublicKeys();
        BtcECKey federator10PublicKey = BtcECKey.fromPublicOnly(
            Hex.decode("02550cc87fa9061162b1dd395a16662529c9d8094c0feca17905a3244713d65fe8")
        );
        newStandardKeys.add(federator10PublicKey);
        standardKeys = newStandardKeys;
        FederationCreationException exception = assertThrows(
            FederationCreationException.class, this::createDefaultP2shErpFederation
        );

        String expectedMessage = "Unable to create P2shErpFederation. The redeem script size is 525, that is above the maximum allowed.";
        assertTrue(exception.getMessage().contentEquals(expectedMessage));
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
        ErpFederation otherFederation = new P2shErpFederation(
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
    void testEquals_differentNumberOfMembers() {
        // remove federator9
        List<BtcECKey> newStandardKeys = federation.getBtcPublicKeys();
        newStandardKeys.remove(9);
        standardKeys = newStandardKeys;

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
        List<BtcECKey> newStandardKeys = federation.getBtcPublicKeys();
        newStandardKeys.remove(8);
        newStandardKeys.add(federator9PublicKey);
        standardKeys = newStandardKeys;

        ErpFederation otherFederation = createDefaultP2shErpFederation();
        Assertions.assertNotEquals(federation, otherFederation);
    }

    @ParameterizedTest
    @MethodSource("getRedeemScriptArgsProvider")
    void getRedeemScript(BridgeConstants bridgeConstants) {
        if (!(bridgeConstants == BridgeMainNetConstants.getInstance())) {
            // should add this case because adding erp to mainnet genesis federation
            // throws a validation error, so in that case we use the one set up before each test.
            // if using testnet constants, we can add them with no errors
            standardKeys = bridgeConstants.getGenesisFederation().getBtcPublicKeys();
        }

        emergencyKeys = bridgeConstants.getErpFedPubKeysList();
        activationDelayValue = bridgeConstants.getErpFedActivationDelay();

        ErpFederation p2shErpFederation = createDefaultP2shErpFederation();
        validateP2shErpRedeemScript(
            p2shErpFederation.getRedeemScript(),
            standardKeys,
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

        P2shErpFederation p2shFed = new P2shErpFederation(
                members,
                creationTime,
                creationBlock,
                btcParams,
                Arrays.asList(new BtcECKey(), new BtcECKey()),
                10_000,
                activations
        );

        assertEquals(legacyFed.getRedeemScript(), p2shFed.getStandardRedeemScript());
        Assertions.assertNotEquals(p2shFed.getRedeemScript(), p2shFed.getStandardRedeemScript());
    }

    @Test
    void getPowPegAddressAndP2shScript_testnet() {
        BridgeConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();
        networkParameters = bridgeTestNetConstants.getBtcParams();
        emergencyKeys = bridgeTestNetConstants.getErpFedPubKeysList();
        activationDelayValue = bridgeTestNetConstants.getErpFedActivationDelay();

        standardKeys = Arrays.stream(new String[]{
            "02099fd69cf6a350679a05593c3ff814bfaa281eb6dde505c953cf2875979b1209",
            "022a159227df514c7b7808ee182ae07d71770b67eda1e5ee668272761eefb2c24c",
            "0233bc8c1a994a921d7818f93e57a559373133ba531928843bf84c59c15e47eab0",
            "02937df9948c6f18359e473beeee0a19c27dd4f6d4114e5809aa862671bb765b5b",
            "02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da",
            "03db2ebad883823cefe8b2336c03b8d9c6afee4cbac77c7e935bc8c51ec20b2663",
            "03fb8e1d5d0392d35ca8c3656acb6193dbf392b3e89b9b7b86693f5c80f7ce8581",
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        ErpFederation realP2shErpFederation = createDefaultP2shErpFederation();
        Script p2shScript = realP2shErpFederation.getP2SHScript();
        Address address = realP2shErpFederation.getAddress();

        String expectedProgram = "a914007c29a1d854639220aefca2587cdde07f381f4787";
        Address expectedAddress = Address.fromBase58(
            networkParameters,
            "2MsHnjFiAt5srgHJtwnwZTtZQPrKN8yiDqh"
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
    void getPowPegAddressAndP2shScript_mainnet() {
        BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
        networkParameters = bridgeMainNetConstants.getBtcParams();
        emergencyKeys = bridgeMainNetConstants.getErpFedPubKeysList();
        activationDelayValue = bridgeMainNetConstants.getErpFedActivationDelay();

        standardKeys = Arrays.stream(new String[]{
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

        ErpFederation realP2shErpFederation = createDefaultP2shErpFederation();
        Script p2shScript = realP2shErpFederation.getP2SHScript();
        Address address = realP2shErpFederation.getAddress();

        String expectedProgram = "a9142c1bab6ea51fdaf85c8366bd2b1502eaa69b6ae687";
        Address expectedAddress = Address.fromBase58(
            networkParameters,
            "35iEoWHfDfEXRQ5ZWM5F6eMsY2Uxrc64YK"
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
            // Skip test cases where the redeem script exceeds the maximum size
            if (generatedScript.script.getProgram().length <= Standardness.MAX_SCRIPT_ELEMENT_SIZE) {
                Federation erpFederation = new P2shErpFederation(
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

    @ParameterizedTest()
    @MethodSource("spendFromP2shErpFedArgsProvider")
    void spendFromP2shErpFed(
        NetworkParameters networkParameters,
        long activationDelay,
        boolean signWithEmergencyMultisig) {

        List<BtcECKey> standardKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7", "fed8", "fed9", "fed10"},
            true
        );

        List<BtcECKey> emergencyKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"erp1", "erp2", "erp3", "erp4"},
            true
        );

        P2shErpFederation p2shErpFed = new P2shErpFederation(
            FederationMember.getFederationMembersFromKeys(standardKeys),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            networkParameters,
            emergencyKeys,
            activationDelay,
            mock(ActivationConfig.ForBlock.class)
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
            signWithEmergencyMultisig ? emergencyKeys : standardKeys,
            fundTx.getHash(),
            0,
            destinationAddress,
            value.minus(fee),
            signWithEmergencyMultisig
        ));
    }

    private void validateP2shErpRedeemScript(
        Script erpRedeemScript,
        List<BtcECKey> defaultMultisigKeys,
        List<BtcECKey> emergencyMultisigKeys,
        Long csvValue) {

        // Keys are sorted when added to the redeem script, so we need them sorted in order to validate
        defaultMultisigKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        emergencyMultisigKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(csvValue);

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
