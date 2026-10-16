package co.rsk.peg.federation.constants;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.federation.FederationFormatVersion;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FederationConstantsTest {

    private static final FederationConstants MAINNET = FederationMainNetConstants.getInstance();
    private static final FederationConstants TESTNET = FederationTestNetConstants.getInstance();
    private static final FederationConstants TESTNET2 = FederationTestNet2Constants.getInstance();
    private static final FederationConstants REGTEST = new BridgeRegTestConstants().getFederationConstants();

    @ParameterizedTest
    @MethodSource("networkArgs")
    void getBtcParams(FederationConstants constants, NetworkParameters expectedNetwork) {
        assertEquals(expectedNetwork, constants.getBtcParams());
    }

    private static Stream<Arguments> networkArgs() {
        return Stream.of(
            Arguments.of(MAINNET, NetworkParameters.fromID(NetworkParameters.ID_MAINNET)),
            Arguments.of(TESTNET, NetworkParameters.fromID(NetworkParameters.ID_TESTNET)),
            Arguments.of(TESTNET2, NetworkParameters.fromID(NetworkParameters.ID_TESTNET4)),
            Arguments.of(REGTEST, NetworkParameters.fromID(NetworkParameters.ID_REGTEST))
        );
    }

    @ParameterizedTest
    @MethodSource("genesisFedPublicKeysArgs")
    void getGenesisFederationPublicKeys(FederationConstants constants, List<BtcECKey> expectedPublicKeys) {
        assertEquals(expectedPublicKeys, constants.getGenesisFederationPublicKeys());
    }

    private static Stream<Arguments> genesisFedPublicKeysArgs() {
        List<BtcECKey> genesisFederationPublicKeysMainnet = Stream.of(
            "03b53899c390573471ba30e5054f78376c5f797fda26dde7a760789f02908cbad2",
            "027319afb15481dbeb3c426bcc37f9a30e7f51ceff586936d85548d9395bcc2344",
            "0355a2e9bf100c00fc0a214afd1bf272647c7824eb9cb055480962f0c382596a70",
            "02566d5ded7c7db1aa7ee4ef6f76989fb42527fcfdcddcd447d6793b7d869e46f7",
            "0294c817150f78607566e961b3c71df53a22022a80acbb982f83c0c8baac040adc",
            "0372cd46831f3b6afd4c044d160b7667e8ebf659d6cb51a825a3104df6ee0638c6",
            "0340df69f28d69eef60845da7d81ff60a9060d4da35c767f017b0dd4e20448fb44",
            "02ac1901b6fba2c1dbd47d894d2bd76c8ba1d296d65f6ab47f1c6b22afb53e73eb",
            "031aabbeb9b27258f98c2bf21f36677ae7bae09eb2d8c958ef41a20a6e88626d26",
            "0245ef34f5ee218005c9c21227133e8568a4f3f11aeab919c66ff7b816ae1ffeea",
            "02550cc87fa9061162b1dd395a16662529c9d8094c0feca17905a3244713d65fe8",
            "02481f02b7140acbf3fcdd9f72cf9a7d9484d8125e6df7c9451cfa55ba3b077265",
            "03f909ae15558c70cc751aff9b1f495199c325b13a9e5b934fd6299cd30ec50be8",
            "02c6018fcbd3e89f3cf9c7f48b3232ea3638eb8bf217e59ee290f5f0cfb2fb9259",
            "03b65694ccccda83cbb1e56b31308acd08e993114c33f66a456b627c2c1c68bed6"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();

        List<BtcECKey> genesisFederationPublicKeysTestnet = Stream.of(
            "039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9",
            "02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da",
            "0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09",
            "034844a99cd7028aa319476674cc381df006628be71bc5593b8b5fdb32bb42ef85"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();

        List<BtcECKey> genesisFederationPublicKeysTestnet2 = Stream.of(
            "02337f4a3d81fb621ec72e0f62336e3fb9b181063ad86674ea7f80d3ae687acf73",
            "02f9c08784952982e5cc24624f34d5afd1584ed32851b0790f2bba965e7a131b0f",
            "030e6370c451967cef1ae35e3a04d2cdfc337d94cc15ce1afa16130ca677294a9b"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();

        List<BtcECKey> genesisFederationPublicKeysRegtest = Stream.of(
            "0362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a124",
            "03c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db",
            "02cd53fc53a07f211641a677d250f6de99caf620e8e77071e811a28b3bcddf0be1"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();

        return Stream.of(
            Arguments.of(MAINNET, genesisFederationPublicKeysMainnet),
            Arguments.of(TESTNET, genesisFederationPublicKeysTestnet),
            Arguments.of(TESTNET2, genesisFederationPublicKeysTestnet2),
            Arguments.of(REGTEST, genesisFederationPublicKeysRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("genesisFedCreationTimeArgs")
    void getGenesisFedCreationTime(FederationConstants constants, Instant expectedCreationTime) {
        assertEquals(expectedCreationTime, constants.getGenesisFederationCreationTime());
    }

    private static Stream<Arguments> genesisFedCreationTimeArgs() {
        Instant genesisFedCreationTimeMainnet = ZonedDateTime.parse("1970-01-18T12:49:08.400Z").toInstant();
        Instant genesisFedCreationTimeTestnet = ZonedDateTime.parse("1970-01-18T19:29:27.600Z").toInstant();
        Instant genesisFedCreationTimeTestnet2 = ZonedDateTime.parse("2026-07-20T00:00:00Z").toInstant();
        Instant genesisFedCreationTimeRegtest = ZonedDateTime.parse("2016-01-01T00:00:00Z").toInstant();

        return Stream.of(
            Arguments.of(MAINNET, genesisFedCreationTimeMainnet),
            Arguments.of(TESTNET, genesisFedCreationTimeTestnet),
            Arguments.of(TESTNET2, genesisFedCreationTimeTestnet2),
            Arguments.of(REGTEST, genesisFedCreationTimeRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("genesisFederationTypeArgs")
    void getGenesisFederationType(FederationConstants constants, FederationFormatVersion expectedType) {
        assertEquals(expectedType, constants.getGenesisFederationType());
    }

    private static Stream<Arguments> genesisFederationTypeArgs() {
        return Stream.of(
            Arguments.of(MAINNET, FederationFormatVersion.STANDARD_MULTISIG_FEDERATION),
            Arguments.of(TESTNET, FederationFormatVersion.STANDARD_MULTISIG_FEDERATION),
            Arguments.of(TESTNET2, FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION),
            Arguments.of(REGTEST, FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION)
        );
    }

    @ParameterizedTest
    @MethodSource("validationPeriodDurationArgs")
    void getValidationPeriodDuration(FederationConstants constants, long expectedDuration) {
        assertEquals(expectedDuration, constants.getValidationPeriodDurationInBlocks());
    }

    private static Stream<Arguments> validationPeriodDurationArgs() {
        return Stream.of(
            Arguments.of(MAINNET, 20000L),
            Arguments.of(TESTNET, 2000L),
            Arguments.of(TESTNET2, 2000L),
            Arguments.of(REGTEST, 125L)
        );
    }

    @ParameterizedTest
    @MethodSource("fedActivationAgeAndActivationArgs")
    void getFederationActivationAge(FederationConstants constants, ActivationConfig.ForBlock activations, long expectedActivationAge) {
        assertEquals(expectedActivationAge, constants.getFederationActivationAge(activations));
    }

    private static Stream<Arguments> fedActivationAgeAndActivationArgs() {
        long fedActivationAgeLegacyMainnet = 18500L;
        long fedActivationAgeLegacyTestnet = 60L;
        long fedActivationAgeLegacyRegtest = 10L;
        // testnet2 activates RSKIP383 at genesis, so it never sets a legacy activation age
        long fedActivationAgeLegacyTestnet2 = 0L;
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop401().forBlock(0L);

        long fedActivationAgeMainnet = 40320L;
        long fedActivationAgeTestnet = 120L;
        long fedActivationAgeRegtest = 150L;
        long fedActivationAgeTestnet2 = 2400L;
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0L);

        long fedActivationAgeTestnetPostLovell = 2400L;
        ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0L);

        return Stream.of(
            Arguments.of(MAINNET, hopActivations, fedActivationAgeLegacyMainnet),
            Arguments.of(TESTNET, hopActivations, fedActivationAgeLegacyTestnet),
            Arguments.of(TESTNET2, hopActivations, fedActivationAgeLegacyTestnet2),
            Arguments.of(REGTEST, hopActivations, fedActivationAgeLegacyRegtest),
            Arguments.of(MAINNET, fingerrootActivations, fedActivationAgeMainnet),
            Arguments.of(TESTNET, fingerrootActivations, fedActivationAgeTestnet),
            Arguments.of(TESTNET2, fingerrootActivations, fedActivationAgeTestnet2),
            Arguments.of(REGTEST, fingerrootActivations, fedActivationAgeRegtest),
            Arguments.of(MAINNET, allActivations, fedActivationAgeMainnet),
            Arguments.of(TESTNET, allActivations, fedActivationAgeTestnetPostLovell),
            Arguments.of(TESTNET2, allActivations, fedActivationAgeTestnet2),
            Arguments.of(REGTEST, allActivations, fedActivationAgeRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("fundsMigrationAgeSinceActivationBeginArgs")
    void getFundsMigrationAgeSinceActivationBegin(FederationConstants constants, long expectedFundsMigrationAge) {
        assertEquals(expectedFundsMigrationAge, constants.getFundsMigrationAgeSinceActivationBegin());
    }

    private static Stream<Arguments> fundsMigrationAgeSinceActivationBeginArgs() {
        long fundsMigrationAgeSinceActivationBeginMainnet = 0L;
        long fundsMigrationAgeSinceActivationBeginTestnet = 60L;
        long fundsMigrationAgeSinceActivationBeginTestnet2 = 60L;
        long fundsMigrationAgeSinceActivationBeginRegtest = 15L;

        return Stream.of(
            Arguments.of(MAINNET, fundsMigrationAgeSinceActivationBeginMainnet),
            Arguments.of(TESTNET, fundsMigrationAgeSinceActivationBeginTestnet),
            Arguments.of(TESTNET2, fundsMigrationAgeSinceActivationBeginTestnet2),
            Arguments.of(REGTEST, fundsMigrationAgeSinceActivationBeginRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("fundsMigrationAgeSinceActivationEndAndActivationsArgs")
    void getFundsMigrationAgeSinceActivationEnd(FederationConstants constants, ActivationConfig.ForBlock activations, long expectedActivationAge) {
        assertEquals(expectedActivationAge, constants.getFundsMigrationAgeSinceActivationEnd(activations));
    }

    private static Stream<Arguments> fundsMigrationAgeSinceActivationEndAndActivationsArgs() {
        long fundsMigrationAgeSinceActivationEndMainnet = 10585L;
        long fundsMigrationAgeSinceActivationEndTestnet = 900L;
        long fundsMigrationAgeSinceActivationEndTestnet2 = 900L;
        long fundsMigrationAgeSinceActivationEndRegtest = 150L;
        
        // special case is just different for mainnet,
        // between RSKIPs 357 and 374
        long specialCaseFundsMigrationAgeSinceActivationEndMainnet = 172_800L;
        // testnet2 activates RSKIPs 357 and 374 at genesis, so it never sets a special case value
        long specialCaseFundsMigrationAgeSinceActivationEndTestnet2 = 0L;
        ActivationConfig.ForBlock activationsSpecialCase = mock(ActivationConfig.ForBlock.class);
        when(activationsSpecialCase.isActive(ConsensusRule.RSKIP357)).thenReturn(true);
        when(activationsSpecialCase.isActive(ConsensusRule.RSKIP374)).thenReturn(false);

        // before RSKIP 357 or after 374, value is the same for all networks
        ActivationConfig.ForBlock activationsPreRSKIP357 = mock(ActivationConfig.ForBlock.class);
        when(activationsPreRSKIP357.isActive(ConsensusRule.RSKIP357)).thenReturn(false);
        ActivationConfig.ForBlock activationsPostRSKIP374 = mock(ActivationConfig.ForBlock.class);
        when(activationsPostRSKIP374.isActive(ConsensusRule.RSKIP374)).thenReturn(true);

        return Stream.of(
            Arguments.of(MAINNET, activationsPreRSKIP357, fundsMigrationAgeSinceActivationEndMainnet),
            Arguments.of(TESTNET, activationsPreRSKIP357, fundsMigrationAgeSinceActivationEndTestnet),
            Arguments.of(TESTNET2, activationsPreRSKIP357, fundsMigrationAgeSinceActivationEndTestnet2),
            Arguments.of(REGTEST, activationsPreRSKIP357, fundsMigrationAgeSinceActivationEndRegtest),
            Arguments.of(MAINNET, activationsSpecialCase, specialCaseFundsMigrationAgeSinceActivationEndMainnet),
            Arguments.of(TESTNET, activationsSpecialCase, fundsMigrationAgeSinceActivationEndTestnet),
            Arguments.of(TESTNET2, activationsSpecialCase, specialCaseFundsMigrationAgeSinceActivationEndTestnet2),
            Arguments.of(REGTEST, activationsSpecialCase, fundsMigrationAgeSinceActivationEndRegtest),
            Arguments.of(MAINNET, activationsPostRSKIP374, fundsMigrationAgeSinceActivationEndMainnet),
            Arguments.of(TESTNET, activationsPostRSKIP374, fundsMigrationAgeSinceActivationEndTestnet),
            Arguments.of(TESTNET2, activationsPostRSKIP374, fundsMigrationAgeSinceActivationEndTestnet2),
            Arguments.of(REGTEST, activationsPostRSKIP374, fundsMigrationAgeSinceActivationEndRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("fedChangeAuthorizerArgs")
    void getFederationChangeAuthorizer(FederationConstants constants, Set<RskAddress> expectedAddresses) {
        AddressBasedAuthorizer expectedAuthorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(expectedAddresses);

        assertThat(expectedAuthorizer, samePropertyValuesAs(constants.getFederationChangeAuthorizer()));
    }

    private static Stream<Arguments> fedChangeAuthorizerArgs() {
        Set<RskAddress> fedChangeAuthorizedAddressesMainnet =  Stream.of(
            "56bc5087ac97bc85a877bd20dfef910b78b1dc5a",
            "6fe1cd06a80fa52eb34d7e4d1845881c2b5de89a",
            "d41f4d4c7fc8d5fa9468d0c466c9a726397f91b6"
        ).map(RskAddress::new).collect(Collectors.toSet());

        Set<RskAddress> fedChangeAuthorizedAddressesTestnet =  Stream.of(
            "112140a53537b49788456daffda2e20efd1865cd",
            "5990aaedfccc88e2f7321a5b8d8db1f6e64abf03",
            "5526529f386c4c77044f1c29bb965b37a7767c3d"
        ).map(RskAddress::new).collect(Collectors.toSet());

        Set<RskAddress> fedChangeAuthorizedAddressesTestnet2 =  Stream.of(
            "112140a53537b49788456daffda2e20efd1865cd",
            "5990aaedfccc88e2f7321a5b8d8db1f6e64abf03",
            "5526529f386c4c77044f1c29bb965b37a7767c3d"
        ).map(RskAddress::new).collect(Collectors.toSet());

        Set<RskAddress> fedChangeAuthorizedAddressesRegtest = Stream.of(
            "0d8c44bd578a4e7148d1758b3332b529e93e48c1",
            "566bca18df14ed75efcc1a8a4f3947e81132dbbc",
            "5252403bca6ac3c104abdd320a736fa38282045d"
        ).map(RskAddress::new).collect(Collectors.toSet());

        return Stream.of(
            Arguments.of(MAINNET, fedChangeAuthorizedAddressesMainnet),
            Arguments.of(TESTNET, fedChangeAuthorizedAddressesTestnet),
            Arguments.of(TESTNET2, fedChangeAuthorizedAddressesTestnet2),
            Arguments.of(REGTEST, fedChangeAuthorizedAddressesRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("erpActivationDelayArgs")
    void getErpFedActivationDelay(FederationConstants constants, long expectedActivationDelay) {
        assertEquals(expectedActivationDelay, constants.getErpFedActivationDelay());
    }

    private static Stream<Arguments> erpActivationDelayArgs() {
        long erpActivationDelayMainnet = 52_560;
        long erpActivationDelayTestnet = 52_560;
        long erpActivationDelayTestnet2 = 52_560;
        long erpActivationDelayRegtest = 500;

        return Stream.of(
            Arguments.of(MAINNET, erpActivationDelayMainnet),
            Arguments.of(TESTNET, erpActivationDelayTestnet),
            Arguments.of(TESTNET2, erpActivationDelayTestnet2),
            Arguments.of(REGTEST, erpActivationDelayRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("erpFedPubKeysArgs")
    void getErpFedPubKeysList(FederationConstants constants, List<BtcECKey> expectedKeys) {
        assertEquals(expectedKeys, constants.getErpFedPubKeysList());
    }

    private static Stream<Arguments> erpFedPubKeysArgs() {
        List<BtcECKey> erpFedPubKeysMainnet = Stream.of(
            "0257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d4",
            "03c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f9",
            "03cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b3",
            "02370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();

        List<BtcECKey> erpFedPubKeysTestnet = Stream.of(
            "0216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3",
            "034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f",
            "0275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f14"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();

        List<BtcECKey> erpFedPubKeysTestnet2 = Stream.of(
            "0216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3",
            "034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f",
            "0275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f14"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();

        List<BtcECKey> erpFedPubKeysRegtest = Stream.of(
            "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
            "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
            "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
            "03ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58513312550ea1584bc"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();

        return Stream.of(
            Arguments.of(MAINNET, erpFedPubKeysMainnet),
            Arguments.of(TESTNET, erpFedPubKeysTestnet),
            Arguments.of(TESTNET2, erpFedPubKeysTestnet2),
            Arguments.of(REGTEST, erpFedPubKeysRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("oldFederationAddressArgs")
    void getOldFederationAddress(FederationConstants constants, String expectedAddress) {
        assertEquals(expectedAddress, constants.getOldFederationAddress());
    }

    private static Stream<Arguments> oldFederationAddressArgs() {
        String oldFederationAddressMainnet = "35JUi1FxabGdhygLhnNUEFG4AgvpNMgxK1";
        String oldFederationAddressTestnet = "2N7ZgQyhFKm17RbaLqygYbS7KLrQfapyZzu";
        String oldFederationAddressRegtest = "2N7ZgQyhFKm17RbaLqygYbS7KLrQfapyZzu";
        // testnet2 has no federation that predates the genesis one
        String oldFederationAddressTestnet2 = null;

        return Stream.of(
            Arguments.of(MAINNET, oldFederationAddressMainnet),
            Arguments.of(TESTNET, oldFederationAddressTestnet),
            Arguments.of(TESTNET2, oldFederationAddressTestnet2),
            Arguments.of(REGTEST, oldFederationAddressRegtest)
        );
    }
}
