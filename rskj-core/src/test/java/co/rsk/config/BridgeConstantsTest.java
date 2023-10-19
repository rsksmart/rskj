package co.rsk.config;

import co.rsk.bitcoinj.core.Coin;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
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

    private static Stream<Arguments> bridgeConstantsArgProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance()),
            Arguments.of(BridgeTestNetConstants.getInstance()),
            Arguments.of(BridgeRegTestConstants.getInstance())
        );
    }

    @ParameterizedTest()
    @MethodSource("bridgeConstantsArgProvider")
    void test_getEstimatedPegoutTxIndexBtcActivationHeight(BridgeConstants bridgeConstants){
        // Act
        long estimatedPegoutTxIndexBtcActivationHeight = bridgeConstants.getEstimatedPegoutTxIndexBtcActivationHeight();

        // assert
        Assertions.assertTrue(estimatedPegoutTxIndexBtcActivationHeight > 0);
    }

    @ParameterizedTest()
    @MethodSource("bridgeConstantsArgProvider")
    void test_getPegoutTxIndexGracePeriodInBtcBlocks(BridgeConstants bridgeConstants){
        // Act
        long pegoutTxIndexGracePeriodInBtcBlocks = bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();

        // assert
        Assertions.assertTrue(pegoutTxIndexGracePeriodInBtcBlocks > 0);
    }
}
