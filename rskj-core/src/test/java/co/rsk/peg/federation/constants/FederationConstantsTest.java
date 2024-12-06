package co.rsk.peg.federation.constants;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.constants.*;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FederationConstantsTest {

    private static final FederationConstants MAINNET = FederationMainNetConstants.getInstance();
    private static final FederationConstants TESTNET = FederationTestNetConstants.getInstance();
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
            Arguments.of(MAINNET, genesisFederationPublicKeysMainnet),
            Arguments.of(TESTNET, genesisFederationPublicKeysTestnet),
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
        Instant genesisFedCreationTimeRegtest = ZonedDateTime.parse("2016-01-01T00:00:00Z").toInstant();

        return Stream.of(
            Arguments.of(MAINNET, genesisFedCreationTimeMainnet),
            Arguments.of(TESTNET, genesisFedCreationTimeTestnet),
            Arguments.of(REGTEST, genesisFedCreationTimeRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("validationPeriodDurationArgs")
    void getValidationPeriodDuration(FederationConstants constants, long expectedDuration) {
        assertEquals(expectedDuration, constants.getValidationPeriodDurationInBlocks());
    }

    private static Stream<Arguments> validationPeriodDurationArgs() {
        return Stream.of(
            Arguments.of(MAINNET, 16000L),
            Arguments.of(TESTNET, 80L),
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
        ActivationConfig.ForBlock activationsPreRSKIP383 = mock(ActivationConfig.ForBlock.class);
        when(activationsPreRSKIP383.isActive(ConsensusRule.RSKIP383)).thenReturn(false);

        long fedActivationAgeMainnet = 40320L;
        long fedActivationAgeTestnet = 120L;
        long fedActivationAgeRegtest = 150L;
        ActivationConfig.ForBlock activationsPostRSKIP383 = mock(ActivationConfig.ForBlock.class);
        when(activationsPostRSKIP383.isActive(ConsensusRule.RSKIP383)).thenReturn(true);

        return Stream.of(
            Arguments.of(MAINNET, activationsPreRSKIP383, fedActivationAgeLegacyMainnet),
            Arguments.of(TESTNET, activationsPreRSKIP383, fedActivationAgeLegacyTestnet),
            Arguments.of(REGTEST, activationsPreRSKIP383, fedActivationAgeLegacyRegtest),
            Arguments.of(MAINNET, activationsPostRSKIP383, fedActivationAgeMainnet),
            Arguments.of(TESTNET, activationsPostRSKIP383, fedActivationAgeTestnet),
            Arguments.of(REGTEST, activationsPostRSKIP383, fedActivationAgeRegtest)
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
            Arguments.of(MAINNET, fundsMigrationAgeSinceActivationBeginMainnet),
            Arguments.of(TESTNET, fundsMigrationAgeSinceActivationBeginTestnet),
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
        long fundsMigrationAgeSinceActivationEndRegtest = 150L;
        
        // special case is just different for mainnet,
        // between RSKIPs 357 and 374
        long specialCaseFundsMigrationAgeSinceActivationEndMainnet = 172_800L;
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
            Arguments.of(REGTEST, activationsPreRSKIP357, fundsMigrationAgeSinceActivationEndRegtest),
            Arguments.of(MAINNET, activationsSpecialCase, specialCaseFundsMigrationAgeSinceActivationEndMainnet),
            Arguments.of(TESTNET, activationsSpecialCase, fundsMigrationAgeSinceActivationEndTestnet),
            Arguments.of(REGTEST, activationsSpecialCase, fundsMigrationAgeSinceActivationEndRegtest),
            Arguments.of(MAINNET, activationsPostRSKIP374, fundsMigrationAgeSinceActivationEndMainnet),
            Arguments.of(TESTNET, activationsPostRSKIP374, fundsMigrationAgeSinceActivationEndTestnet),
            Arguments.of(REGTEST, activationsPostRSKIP374, fundsMigrationAgeSinceActivationEndRegtest)
        );
    }

    @ParameterizedTest
    @MethodSource("fedChangeAuthorizerArgs")
    void getFederationChangeAuthorizer(FederationConstants constants, List<ECKey> expectedKeys) {
        AddressBasedAuthorizer expectedAuthorizer =
            new AddressBasedAuthorizer(expectedKeys, AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        assertThat(expectedAuthorizer, samePropertyValuesAs(constants.getFederationChangeAuthorizer()));
    }

    private static Stream<Arguments> fedChangeAuthorizerArgs() {
        List<ECKey> fedChangeAuthorizedKeysMainnet =  Stream.of(
            "04e593d4cde25137b13f19462bc4c02e97ba2ed5a3860813497abf9b4eeb9259e37e0384d12cfd2d9a7a0ba606b31ee34317a9d7f4a8591c6bcf5dfd5563248b2f",
            "045e7f2563e73d44d149c19cffca36e1898597dc759d76166b8104103c0d3f351a8a48e3a224544e9a649ad8ebcfdbd6c39744ddb85925f19c7e3fd48f07fc1c06",
            "0441945e4e272936106f6200b36162f3510e8083535c15e175ac82deaf828da955b85fd72b7534f2a34cedfb45fa63b728cc696a2bd3c5d39ec799ec2618e9aa9f"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        List<ECKey> fedChangeAuthorizedKeysTestnet = Stream.of(
            "04d9052c2022f6f35da53f04f02856ff5e59f9836eec03daad0328d12c5c66140205da540498e46cd05bf63c1201382dd84c100f0d52a10654159965aea452c3f2",
            "04bf889f2035c8c441d7d1054b6a449742edd04d202f44a29348b4140b34e2a81ce66e388f40046636fd012bd7e3cecd9b951ffe28422334722d20a1cf6c7926fb",
            "047e707e4f67655c40c539363fb435d89574b8fe400971ba0290de9c2adbb2bd4e1e5b35a2188b9409ff2cc102292616efc113623483056bb8d8a02bf7695670ea"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        List<ECKey> fedChangeAuthorizedKeysRegtest = Stream.of(
            "04dde17c5fab31ffc53c91c2390136c325bb8690dc135b0840075dd7b86910d8ab9e88baad0c32f3eea8833446a6bc5ff1cd2efa99ecb17801bcb65fc16fc7d991",
            "04af886c67231476807e2a8eee9193878b9d94e30aa2ee469a9611d20e1e1c1b438e5044148f65e6e61bf03e9d72e597cb9cdea96d6fc044001b22099f9ec403e2",
            "045d4dedf9c69ab3ea139d0f0da0ad00160b7663d01ce7a6155cd44a3567d360112b0480ab6f31cac7345b5f64862205ea7ccf555fcf218f87fa0d801008fecb61",
            "04709f002ac4642b6a87ea0a9dc76eeaa93f71b3185985817ec1827eae34b46b5d869320efb5c5cbe2a5c13f96463fe0210710b53352a4314188daffe07bd54154",
            "04aff62315e9c18004392a5d9e39496ff5794b2d9f43ab4e8ade64740d7fdfe896969be859b43f26ef5aa4b5a0d11808277b4abfa1a07cc39f2839b89cc2bc6b4c"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        return Stream.of(
            Arguments.of(MAINNET, fedChangeAuthorizedKeysMainnet),
            Arguments.of(TESTNET, fedChangeAuthorizedKeysTestnet),
            Arguments.of(REGTEST, fedChangeAuthorizedKeysRegtest)
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
        long erpActivationDelayRegtest = 500;

        return Stream.of(
            Arguments.of(MAINNET, erpActivationDelayMainnet),
            Arguments.of(TESTNET, erpActivationDelayTestnet),
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
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        List<BtcECKey> erpFedPubKeysTestnet = Stream.of(
            "0216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3",
            "034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f",
            "0275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f14"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        List<BtcECKey> erpFedPubKeysRegtest = Stream.of(
            "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
            "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
            "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
            "03776b1fd8f86da3c1db3d69699e8250a15877d286734ea9a6da8e9d8ad25d16c1",
            "03ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58513312550ea1584bc"
        ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        return Stream.of(
            Arguments.of(MAINNET, erpFedPubKeysMainnet),
            Arguments.of(TESTNET, erpFedPubKeysTestnet),
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

        return Stream.of(
            Arguments.of(MAINNET, oldFederationAddressMainnet),
            Arguments.of(TESTNET, oldFederationAddressTestnet),
            Arguments.of(REGTEST, oldFederationAddressRegtest)
        );
    }
}
