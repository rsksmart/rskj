package co.rsk.peg.federation.constants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.constants.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FederationConstantsTest {
    private static final FederationConstants mainnet = FederationMainNetConstants.getInstance();
    private static final FederationConstants testnet = FederationTestNetConstants.getInstance();
    private static final FederationConstants regtest = new BridgeRegTestConstants().getFederationConstants();

    @ParameterizedTest
    @MethodSource("networkArgs")
    void getBtcParams(FederationConstants constants, NetworkParameters expectedNetwork) {
        assertEquals(expectedNetwork, constants.getBtcParams());
    }

    private static Stream<Arguments> networkArgs() {
        return Stream.of(
            Arguments.of(mainnet, NetworkParameters.fromID(NetworkParameters.ID_MAINNET)),
            Arguments.of(testnet, NetworkParameters.fromID(NetworkParameters.ID_TESTNET)),
            Arguments.of(regtest, NetworkParameters.fromID(NetworkParameters.ID_REGTEST))
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
            ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        List<BtcECKey> genesisFederationPublicKeysTestnet = Stream.of(
            "039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb9",
            "02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da",
            "0344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a09",
            "034844a99cd7028aa319476674cc381df006628be71bc5593b8b5fdb32bb42ef85"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        List<BtcECKey> genesisFederationPublicKeysRegtest = Stream.of(
            "0362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a124",
            "03c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db",
            "02cd53fc53a07f211641a677d250f6de99caf620e8e77071e811a28b3bcddf0be1"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        return Stream.of(
            Arguments.of(mainnet, genesisFederationPublicKeysMainnet),
            Arguments.of(testnet, genesisFederationPublicKeysTestnet),
            Arguments.of(regtest, genesisFederationPublicKeysRegtest)
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
        Instant genesisFedCreationTimeRegtest = ZonedDateTime.parse("2016-01-01T00:00:00Z").toInstant();

        return Stream.of(
            Arguments.of(mainnet, genesisFedCreationTimeMainnet),
            Arguments.of(testnet, genesisFedCreationTimeTestnet),
            Arguments.of(regtest, genesisFedCreationTimeRegtest)
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
        ActivationConfig.ForBlock activationsPreRSKIP383 = mock(ActivationConfig.ForBlock.class);
        when(activationsPreRSKIP383.isActive(ConsensusRule.RSKIP383)).thenReturn(false);

        long fedActivationAgeMainnet = 40320L;
        long fedActivationAgeTestnet = 120L;
        long fedActivationAgeRegtest = 20L;
        ActivationConfig.ForBlock activationsPostRSKIP383 = mock(ActivationConfig.ForBlock.class);
        when(activationsPostRSKIP383.isActive(ConsensusRule.RSKIP383)).thenReturn(true);

        return Stream.of(
            Arguments.of(mainnet, activationsPreRSKIP383, fedActivationAgeLegacyMainnet),
            Arguments.of(testnet, activationsPreRSKIP383, fedActivationAgeLegacyTestnet),
            Arguments.of(regtest, activationsPreRSKIP383, fedActivationAgeLegacyRegtest),
            Arguments.of(mainnet, activationsPostRSKIP383, fedActivationAgeMainnet),
            Arguments.of(testnet, activationsPostRSKIP383, fedActivationAgeTestnet),
            Arguments.of(regtest, activationsPostRSKIP383, fedActivationAgeRegtest)
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
        long fundsMigrationAgeSinceActivationBeginRegtest = 15L;

        return Stream.of(
            Arguments.of(mainnet, fundsMigrationAgeSinceActivationBeginMainnet),
            Arguments.of(testnet, fundsMigrationAgeSinceActivationBeginTestnet),
            Arguments.of(regtest, fundsMigrationAgeSinceActivationBeginRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("fundsMigrationAgeSinceActivationEndAndActivationsArgs")
    void getFundsMigrationAgeSinceActivationEnd(FederationConstants federationConstants, ActivationConfig.ForBlock activations, long expectedActivationAge) {
        assertEquals(expectedActivationAge, federationConstants.getFundsMigrationAgeSinceActivationEnd(activations));
    }

    private static Stream<Arguments> fundsMigrationAgeSinceActivationEndAndActivationsArgs() {
        // special case is just different for mainnet,
        // between RSKIPS 357 and 374
        long specialCaseFundsMigrationAgeSinceActivationEndMainnet = 172_800L;
        long generalCaseFundsMigrationAgeSinceActivationEndMainnet = 10585L;
        long fundsMigrationAgeSinceActivationEndTestnet = 900L;
        long fundsMigrationAgeSinceActivationEndRegtest = 150L;
        ActivationConfig.ForBlock activationsForSpecialCase = mock(ActivationConfig.ForBlock.class);
        when(activationsForSpecialCase.isActive(ConsensusRule.RSKIP357)).thenReturn(true);
        when(activationsForSpecialCase.isActive(ConsensusRule.RSKIP374)).thenReturn(false);

        // before RSKIP 357 or after 374, value is the same for all networks
        ActivationConfig.ForBlock activationsForGeneralCase1 = mock(ActivationConfig.ForBlock.class);
        when(activationsForGeneralCase1.isActive(ConsensusRule.RSKIP357)).thenReturn(false);
        ActivationConfig.ForBlock activationsForGeneralCase2 = mock(ActivationConfig.ForBlock.class);
        when(activationsForGeneralCase2.isActive(ConsensusRule.RSKIP374)).thenReturn(true);

        return Stream.of(
            Arguments.of(mainnet, activationsForGeneralCase1, generalCaseFundsMigrationAgeSinceActivationEndMainnet),
            Arguments.of(testnet, activationsForGeneralCase1, fundsMigrationAgeSinceActivationEndTestnet),
            Arguments.of(regtest, activationsForGeneralCase1, fundsMigrationAgeSinceActivationEndRegtest),
            Arguments.of(mainnet, activationsForSpecialCase, specialCaseFundsMigrationAgeSinceActivationEndMainnet),
            Arguments.of(testnet, activationsForSpecialCase, fundsMigrationAgeSinceActivationEndTestnet),
            Arguments.of(regtest, activationsForSpecialCase, fundsMigrationAgeSinceActivationEndRegtest),
            Arguments.of(mainnet, activationsForGeneralCase2, generalCaseFundsMigrationAgeSinceActivationEndMainnet),
            Arguments.of(testnet, activationsForGeneralCase2, fundsMigrationAgeSinceActivationEndTestnet),
            Arguments.of(regtest, activationsForGeneralCase2, fundsMigrationAgeSinceActivationEndRegtest)
        );
    }
}
