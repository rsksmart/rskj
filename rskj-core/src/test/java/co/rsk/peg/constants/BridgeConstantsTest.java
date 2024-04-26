package co.rsk.peg.constants;

import co.rsk.bitcoinj.core.Coin;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeConstantsTest {
    private static Stream<Arguments> fundsMigrationAgeSinceActivationEndArgsProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), false),
            Arguments.of(BridgeTestNetConstants.getInstance(), true),
            Arguments.of(BridgeRegTestConstants.getInstance(), true)
        );
    }

    @ParameterizedTest()
    @MethodSource("fundsMigrationAgeSinceActivationEndArgsProvider")
    void test_getFundsMigrationAgeSinceActivationEnd(BridgeConstants bridgeConstants, boolean hasSameValueForBothFields) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        // Act
        long fundsMigrationAgeSinceActivationEnd = bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertEquals(fundsMigrationAgeSinceActivationEnd, bridgeConstants.fundsMigrationAgeSinceActivationEnd);
        assertEquals(hasSameValueForBothFields,  fundsMigrationAgeSinceActivationEnd == bridgeConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
    }

    @ParameterizedTest()
    @MethodSource("fundsMigrationAgeSinceActivationEndArgsProvider")
    void test_getFundsMigrationAgeSinceActivationEnd_post_RSKIP357(BridgeConstants bridgeConstants, boolean hasSameValueForBothMigrationAges) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP357)).thenReturn(true);

        // Act
        long fundsMigrationAgeSinceActivationEnd = bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertEquals(fundsMigrationAgeSinceActivationEnd, bridgeConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
        assertEquals(hasSameValueForBothMigrationAges,  fundsMigrationAgeSinceActivationEnd == bridgeConstants.fundsMigrationAgeSinceActivationEnd);
    }

    @ParameterizedTest()
    @MethodSource("fundsMigrationAgeSinceActivationEndArgsProvider")
    void test_getFundsMigrationAgeSinceActivationEnd_post_RSKIP374(BridgeConstants bridgeConstants, boolean hasSameValueForBothMigrationAges) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP357)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP374)).thenReturn(true);

        // Act
        long fundsMigrationAgeSinceActivationEnd = bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertEquals(fundsMigrationAgeSinceActivationEnd, bridgeConstants.fundsMigrationAgeSinceActivationEnd);
        assertEquals(hasSameValueForBothMigrationAges,  fundsMigrationAgeSinceActivationEnd == bridgeConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
    }

    private static Stream<Arguments> federationActivationAgeArgProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), false),
            Arguments.of(BridgeTestNetConstants.getInstance(), false),
            Arguments.of(BridgeRegTestConstants.getInstance(), false),
            Arguments.of(BridgeMainNetConstants.getInstance(), true),
            Arguments.of(BridgeTestNetConstants.getInstance(), true),
            Arguments.of(BridgeRegTestConstants.getInstance(), true)
        );
    }

    @ParameterizedTest()
    @MethodSource("federationActivationAgeArgProvider")
    void test_getFederationActivationAge(BridgeConstants bridgeConstants, boolean isRSKIP383Active) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP383)).thenReturn(isRSKIP383Active);
        // Act
        long federationActivationAge = bridgeConstants.getFederationActivationAge(activations);

        // assert
        if (isRSKIP383Active){
            assertEquals(bridgeConstants.federationActivationAge, federationActivationAge);
        } else {
            assertEquals(bridgeConstants.federationActivationAgeLegacy, federationActivationAge);
        }
    }

    private static Stream<Arguments> minimumPeginTxValueArgProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), false),
            Arguments.of(BridgeTestNetConstants.getInstance(), false),
            Arguments.of(BridgeRegTestConstants.getInstance(), false),
            Arguments.of(BridgeMainNetConstants.getInstance(), true),
            Arguments.of(BridgeTestNetConstants.getInstance(), true),
            Arguments.of(BridgeRegTestConstants.getInstance(), true)
        );
    }

    @ParameterizedTest()
    @MethodSource("minimumPeginTxValueArgProvider")
    void test_getMinimumPeginTxValue(BridgeConstants bridgeConstants, boolean isRSKIP219Active){
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(isRSKIP219Active);
        // Act
        Coin minimumPeginTxValue = bridgeConstants.getMinimumPeginTxValue(activations);

        // assert
        if (isRSKIP219Active){
            assertEquals(bridgeConstants.minimumPeginTxValue, minimumPeginTxValue);
        } else {
            assertEquals(bridgeConstants.legacyMinimumPeginTxValue, minimumPeginTxValue);
        }
    }

    private static Stream<Arguments> getBtcHeightWhenPegoutTxIndexActivatesArgProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), 837589),
            Arguments.of(BridgeTestNetConstants.getInstance(), 2589553),
            Arguments.of(BridgeRegTestConstants.getInstance(), 250)
        );
    }

    @ParameterizedTest()
    @MethodSource("getBtcHeightWhenPegoutTxIndexActivatesArgProvider")
    void test_getBtcHeightWhenPegoutTxIndexActivates(BridgeConstants bridgeConstants, int expectedValue){
        // Act
        int btcHeightWhenPegoutTxIndexActivates = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates();

        // assert
        assertEquals(expectedValue, btcHeightWhenPegoutTxIndexActivates);
    }

    private static Stream<Arguments> getPegoutTxIndexGracePeriodInBtcBlocksArgProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), 4_320),
            Arguments.of(BridgeTestNetConstants.getInstance(), 1_440),
            Arguments.of(BridgeRegTestConstants.getInstance(), 100)
        );
    }

    @ParameterizedTest()
    @MethodSource("getPegoutTxIndexGracePeriodInBtcBlocksArgProvider")
    void getPegoutTxIndexGracePeriodInBtcBlocks(BridgeConstants bridgeConstants, int expectedValue){
        // Act
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();

        // assert
        assertEquals(expectedValue, pegoutTxIndexGracePeriodInBtcBlocks);
    }

    private static Stream<Arguments> getGenesisFederationCreationTimeTestProvider() {
        return Stream.of(
                Arguments.of(BridgeMainNetConstants.getInstance(), 1514948400L),
                Arguments.of(BridgeTestNetConstants.getInstance(), 1538967600L),
                Arguments.of(BridgeRegTestConstants.getInstance(), 1451606400L),
                Arguments.of(BridgeDevNetConstants.getInstance(),1510617600L)
        );
    }

    @ParameterizedTest
    @MethodSource("getGenesisFederationCreationTimeTestProvider")
    void getGenesisFederationCreationTimeTest(BridgeConstants bridgeConstants, long expectedGenesisFederationCreationTime){
        long actualGenesisFederationCreationTime = bridgeConstants.getGenesisFederationCreationTime().getEpochSecond();
        assertEquals(expectedGenesisFederationCreationTime, actualGenesisFederationCreationTime);
    }
}
